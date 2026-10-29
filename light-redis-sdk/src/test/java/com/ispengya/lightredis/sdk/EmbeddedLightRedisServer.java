package com.ispengya.lightredis.sdk;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandRegistry;
import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.network.LightRedisRequestProcessor;
import com.ispengya.lightredis.persistence.CommandLog;
import com.ispengya.lightredis.persistence.NoopCommandLog;
import com.ispengya.lightredis.pubsub.PubSubService;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.remoting.netty.NettyRemotingServer;
import com.ispengya.remoting.netty.NettyRequestProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 测试用内嵌服务端，使用随机端口且不开启 AOF。
 */
final class EmbeddedLightRedisServer implements AutoCloseable {

    /**
     * 命令请求码。
     */
    private static final int REQUEST_CODE = 1;

    /**
     * 内嵌 Netty 服务端。
     */
    private final NettyRemotingServer server;

    /**
     * 单线程命令执行器。
     */
    private final ExecutorService executor;

    /**
     * 实际监听端口。
     */
    private final int port;

    /**
     * 启动内嵌服务端。
     */
    EmbeddedLightRedisServer() {
        ServerStats stats = new ServerStats();
        KeyValueStore store = new InMemoryKeyValueStore();
        CommandDispatcher dispatcher = new CommandDispatcher(store);
        CommandRegistry.registerAll(dispatcher, stats);
        CommandLog commandLog = NoopCommandLog.INSTANCE;
        CommandRegistry.registerPersistenceCommands(dispatcher, commandLog);
        CommandService commandService = new CommandService(dispatcher, commandLog);

        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "light-redis-sdk-test-command");
            thread.setDaemon(true);
            return thread;
        });
        this.server = new NettyRemotingServer(0);
        PubSubService pubSubService = new PubSubService(server::push);
        NettyRequestProcessor processor = new LightRedisRequestProcessor(
                commandService, stats, pubSubService);
        this.server.registerProcessor(REQUEST_CODE, processor, executor);
        this.server.start();
        this.port = server.localListenPort();
    }

    /**
     * 返回服务端监听端口。
     *
     * @return 端口
     */
    int getPort() {
        return port;
    }

    @Override
    public void close() {
        server.shutdown();
        executor.shutdownNow();
    }
}
