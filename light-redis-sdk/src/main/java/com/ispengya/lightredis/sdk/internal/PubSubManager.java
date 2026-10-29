package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.LightRedisException;
import com.ispengya.lightredis.sdk.pubsub.MessageListener;
import com.ispengya.lightredis.sdk.pubsub.PubSubMessage;
import com.ispengya.lightredis.sdk.pubsub.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 客户端订阅关系与消息分发。
 *
 * <p>服务端按连接维护订阅关系，SDK 在同一连接上对频道和模式做引用计数：
 * 第一个本地句柄订阅时发送 SUBSCRIBE，最后一个句柄关闭时发送 UNSUBSCRIBE。
 * 回调在独立单线程上串行执行，避免阻塞 Netty IO 线程，也允许在回调中
 * 继续执行普通命令或关闭订阅。</p>
 *
 * <p>订阅表使用并发容器，推送路径不获取订阅操作锁，避免网络等待阻塞
 * Netty IO 线程。</p>
 */
public final class PubSubManager implements AutoCloseable {

    /**
     * 日志。
     */
    private static final Logger log = LoggerFactory.getLogger(PubSubManager.class);

    /**
     * 回调线程编号。
     */
    private static final AtomicInteger THREAD_INDEX = new AtomicInteger(0);

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 串行化订阅和取消订阅操作的锁。
     */
    private final Object commandLock = new Object();

    /**
     * 频道到本地订阅句柄。
     */
    private final ConcurrentMap<String, CopyOnWriteArrayList<SubscriptionImpl>>
            channelSubscriptions = new ConcurrentHashMap<>();

    /**
     * 模式到本地订阅句柄。
     */
    private final ConcurrentMap<String, CopyOnWriteArrayList<SubscriptionImpl>>
            patternSubscriptions = new ConcurrentHashMap<>();

    /**
     * 串行执行回调的线程池。
     */
    private final ExecutorService callbackExecutor;

    /**
     * 是否已经关闭。
     */
    private volatile boolean closed;

    /**
     * 创建订阅管理器。
     *
     * @param invoker 命令调用器
     */
    public PubSubManager(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
        this.callbackExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable,
                    "light-redis-sdk-pubsub-" + THREAD_INDEX.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 订阅一组频道或模式。
     *
     * @param targets 频道或模式
     * @param pattern 是否为模式订阅
     * @param listener 消息监听器
     * @return 订阅句柄
     */
    public Subscription subscribe(Collection<String> targets, boolean pattern,
                                   MessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("Listener must not be null");
        }
        Args.requireNonEmpty(targets, pattern ? "Patterns" : "Channels");
        Set<String> unique = new LinkedHashSet<>();
        for (String target : targets) {
            unique.add(Args.requireLineSafe(target, pattern ? "Pattern" : "Channel"));
        }

        SubscriptionImpl subscription = new SubscriptionImpl(this, unique, pattern, listener);
        synchronized (commandLock) {
            ensureOpen();
            ConcurrentMap<String, CopyOnWriteArrayList<SubscriptionImpl>> index = pattern
                    ? patternSubscriptions : channelSubscriptions;
            List<String> newlySubscribed = new ArrayList<>();
            for (String target : unique) {
                CopyOnWriteArrayList<SubscriptionImpl> handlers = index.get(target);
                if (handlers == null) {
                    handlers = new CopyOnWriteArrayList<>();
                    index.put(target, handlers);
                    newlySubscribed.add(target);
                }
                handlers.add(subscription);
            }

            if (!newlySubscribed.isEmpty()) {
                try {
                    invoker.execute(pattern ? "PSUBSCRIBE" : "SUBSCRIBE",
                            newlySubscribed.toArray(new String[0]));
                } catch (RuntimeException e) {
                    removeLocal(subscription);
                    subscription.markClosed();
                    throw e;
                }
            }
        }
        return subscription;
    }

    /**
     * 关闭一个本地订阅句柄。
     *
     * @param subscription 订阅句柄
     */
    void unsubscribe(SubscriptionImpl subscription) {
        synchronized (commandLock) {
            if (subscription.isClosed()) {
                return;
            }
            subscription.markClosed();
            List<String> toUnsubscribe = removeLocal(subscription);
            if (toUnsubscribe.isEmpty()) {
                return;
            }
            if (closed) {
                return;
            }
            try {
                invoker.execute(subscription.isPattern() ? "PUNSUBSCRIBE" : "UNSUBSCRIBE",
                        toUnsubscribe.toArray(new String[0]));
            } catch (RuntimeException e) {
                log.warn("Failed to unsubscribe {}", toUnsubscribe, e);
            }
        }
    }

    /**
     * 处理服务端推送的消息。
     *
     * @param source 底层客户端的订阅消息
     */
    public void onPush(com.ispengya.lightredis.client.PubSubMessage source) {
        if (source == null || closed) {
            return;
        }
        PubSubMessage message = new PubSubMessage(
                source.getType() == com.ispengya.lightredis.client.PubSubMessage.Type.PMESSAGE
                        ? PubSubMessage.Type.PMESSAGE : PubSubMessage.Type.MESSAGE,
                source.getChannel(), source.getPattern(), source.getPayload());

        String target = message.isPatternMessage()
                ? message.getPattern() : message.getChannel();
        ConcurrentMap<String, CopyOnWriteArrayList<SubscriptionImpl>> index =
                message.isPatternMessage() ? patternSubscriptions : channelSubscriptions;
        CopyOnWriteArrayList<SubscriptionImpl> handlers = target == null ? null : index.get(target);
        if (handlers == null || handlers.isEmpty()) {
            return;
        }
        try {
            callbackExecutor.execute(() -> dispatch(handlers, message));
        } catch (RejectedExecutionException e) {
            log.debug("Subscription callback rejected because manager is closed");
        }
    }

    /**
     * 关闭管理器，停止接受新消息并释放回调线程。
     */
    @Override
    public void close() {
        List<SubscriptionImpl> all = new ArrayList<>();
        synchronized (commandLock) {
            if (closed) {
                return;
            }
            closed = true;
            for (List<SubscriptionImpl> subscriptions : channelSubscriptions.values()) {
                all.addAll(subscriptions);
            }
            for (List<SubscriptionImpl> subscriptions : patternSubscriptions.values()) {
                all.addAll(subscriptions);
            }
            channelSubscriptions.clear();
            patternSubscriptions.clear();
        }
        for (SubscriptionImpl subscription : all) {
            subscription.markClosed();
        }
        callbackExecutor.shutdownNow();
    }

    /**
     * 在回调线程上分发消息。
     *
     * @param subscriptions 订阅句柄列表，迭代期间使用写时复制快照
     * @param message 消息
     */
    private void dispatch(List<SubscriptionImpl> subscriptions, PubSubMessage message) {
        for (SubscriptionImpl subscription : subscriptions) {
            if (subscription.isClosed()) {
                continue;
            }
            MessageListener listener = subscription.getListener();
            try {
                listener.onMessage(message);
            } catch (RuntimeException e) {
                log.warn("Subscription listener failed for {}", subscription.getTargets(), e);
            }
        }
    }

    /**
     * 从本地订阅表中移除句柄，并返回需要取消的服务端目标。
     *
     * <p>调用方必须持有 {@link #commandLock}。</p>
     *
     * @param subscription 订阅句柄
     * @return 最后一个本地句柄被移除的频道或模式
     */
    private List<String> removeLocal(SubscriptionImpl subscription) {
        ConcurrentMap<String, CopyOnWriteArrayList<SubscriptionImpl>> index =
                subscription.isPattern() ? patternSubscriptions : channelSubscriptions;
        List<String> toUnsubscribe = new ArrayList<>();
        for (String target : subscription.getTargets()) {
            CopyOnWriteArrayList<SubscriptionImpl> handlers = index.get(target);
            if (handlers == null) {
                continue;
            }
            handlers.remove(subscription);
            if (handlers.isEmpty()) {
                index.remove(target);
                toUnsubscribe.add(target);
            }
        }
        return toUnsubscribe;
    }

    /**
     * 确保管理器尚未关闭。
     */
    private void ensureOpen() {
        if (closed) {
            throw new LightRedisException("Client is closed");
        }
    }
}
