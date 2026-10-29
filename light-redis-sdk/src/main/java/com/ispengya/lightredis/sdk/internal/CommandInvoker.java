package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.client.LightRedisClient;
import com.ispengya.lightredis.client.PubSubListener;
import com.ispengya.lightredis.sdk.LightRedisConnectionException;
import com.ispengya.lightredis.sdk.LightRedisException;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 命令调用与异常转换。
 *
 * <p>负责构造命令文本、调用底层客户端、识别服务端错误，
 * 并把网络异常转换为 SDK 异常。实例线程安全。</p>
 */
public final class CommandInvoker implements AutoCloseable {

    /**
     * 底层客户端。
     */
    private final LightRedisClient client;

    /**
     * 是否已经启动。
     */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /**
     * 是否已经关闭。
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * 创建命令调用器。
     *
     * @param client 底层客户端
     */
    public CommandInvoker(LightRedisClient client) {
        if (client == null) {
            throw new IllegalArgumentException("Client must not be null");
        }
        this.client = client;
    }

    /**
     * 启动底层客户端。
     */
    public void start() {
        ensureOpen();
        if (started.compareAndSet(false, true)) {
            try {
                client.start();
            } catch (RuntimeException e) {
                started.set(false);
                throw new LightRedisConnectionException("Failed to start light-redis client", e);
            }
        }
    }

    /**
     * 注册服务端推送监听器。
     *
     * @param listener 推送监听器
     */
    public void setPubSubListener(PubSubListener listener) {
        client.setPubSubListener(listener);
    }

    /**
     * 执行一条类型化命令。
     *
     * @param command 命令名
     * @param args 命令参数
     * @return 服务端原始回复
     */
    public String execute(String command, String... args) {
        String line = CommandEncoder.encode(command, args);
        return invoke(line);
    }

    /**
     * 执行一条原始命令文本。
     *
     * @param command 完整命令文本
     * @return 服务端原始回复
     */
    public String executeRaw(String command) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }
        return invoke(command);
    }

    /**
     * 判断客户端是否已关闭。
     *
     * @return 已关闭返回 true
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * 关闭底层客户端。
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            client.close();
        }
    }

    /**
     * 调用底层客户端并转换异常。
     *
     * @param command 完整命令文本
     * @return 服务端原始回复
     */
    private String invoke(String command) {
        ensureOpen();
        start();
        try {
            String reply = client.execute(command);
            ReplyParser.checkError(reply);
            return reply;
        } catch (LightRedisException e) {
            throw e;
        } catch (Exception e) {
            throw new LightRedisConnectionException("Failed to execute command: " + command, e);
        }
    }

    /**
     * 确保客户端尚未关闭。
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new LightRedisException("Client is closed");
        }
    }
}
