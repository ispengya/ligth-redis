package com.ispengya.lightredis.persistence;

import com.ispengya.file.api.StoreConfig;

/**
 * light-redis 持久化配置。
 */
public final class PersistenceConfig {

    /**
     * 默认 AOF 数据目录。
     */
    public static final String DEFAULT_DATA_DIR = "./data/light-redis";

    /**
     * 默认触发重写的最小逻辑日志大小。
     */
    public static final long DEFAULT_REWRITE_MIN_SIZE_BYTES = 16L * 1024 * 1024;

    /**
     * 默认重写增长比例。
     */
    public static final int DEFAULT_REWRITE_PERCENTAGE = 100;

    /**
     * 默认重写检查间隔。
     */
    public static final long DEFAULT_REWRITE_CHECK_INTERVAL_MILLIS = 1000L;

    /**
     * AOF 数据根目录。
     */
    private final String dataDir;

    /**
     * 是否每次追加后强制刷盘。
     */
    private final boolean syncFlush;

    /**
     * 自动重写的最小逻辑日志大小。
     */
    private final long rewriteMinSizeBytes;

    /**
     * 相比上次重写后的日志增长百分比。
     */
    private final int rewritePercentage;

    /**
     * 自动重写检查间隔。
     */
    private final long rewriteCheckIntervalMillis;

    /**
     * 创建持久化配置。
     *
     * @param dataDir AOF 数据根目录
     * @param syncFlush 是否每次追加后强制刷盘
     */
    public PersistenceConfig(String dataDir, boolean syncFlush) {
        this(dataDir, syncFlush, DEFAULT_REWRITE_MIN_SIZE_BYTES,
                DEFAULT_REWRITE_PERCENTAGE, DEFAULT_REWRITE_CHECK_INTERVAL_MILLIS);
    }

    /**
     * 创建带重写策略的持久化配置。
     *
     * @param dataDir AOF 数据根目录
     * @param syncFlush 是否每次追加后强制刷盘
     * @param rewriteMinSizeBytes 自动重写最小逻辑日志大小
     * @param rewritePercentage 相比上次重写后的增长百分比
     * @param rewriteCheckIntervalMillis 自动重写检查间隔
     */
    public PersistenceConfig(String dataDir, boolean syncFlush, long rewriteMinSizeBytes,
                             int rewritePercentage, long rewriteCheckIntervalMillis) {
        if (dataDir == null || dataDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Data dir must not be empty");
        }
        if (rewriteMinSizeBytes < 0) {
            throw new IllegalArgumentException("Rewrite min size must not be negative");
        }
        if (rewritePercentage <= 0) {
            throw new IllegalArgumentException("Rewrite percentage must be positive");
        }
        if (rewriteCheckIntervalMillis <= 0) {
            throw new IllegalArgumentException("Rewrite check interval must be positive");
        }
        this.dataDir = dataDir;
        this.syncFlush = syncFlush;
        this.rewriteMinSizeBytes = rewriteMinSizeBytes;
        this.rewritePercentage = rewritePercentage;
        this.rewriteCheckIntervalMillis = rewriteCheckIntervalMillis;
    }

    /**
     * 转换为 mini-file-storage 配置。
     *
     * <p>只启用自定义恢复索引，不启用内置 ConsumeQueue 和 KeyIndex；
     * 运行时使用 ASYNC 模式，启动恢复仍由 SDK 同步完成。</p>
     *
     * @return 存储引擎配置
     */
    public StoreConfig toStoreConfig() {
        return toStoreConfig(dataDir, syncFlush);
    }

    /**
     * 为指定 generation 目录创建存储引擎配置。
     *
     * @param baseDir 存储目录
     * @param syncFlush 是否每次追加后强制刷盘
     * @return 存储引擎配置
     */
    public StoreConfig toStoreConfig(String baseDir, boolean syncFlush) {
        return StoreConfig.builder(baseDir)
                .syncFlush(syncFlush)
                .indexMode(StoreConfig.IndexMode.ASYNC)
                .build();
    }

    /**
     * 返回 AOF 数据根目录。
     *
     * @return 数据目录
     */
    public String getDataDir() {
        return dataDir;
    }

    /**
     * 返回是否每次追加后强制刷盘。
     *
     * @return 同步刷盘时返回 true
     */
    public boolean isSyncFlush() {
        return syncFlush;
    }

    /**
     * 返回自动重写最小逻辑日志大小。
     *
     * @return 字节数
     */
    public long getRewriteMinSizeBytes() {
        return rewriteMinSizeBytes;
    }

    /**
     * 返回自动重写增长百分比。
     *
     * @return 增长百分比
     */
    public int getRewritePercentage() {
        return rewritePercentage;
    }

    /**
     * 返回自动重写检查间隔。
     *
     * @return 毫秒数
     */
    public long getRewriteCheckIntervalMillis() {
        return rewriteCheckIntervalMillis;
    }
}
