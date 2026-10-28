package com.ispengya.lightredis.persistence;

import com.ispengya.file.api.MiniFileStore;
import com.ispengya.file.api.RecordScanner;
import com.ispengya.file.api.StoreConfig;
import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandRegistry;
import com.ispengya.lightredis.command.CommandReplies;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import com.ispengya.lightredis.store.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * 基于 mini-file-storage 的命令日志。
 *
 * <p>启动时通过 {@link RedisRecoveryIndex} 把历史命令回放到内存 store；
 * 运行时每条写命令追加到 CommitLog。AOF 重写通过旧日志前缀回放生成新的
 * generation，并在切换期间用 delta 队列补齐增量写入。</p>
 */
public final class FileCommandLog implements CommandLog {

    private static final Logger log = LoggerFactory.getLogger(FileCommandLog.class);

    /**
     * 持久化配置。
     */
    private final PersistenceConfig config;

    /**
     * AOF 目录布局。
     */
    private final AofLayout layout;

    /**
     * AOF 编解码器。
     */
    private final AofCodec codec;

    /**
     * 启动恢复索引，仅用于记录首次恢复指标。
     */
    private final RedisRecoveryIndex recoveryIndex;

    /**
     * 保护活动 store、重写状态和 generation 切换。
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 是否已经关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 上次重写后的逻辑日志大小。
     */
    private final AtomicLong rewriteBaseSize = new AtomicLong(0L);

    /**
     * 自动重写检查任务。
     */
    private final ScheduledExecutorService rewriteScheduler;

    /**
     * 当前活跃 store。
     */
    private volatile MiniFileStore<AofRecord> store;

    /**
     * 当前活跃 generation 目录。
     */
    private volatile Path activeDir;

    /**
     * 当前重写任务。
     */
    private volatile RewriteSession rewriteSession;

    /**
     * 最近一次重写是否成功。
     */
    private volatile boolean lastRewriteSucceeded;

    private FileCommandLog(PersistenceConfig config, AofLayout layout, Path activeDir,
                           AofCodec codec, MiniFileStore<AofRecord> store,
                           RedisRecoveryIndex recoveryIndex, long baseSize) {
        this.config = config;
        this.layout = layout;
        this.activeDir = activeDir;
        this.codec = codec;
        this.store = store;
        this.recoveryIndex = recoveryIndex;
        this.rewriteBaseSize.set(baseSize);
        this.rewriteScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "light-redis-aof-rewrite-check");
                thread.setDaemon(true);
                return thread;
            }
        });
        long interval = config.getRewriteCheckIntervalMillis();
        rewriteScheduler.scheduleAtFixedRate(this::checkAutoRewrite,
                interval, interval, TimeUnit.MILLISECONDS);
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

        AofLayout layout = AofLayout.open(config.getDataDir());
        AofCodec codec = new AofCodec();
        RedisRecoveryIndex recoveryIndex = new RedisRecoveryIndex(dispatcher, codec);
        Path activeDir = layout.getActiveDir();
        StoreConfig storeConfig = config.toStoreConfig(activeDir.toString(), config.isSyncFlush());

        MiniFileStore<AofRecord> store;
        try {
            store = MiniFileStore.builder(storeConfig, codec)
                    .withIndex(recoveryIndex)
                    .build();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to open AOF store at " + activeDir, e);
        }

        if (recoveryIndex.getFailure() != null) {
            closeAfterRecoveryFailure(store);
            throw new PersistenceException("Failed to recover AOF store at " + activeDir,
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
                    + " of " + logEnd + " in " + activeDir);
        }

        recoveryIndex.markRecovered();
        long baseSize = logEnd;
        log.info("AOF ready: root={}, active={}, replayedCommands={}, failedCommands={}, "
                        + "logicalSize={}, syncFlush={}",
                layout.getRootDir(), activeDir, recoveryIndex.getReplayedCommands(),
                recoveryIndex.getFailedCommands(), baseSize, config.isSyncFlush());

        try {
            return new FileCommandLog(config, layout, activeDir, codec, store, recoveryIndex, baseSize);
        } catch (RuntimeException e) {
            closeAfterRecoveryFailure(store);
            throw e;
        }
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
        lock.readLock().lock();
        try {
            ensureOpen();
            MiniFileStore<AofRecord> current = store;
            AofRecord record = AofRecord.from(command);
            try {
                current.put(record);
            } catch (RuntimeException e) {
                throw new PersistenceException("Failed to append command " + command.getName(), e);
            }
            RewriteSession session = rewriteSession;
            if (session != null) {
                session.delta.add(record);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void flush() {
        lock.readLock().lock();
        try {
            ensureOpen();
            try {
                store.flush();
            } catch (RuntimeException e) {
                throw new PersistenceException("Failed to flush AOF store", e);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean startRewrite() {
        lock.writeLock().lock();
        try {
            if (closed.get() || rewriteSession != null) {
                return false;
            }
            RecordScanner scanner;
            try {
                scanner = store.scan();
            } catch (RuntimeException e) {
                log.warn("Failed to create AOF rewrite scanner", e);
                return false;
            }

            RewriteSession session = new RewriteSession(scanner);
            rewriteSession = session;
            lastRewriteSucceeded = false;
            Thread thread = new Thread(() -> runRewrite(session), "light-redis-aof-rewrite");
            thread.setDaemon(true);
            session.thread = thread;
            try {
                thread.start();
            } catch (RuntimeException e) {
                rewriteSession = null;
                closeScanner(session);
                return false;
            }
            log.info("AOF rewrite started: active={}, snapshotEnd={}, logicalSize={}",
                    activeDir, session.snapshotEnd, session.snapshotEnd);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean isRewriteInProgress() {
        return rewriteSession != null;
    }

    /**
     * 返回当前活动 generation 目录。
     *
     * @return 活动目录
     */
    public Path getActiveDir() {
        return activeDir;
    }

    /**
     * 返回最近一次重写是否成功。
     *
     * @return 成功时返回 true
     */
    public boolean isLastRewriteSucceeded() {
        return lastRewriteSucceeded;
    }

    /**
     * 等待当前重写任务结束。
     *
     * @param timeoutMillis 超时时间
     * @return 重写成功返回 true；超时或失败返回 false
     */
    public boolean awaitRewriteCompletion(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (rewriteSession == null) {
                return lastRewriteSucceeded;
            }
            try {
                Thread.sleep(20L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
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

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        rewriteScheduler.shutdownNow();

        RewriteSession session = rewriteSession;
        if (session != null) {
            session.aborted = true;
            Thread thread = session.thread;
            if (thread != null && thread != Thread.currentThread()) {
                try {
                    thread.join(5000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        lock.writeLock().lock();
        try {
            rewriteSession = null;
            MiniFileStore<AofRecord> current = store;
            try {
                current.close();
            } catch (RuntimeException e) {
                throw new PersistenceException("Failed to close AOF store", e);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void checkAutoRewrite() {
        if (closed.get()) {
            return;
        }
        long currentSize;
        lock.readLock().lock();
        try {
            if (rewriteSession != null) {
                return;
            }
            currentSize = logicalSize(store);
        } catch (RuntimeException e) {
            log.warn("Failed to inspect AOF size", e);
            return;
        } finally {
            lock.readLock().unlock();
        }

        if (currentSize <= 0 || currentSize < config.getRewriteMinSizeBytes()) {
            return;
        }
        long baseSize = rewriteBaseSize.get();
        boolean grown = baseSize <= 0
                || currentSize >= baseSize * (1.0 + config.getRewritePercentage() / 100.0);
        if (grown) {
            log.info("AOF auto rewrite triggered: currentSize={}, baseSize={}, minSize={}",
                    currentSize, baseSize, config.getRewriteMinSizeBytes());
            startRewrite();
        }
    }

    private void runRewrite(RewriteSession session) {
        MiniFileStore<AofRecord> newStore = null;
        Path newDir = null;
        boolean switched = false;
        try {
            KeyValueStore temp = new InMemoryKeyValueStore();
            CommandDispatcher tempDispatcher = new CommandDispatcher(temp);
            CommandRegistry.registerAll(tempDispatcher, new ServerStats());

            try (RecordScanner scanner = session.scanner) {
                while (scanner.next()) {
                    checkAborted(session);
                    AofRecord record = codec.decode(scanner.body());
                    String reply = tempDispatcher.dispatch(record.toCommand());
                    if (CommandReplies.isError(reply)) {
                        log.debug("AOF rewrite replay command returned an error: {} -> {}",
                                record.getCommandName(), reply);
                    }
                }
            }
            checkAborted(session);

            newDir = layout.createGeneration();
            StoreConfig bulkConfig = config.toStoreConfig(newDir.toString(), false);
            newStore = MiniFileStore.builder(bulkConfig, codec).build();
            AofSnapshotWriter writer = new AofSnapshotWriter(newStore,
                    () -> session.aborted || closed.get());
            writer.write(temp);

            session.snapshotDone = true;
            lock.writeLock().lock();
            try {
                checkAborted(session);

                AofRecord deltaRecord;
                while ((deltaRecord = session.delta.poll()) != null) {
                    checkAborted(session);
                    newStore.put(deltaRecord);
                }
                newStore.flush();
                newStore.close();
                newStore = null;

                MiniFileStore<AofRecord> runtimeStore = MiniFileStore.builder(
                        config.toStoreConfig(newDir.toString(), config.isSyncFlush()), codec).build();
                newStore = runtimeStore;

                layout.updateCurrent(newDir);

                MiniFileStore<AofRecord> oldStore = store;
                store = runtimeStore;
                activeDir = newDir;
                rewriteSession = null;
                lastRewriteSucceeded = true;
                newStore = null;
                switched = true;
                try {
                    rewriteBaseSize.set(logicalSize(runtimeStore));
                } catch (RuntimeException e) {
                    log.warn("Failed to inspect new AOF size", e);
                    rewriteBaseSize.set(0L);
                }
                log.info("AOF rewrite completed: active={}, logicalSize={}",
                        activeDir, rewriteBaseSize.get());
                try {
                    oldStore.close();
                } catch (RuntimeException e) {
                    log.warn("Failed to close old AOF store after rewrite", e);
                }
            } finally {
                lock.writeLock().unlock();
            }
        } catch (RewriteAbortedException e) {
            log.info("AOF rewrite aborted");
        } catch (RuntimeException e) {
            log.error("AOF rewrite failed", e);
        } finally {
            if (!switched) {
                cleanupFailedRewrite(session, newStore, newDir);
            }
        }
    }

    private void cleanupFailedRewrite(RewriteSession session,
                                      MiniFileStore<AofRecord> newStore, Path newDir) {
        lock.writeLock().lock();
        try {
            if (rewriteSession == session) {
                rewriteSession = null;
            }
            if (newStore != null) {
                try {
                    newStore.close();
                } catch (RuntimeException e) {
                    log.warn("Failed to close aborted AOF generation", e);
                }
            }
            if (newDir != null) {
                deleteRecursively(newDir);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void checkAborted(RewriteSession session) {
        if (session.aborted || closed.get()) {
            throw new RewriteAbortedException();
        }
    }

    private long logicalSize(MiniFileStore<AofRecord> target) {
        try (RecordScanner scanner = target.scan()) {
            return scanner.endOffset();
        } catch (RuntimeException e) {
            throw new PersistenceException("Failed to read AOF logical size", e);
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new PersistenceException("AOF store is already closed");
        }
    }

    private void closeScanner(RewriteSession session) {
        try {
            session.scanner.close();
        } catch (RuntimeException ignored) {
            // scanner 关闭失败不影响旧 AOF
        }
    }

    private void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Failed to delete aborted AOF file {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to clean aborted AOF generation {}", root, e);
        }
    }

    /**
     * 一次重写的运行状态。
     */
    private static final class RewriteSession {

        /**
         * 旧 AOF 的稳定前缀扫描器。
         */
        private final RecordScanner scanner;

        /**
         * 旧 AOF 快照末尾位点。
         */
        private final long snapshotEnd;

        /**
         * 重写开始后的写命令。
         */
        private final Queue<AofRecord> delta = new ConcurrentLinkedQueue<>();

        /**
         * 是否已取消。
         */
        private volatile boolean aborted;

        /**
         * 是否已完成快照。
         */
        private volatile boolean snapshotDone;

        /**
         * 后台线程，供关闭时等待。
         */
        private volatile Thread thread;

        private RewriteSession(RecordScanner scanner) {
            this.scanner = scanner;
            this.snapshotEnd = scanner.endOffset();
        }
    }
}
