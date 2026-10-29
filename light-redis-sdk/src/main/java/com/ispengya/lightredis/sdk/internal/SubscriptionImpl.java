package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.pubsub.MessageListener;
import com.ispengya.lightredis.sdk.pubsub.Subscription;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 发布订阅句柄实现。
 */
final class SubscriptionImpl implements Subscription {

    /**
     * 所属的订阅管理器。
     */
    private final PubSubManager manager;

    /**
     * 订阅的频道或模式。
     */
    private final Set<String> targets;

    /**
     * 是否为模式订阅。
     */
    private final boolean pattern;

    /**
     * 消息监听器。
     */
    private final MessageListener listener;

    /**
     * 是否已经关闭。
     */
    private volatile boolean closed;

    /**
     * 创建订阅句柄。
     *
     * @param manager 订阅管理器
     * @param targets 频道或模式
     * @param pattern 是否为模式订阅
     * @param listener 消息监听器
     */
    SubscriptionImpl(PubSubManager manager, Set<String> targets,
                     boolean pattern, MessageListener listener) {
        this.manager = manager;
        this.targets = Collections.unmodifiableSet(new LinkedHashSet<>(targets));
        this.pattern = pattern;
        this.listener = listener;
    }

    @Override
    public Set<String> getTargets() {
        return targets;
    }

    @Override
    public boolean isPattern() {
        return pattern;
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        manager.unsubscribe(this);
    }

    /**
     * 返回消息监听器。
     *
     * @return 消息监听器
     */
    MessageListener getListener() {
        return listener;
    }

    /**
     * 标记句柄为已关闭。
     */
    void markClosed() {
        closed = true;
    }
}
