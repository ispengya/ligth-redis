package com.ispengya.lightredis.sdk;

import com.ispengya.lightredis.client.RemotingLightRedisClient;
import com.ispengya.lightredis.sdk.command.HashCommands;
import com.ispengya.lightredis.sdk.command.KeyCommands;
import com.ispengya.lightredis.sdk.command.ListCommands;
import com.ispengya.lightredis.sdk.command.PubSubCommands;
import com.ispengya.lightredis.sdk.command.RawCommands;
import com.ispengya.lightredis.sdk.command.ServerCommands;
import com.ispengya.lightredis.sdk.command.SetCommands;
import com.ispengya.lightredis.sdk.command.SortedSetCommands;
import com.ispengya.lightredis.sdk.command.StringCommands;
import com.ispengya.lightredis.sdk.internal.CommandInvoker;
import com.ispengya.lightredis.sdk.internal.HashCommandsImpl;
import com.ispengya.lightredis.sdk.internal.KeyCommandsImpl;
import com.ispengya.lightredis.sdk.internal.ListCommandsImpl;
import com.ispengya.lightredis.sdk.internal.PubSubCommandsImpl;
import com.ispengya.lightredis.sdk.internal.PubSubManager;
import com.ispengya.lightredis.sdk.internal.RawCommandsImpl;
import com.ispengya.lightredis.sdk.internal.ServerCommandsImpl;
import com.ispengya.lightredis.sdk.internal.SetCommandsImpl;
import com.ispengya.lightredis.sdk.internal.SortedSetCommandsImpl;
import com.ispengya.lightredis.sdk.internal.StringCommandsImpl;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * light-redis Java SDK 入口。
 *
 * <p>一个实例持有一个 Netty 连接，所有类型化命令共享该连接，实例线程安全。
 * 创建实例时会发送一次 PING 验证连接，失败抛出
 * {@link LightRedisConnectionException}。使用完毕后调用 {@link #close()}，
 * 或使用 try-with-resources 释放资源。</p>
 *
 * <pre>{@code
 * try (LightRedis redis = LightRedis.builder()
 *         .host("127.0.0.1")
 *         .port(6379)
 *         .connect()) {
 *     redis.strings().set("demo:name", "tom");
 *     String name = redis.strings().get("demo:name");
 * }
 * }</pre>
 */
public final class LightRedis implements AutoCloseable {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 订阅管理器。
     */
    private final PubSubManager pubSubManager;

    /**
     * 通用 key 命令。
     */
    private final KeyCommands keyCommands;

    /**
     * String 命令。
     */
    private final StringCommands stringCommands;

    /**
     * List 命令。
     */
    private final ListCommands listCommands;

    /**
     * Hash 命令。
     */
    private final HashCommands hashCommands;

    /**
     * Set 命令。
     */
    private final SetCommands setCommands;

    /**
     * Sorted Set 命令。
     */
    private final SortedSetCommands sortedSetCommands;

    /**
     * 服务端管理命令。
     */
    private final ServerCommands serverCommands;

    /**
     * 发布订阅命令。
     */
    private final PubSubCommands pubSubCommands;

    /**
     * 原始命令出口。
     */
    private final RawCommands rawCommands;

    /**
     * 是否已经关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 创建 SDK 实例。
     *
     * @param config 连接配置
     */
    private LightRedis(LightRedisConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        RemotingLightRedisClient transport = new RemotingLightRedisClient(
                config.getHost(), config.getPort(), config.getTimeoutMillis());
        this.invoker = new CommandInvoker(transport);
        this.pubSubManager = new PubSubManager(invoker);
        this.invoker.setPubSubListener(pubSubManager::onPush);
        try {
            this.invoker.start();
            this.invoker.execute("PING");
        } catch (RuntimeException e) {
            this.pubSubManager.close();
            this.invoker.close();
            throw e;
        }

        this.keyCommands = new KeyCommandsImpl(invoker);
        this.stringCommands = new StringCommandsImpl(invoker);
        this.listCommands = new ListCommandsImpl(invoker);
        this.hashCommands = new HashCommandsImpl(invoker);
        this.setCommands = new SetCommandsImpl(invoker);
        this.sortedSetCommands = new SortedSetCommandsImpl(invoker);
        this.serverCommands = new ServerCommandsImpl(invoker);
        this.pubSubCommands = new PubSubCommandsImpl(invoker, pubSubManager);
        this.rawCommands = new RawCommandsImpl(invoker);
    }

    /**
     * 创建连接构建器。
     *
     * @return 构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 使用默认超时连接服务端。
     *
     * @param host 服务端地址
     * @param port 服务端端口
     * @return SDK 实例
     */
    public static LightRedis connect(String host, int port) {
        return connect(host, port, LightRedisConfig.DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * 连接服务端。
     *
     * @param host 服务端地址
     * @param port 服务端端口
     * @param timeoutMillis 调用超时时间，单位毫秒
     * @return SDK 实例
     */
    public static LightRedis connect(String host, int port, long timeoutMillis) {
        return connect(LightRedisConfig.builder()
                .host(host)
                .port(port)
                .timeoutMillis(timeoutMillis)
                .build());
    }

    /**
     * 使用连接配置创建 SDK 实例。
     *
     * @param config 连接配置
     * @return SDK 实例
     */
    public static LightRedis connect(LightRedisConfig config) {
        return new LightRedis(config);
    }

    /**
     * 返回通用 key 命令。
     *
     * @return key 命令
     */
    public KeyCommands keys() {
        return keyCommands;
    }

    /**
     * 返回 String 命令。
     *
     * @return String 命令
     */
    public StringCommands strings() {
        return stringCommands;
    }

    /**
     * 返回 List 命令。
     *
     * @return List 命令
     */
    public ListCommands lists() {
        return listCommands;
    }

    /**
     * 返回 Hash 命令。
     *
     * @return Hash 命令
     */
    public HashCommands hashes() {
        return hashCommands;
    }

    /**
     * 返回 Set 命令。
     *
     * @return Set 命令
     */
    public SetCommands sets() {
        return setCommands;
    }

    /**
     * 返回 Sorted Set 命令。
     *
     * @return Sorted Set 命令
     */
    public SortedSetCommands sortedSets() {
        return sortedSetCommands;
    }

    /**
     * 返回服务端管理命令。
     *
     * @return 服务端命令
     */
    public ServerCommands server() {
        return serverCommands;
    }

    /**
     * 返回发布订阅命令。
     *
     * @return 发布订阅命令
     */
    public PubSubCommands pubsub() {
        return pubSubCommands;
    }

    /**
     * 返回原始命令出口。
     *
     * @return 原始命令出口
     */
    public RawCommands raw() {
        return rawCommands;
    }

    /**
     * 判断实例是否已经关闭。
     *
     * @return 已关闭返回 true
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 关闭实例，取消订阅并释放连接资源。
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        pubSubManager.close();
        invoker.setPubSubListener(null);
        invoker.close();
    }

    /**
     * SDK 连接构建器。
     */
    public static final class Builder {

        /**
         * 连接配置构建器。
         */
        private final LightRedisConfig.Builder config = LightRedisConfig.builder();

        /**
         * 创建构建器。
         */
        private Builder() {
        }

        /**
         * 设置服务端地址。
         *
         * @param host 服务端地址
         * @return 当前构建器
         */
        public Builder host(String host) {
            config.host(host);
            return this;
        }

        /**
         * 设置服务端端口。
         *
         * @param port 服务端端口
         * @return 当前构建器
         */
        public Builder port(int port) {
            config.port(port);
            return this;
        }

        /**
         * 设置调用超时时间。
         *
         * @param timeoutMillis 超时毫秒数
         * @return 当前构建器
         */
        public Builder timeoutMillis(long timeoutMillis) {
            config.timeoutMillis(timeoutMillis);
            return this;
        }

        /**
         * 创建并启动 SDK 实例。
         *
         * @return SDK 实例
         */
        public LightRedis connect() {
            return new LightRedis(config.build());
        }
    }
}
