package com.ispengya.lightredis.bootstrap;

import com.ispengya.lightredis.network.LightRedisRequestProcessor;
import com.ispengya.remoting.RemotingServer;
import com.ispengya.remoting.netty.NettyRemotingServer;
import com.ispengya.remoting.netty.NettyRequestProcessor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class LightRedisApplication {

    private static final int DEFAULT_PORT = 6379;
    private static final int REQUEST_CODE = 1;

    public static void main(String[] args) {
        RemotingServer server = new NettyRemotingServer(DEFAULT_PORT);
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "light-redis-command-executor");
            }
        });
        NettyRequestProcessor processor = new LightRedisRequestProcessor();
        server.registerProcessor(REQUEST_CODE, processor, executor);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.shutdown();
            executor.shutdown();
        }));
        server.start();
    }
}
