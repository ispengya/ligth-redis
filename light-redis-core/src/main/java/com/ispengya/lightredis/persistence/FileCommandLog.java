package com.ispengya.lightredis.persistence;

import com.ispengya.file.api.MiniFileStore;
import com.ispengya.file.api.RecordScanner;
import com.ispengya.file.api.StoreConfig;
import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.protocol.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 基于 mini-file-storage 的命令日志。
 *
 * <p>启动时通过 {@link RedisRecoveryIndex} 把历史命令回放到内存 store；
 * 运行时每条写命令追加到 CommitLog，使用 ASYNC 索引模式，命令本身仍由
 * light-redis 的命令线程执行。</p>
 */
public final class FileCommandLog implements CommandLog {

    private static final Logger log = LoggerFactory.getLogger(FileCommandLog.class);

    /**
     * 底层顺序存储。
     */
    private final MiniFileStore<AofRecord> store;

    /**
     * 启动恢复索引。
     */
    private final RedisRecoveryIndex recoveryIndex;

    /**
     * 是否已经关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private FileCommandLog(MiniFileStore<AofRecord> store, RedisRecoveryIndex recoveryIndex) {
        this.store = store;
        this.recoveryIndex = recoveryIndex;
    }

    /**
     * 打开 AOF 日志并完成启动恢复。
     *
     * @param config 持久化配置
     * @param dispatcher 已完成命令注册的分发器
     * @return 命令日志
     * @throws PersistenceException 打开或恢复失败
     */
    public static FileCommandLog open(PersistenceConfig config, CommandDispatcher dispatcher) {
        if (config == null) {
            throw new IllegalArgumentException("Persistence config must not be null");
        }
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher must not be null");
        }

        AofCodec codec = new AofCodec();
        RedisRecoveryIndex recoveryIndex = new RedisRecoveryIndex(dispatcher, codec);
        StoreConfig storeConfig = config.toStoreConfig();

        MiniFileStore<AofRecord> store;
        try {
            store = MiniFileStore.builder(storeConfig, codec)
                    .withIndex(recoveryIndex)
                    .build();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to open AOF store at " + config.getDataDir(), e);
        }

        if (recoveryIndex.getFailure() != null) {
            closeAfterRecoveryFailure(store);
            throw new PersistenceException("Failed to recover AOF store at " + config.getDataDir(),
                    recoveryIndex.getFailure());
        }

        long logEnd;
        try (RecordScanner scanner = store.scan()) {
            logEnd = scanner.endOffset();
        }
        long indexedTo = store.maxIndexedOffset();
        if (indexedTo < logEnd) {
            closeAfterRecoveryFailure(store);
            throw new PersistenceException("AOF recovery stopped at " + indexedTo
                    + " of " + logEnd + " in " + config.getDataDir());
        }

        recoveryIndex.markRecovered();
        log.info("AOF ready: dataDir={}, replayedCommands={}, failedCommands={}, syncFlush={}",
                config.getDataDir(), recoveryIndex.getReplayedCommands(),
                recoveryIndex.getFailedCommands(), config.isSyncFlush());
        return new FileCommandLog(store, recoveryIndex);
    }

    private static void closeAfterRecoveryFailure(MiniFileStore<AofRecord> store) {
        try {
            store.close();
        } catch (RuntimeException e) {
            log.warn("Failed to close AOF store after recovery failure", e);
        }
    }

    @Override
    public void append(Command command) {
        if (closed.get()) {
            throw new PersistenceException("AOF store is already closed");
        }
        try {
            store.put(AofRecord.from(command));
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to append command " + command.getName(), e);
        }
    }

    @Override
    public void flush() {
        if (closed.get()) {
            throw new PersistenceException("AOF store is already closed");
        }
        try {
            store.flush();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to flush AOF store", e);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            store.close();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to close AOF store", e);
        }
    }

    /**
     * 返回本次启动恢复的命令数量。
     *
     * @return 恢复命令数量
     */
    public long getRecoveredCommands() {
        return recoveryIndex.getReplayedCommands();
    }

    /**
     * 返回本次启动恢复时返回错误的命令数量。
     *
     * @return 恢复失败命令数量
     */
    public long getRecoveredFailures() {
        return recoveryIndex.getFailedCommands();
    }
}
