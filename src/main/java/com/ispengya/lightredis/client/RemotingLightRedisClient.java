package com.ispengya.lightredis.client;

import com.ispengya.remoting.InvokeCallback;
import com.ispengya.remoting.netty.NettyRemotingClient;
import com.ispengya.remoting.protocol.RemotingCommand;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 remoting 框架实现的客户端核心。
 */
public class RemotingLightRedisClient implements LightRedisClient {

    private static final int REQUEST_CODE = 1;

    /**
     * 目标服务端地址，格式为 host:port。
     */
    private final String address;

    /**
     * 调用超时时间，单位毫秒。
     */
    private final long timeoutMillis;

    /**
     * 底层 remoting 客户端。
     */
    private final NettyRemotingClient client;

    /**
     * 客户端是否已经启动。
     */
    private boolean started;

    /**
     * 客户端是否已经关闭。
     */
    private boolean closed;

    /**
     * 创建客户端。
     *
     * @param host 服务端地址
     * @param port 服务端端口
     * @param timeoutMillis 调用超时时间
     */
    public RemotingLightRedisClient(String host, int port, long timeoutMillis) {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host must not be empty");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        this.address = host + ":" + port;
        this.timeoutMillis = timeoutMillis;
        this.client = new NettyRemotingClient();
    }

    @Override
    public synchronized void start() {
        if (closed) {
            throw new IllegalStateException("Client is closed");
        }
        if (!started) {
            client.start();
            started = true;
        }
    }

    @Override
    public String execute(String command) throws Exception {
        start();
        RemotingCommand response = client.invokeSync(address, createRequest(command), timeoutMillis);
        return readResponse(response);
    }

    @Override
    public CompletableFuture<String> executeAsync(String command) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            start();
            client.invokeAsync(address, createRequest(command), timeoutMillis, new InvokeCallback() {
                @Override
                public void onComplete(RemotingCommand response) {
                    try {
                        future.complete(readResponse(response));
                    } catch (IllegalArgumentException e) {
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void onException(Throwable e) {
                    future.completeExceptionally(e);
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            client.shutdown();
            started = false;
        }
    }

    private RemotingCommand createRequest(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }
        RemotingCommand request = RemotingCommand.createRequestCommand(REQUEST_CODE, null);
        request.setBody(command.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private String readResponse(RemotingCommand response) {
        if (response == null) {
            throw new IllegalArgumentException("Response must not be null");
        }
        byte[] body = response.getBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
