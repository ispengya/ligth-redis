package com.ispengya.lightredis.client;

import com.ispengya.remoting.InvokeCallback;
import com.ispengya.remoting.netty.NettyRemotingClient;
import com.ispengya.remoting.protocol.RemotingCommand;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 基于 remoting 框架实现的客户端核心。
 */
public class RemotingLightRedisClient implements LightRedisClient {

    private static final int REQUEST_CODE = 1;

    /**
     * 发布订阅推送消息码，与 light-redis-core 的 PubSubProtocol 保持一致。
     */
    private static final int PUBSUB_PUSH_CODE = 2;

    /**
     * 推送类型字段。
     */
    private static final String FIELD_TYPE = "type";

    /**
     * 推送频道字段。
     */
    private static final String FIELD_CHANNEL = "channel";

    /**
     * 推送模式字段。
     */
    private static final String FIELD_PATTERN = "pattern";

    /**
     * 模式匹配消息类型。
     */
    private static final String TYPE_PMESSAGE = "pmessage";

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
     * 发布订阅消息监听器。
     */
    private volatile PubSubListener pubSubListener;

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
            client.registerPushListener(PUBSUB_PUSH_CODE, this::handlePush);
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
    public void setPubSubListener(PubSubListener listener) {
        this.pubSubListener = listener;
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            client.unregisterPushListener(PUBSUB_PUSH_CODE);
            client.shutdown();
            started = false;
        }
    }

    /**
     * 处理服务端推送的订阅消息。
     *
     * @param message 推送消息
     */
    private void handlePush(RemotingCommand message) {
        PubSubListener listener = this.pubSubListener;
        if (listener == null) {
            return;
        }
        Map<String, String> fields = message.getExtFields();
        String type = fields == null ? null : fields.get(FIELD_TYPE);
        String channel = fields == null ? null : fields.get(FIELD_CHANNEL);
        String pattern = fields == null ? null : fields.get(FIELD_PATTERN);
        byte[] body = message.getBody();
        String payload = body == null ? "" : new String(body, StandardCharsets.UTF_8);
        PubSubMessage.Type messageType = TYPE_PMESSAGE.equals(type)
                ? PubSubMessage.Type.PMESSAGE
                : PubSubMessage.Type.MESSAGE;
        listener.onMessage(new PubSubMessage(messageType, channel, pattern, payload));
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
