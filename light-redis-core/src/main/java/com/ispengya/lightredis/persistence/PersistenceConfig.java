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
     * AOF 数据根目录。
     */
    private final String dataDir;

    /**
     * 是否每次追加后强制刷盘。
     */
    private final boolean syncFlush;

    /**
     * 创建持久化配置。
     *
     * @param dataDir AOF 数据根目录
     * @param syncFlush 是否每次追加后强制刷盘
     */
    public PersistenceConfig(String dataDir, boolean syncFlush) {
        if (dataDir == null || dataDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Data dir must not be empty");
        }
        this.dataDir = dataDir;
        this.syncFlush = syncFlush;
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
        return StoreConfig.builder(dataDir)
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
}
