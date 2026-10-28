package com.ispengya.lightredis.store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 定期删除过期 key 的后台任务。
 *
 * <p>任务必须提交到命令单线程执行器，和命令串行执行，避免删除过程
 * 与读命令交错。每轮最多采样 20 个带 TTL 的 key，过期比例超过 25%
 * 时在 1ms 预算内继续采样。</p>
 */
public final class ActiveExpireCycle {

    private static final Logger log = LoggerFactory.getLogger(ActiveExpireCycle.class);

    /**
     * 检查间隔，毫秒。
     */
    private static final long INTERVAL_MILLIS = 100L;

    /**
     * 每轮采样数量。
     */
    private static final int SAMPLE_SIZE = 20;

    /**
     * 单次清理的时间预算，纳秒。
     */
    private static final long BUDGET_NANOS = 1_000_000L;

    /**
     * 被清理的存储。
     */
    private final KeyValueStore store;

    /**
     * 创建定期过期任务。
     *
     * @param store 存储
     */
    public ActiveExpireCycle(KeyValueStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Store must not be null");
        }
        this.store = store;
    }

    /**
     * 在命令执行器上启动周期任务。
     *
     * @param executor 单线程命令执行器
     */
    public void start(ScheduledExecutorService executor) {
        executor.scheduleWithFixedDelay(() -> runOnce(),
                INTERVAL_MILLIS, INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }

    /**
     * 执行一次过期清理。
     *
     * @return 删除的 key 数量
     */
    public int runOnce() {
        try {
            return store.purgeExpired(SAMPLE_SIZE, System.nanoTime() + BUDGET_NANOS);
        } catch (RuntimeException e) {
            log.warn("Active expire cycle failed", e);
            return 0;
        }
    }
}
