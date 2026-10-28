package com.ispengya.lightredis.bootstrap;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandRegistry;
import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.network.LightRedisRequestProcessor;
import com.ispengya.lightredis.persistence.CommandLog;
import com.ispengya.lightredis.persistence.FileCommandLog;
import com.ispengya.lightredis.persistence.NoopCommandLog;
import com.ispengya.lightredis.persistence.PersistenceConfig;
import com.ispengya.lightredis.store.ActiveExpireCycle;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.remoting.RemotingServer;
import com.ispengya.remoting.netty.NettyRemotingServer;
import com.ispengya.remoting.netty.NettyRequestProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class LightRedisApplication {

    private static final Logger log = LoggerFactory.getLogger(LightRedisApplication.class);

    private static final int DEFAULT_PORT = 6379;
    private static final int REQUEST_CODE = 1;

    public static void main(String[] args) {
        ServerOptions options;
        try {
            options = ServerOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            ServerOptions.printUsage(System.out);
            System.exit(2);
            return;
        }

        if (options.isHelp()) {
            ServerOptions.printUsage(System.out);
            return;
        }

        try {
            start(options);
        } catch (RuntimeException e) {
            log.error("Failed to start light-redis", e);
            System.err.println("Failed to start light-redis: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void start(ServerOptions options) {
        ServerStats stats = new ServerStats();
        KeyValueStore store = new InMemoryKeyValueStore();
        CommandDispatcher dispatcher = new CommandDispatcher(store);
        CommandRegistry.registerAll(dispatcher, stats);

        CommandLog commandLog;
        if (options.isAppendOnly()) {
            commandLog = FileCommandLog.open(
                    new PersistenceConfig(options.getDataDir(), true), dispatcher);
        } else {
            commandLog = NoopCommandLog.INSTANCE;
            log.info("AOF is disabled");
        }

        CommandRegistry.registerPersistenceCommands(dispatcher, commandLog);
        long expired = store.purgeAllExpired();
        if (expired > 0) {
            log.info("Purged {} expired keys after recovery", expired);
        }
        CommandService commandService = new CommandService(dispatcher, commandLog);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
                new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "light-redis-command-executor");
                    }
                });
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        RemotingServer server = new NettyRemotingServer(DEFAULT_PORT);
        NettyRequestProcessor processor = new LightRedisRequestProcessor(commandService, stats);
        server.registerProcessor(REQUEST_CODE, processor, executor);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                commandLog.flush();
            } catch (RuntimeException e) {
                log.warn("AOF flush failed during shutdown", e);
            }
            try {
                commandLog.close();
            } catch (RuntimeException e) {
                log.warn("AOF close failed during shutdown", e);
            }
        }, "light-redis-shutdown"));

        try {
            server.start();
            new ActiveExpireCycle(store).start(executor);
        } catch (RuntimeException e) {
            server.shutdown();
            executor.shutdownNow();
            try {
                commandLog.close();
            } catch (RuntimeException closeError) {
                log.warn("AOF close failed after startup error", closeError);
            }
            throw e;
        }

        log.info("light-redis started: port={}, appendonly={}, dataDir={}",
                DEFAULT_PORT, options.isAppendOnly(), options.getDataDir());
    }
}
