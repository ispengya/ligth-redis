package com.ispengya.lightredis.sdk;

/**
 * SDK 连接配置。
 *
 * <p>配置不可变，通过 {@link #builder()} 创建。默认连接
 * {@code 127.0.0.1:6379}，调用超时 5000 毫秒。</p>
 */
public final class LightRedisConfig {

    /**
     * 默认服务端地址。
     */
    public static final String DEFAULT_HOST = "127.0.0.1";

    /**
     * 默认服务端端口。
     */
    public static final int DEFAULT_PORT = 6379;

    /**
     * 默认调用超时时间，单位毫秒。
     */
    public static final long DEFAULT_TIMEOUT_MILLIS = 5000L;

    /**
     * 服务端地址。
     */
    private final String host;

    /**
     * 服务端端口。
     */
    private final int port;

    /**
     * 调用超时时间，单位毫秒。
     */
    private final long timeoutMillis;

    /**
     * 创建连接配置。
     *
     * @param host 服务端地址
     * @param port 服务端端口
     * @param timeoutMillis 调用超时时间，单位毫秒
     */
    private LightRedisConfig(String host, int port, long timeoutMillis) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host must not be empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.host = host.trim();
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * 创建配置构建器。
     *
     * @return 新的构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 返回服务端地址。
     *
     * @return 服务端地址
     */
    public String getHost() {
        return host;
    }

    /**
     * 返回服务端端口。
     *
     * @return 服务端端口
     */
    public int getPort() {
        return port;
    }

    /**
     * 返回调用超时时间。
     *
     * @return 超时毫秒数
     */
    public long getTimeoutMillis() {
        return timeoutMillis;
    }

    /**
     * 返回可读的地址文本。
     *
     * @return host:port
     */
    @Override
    public String toString() {
        return host + ":" + port;
    }

    /**
     * 连接配置构建器。
     */
    public static final class Builder {

        /**
         * 服务端地址。
         */
        private String host = DEFAULT_HOST;

        /**
         * 服务端端口。
         */
        private int port = DEFAULT_PORT;

        /**
         * 调用超时时间，单位毫秒。
         */
        private long timeoutMillis = DEFAULT_TIMEOUT_MILLIS;

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
            this.host = host;
            return this;
        }

        /**
         * 设置服务端端口。
         *
         * @param port 服务端端口
         * @return 当前构建器
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * 设置调用超时时间。
         *
         * @param timeoutMillis 超时毫秒数
         * @return 当前构建器
         */
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * 构建不可变配置。
         *
         * @return 连接配置
         */
        public LightRedisConfig build() {
            return new LightRedisConfig(host, port, timeoutMillis);
        }
    }
}
