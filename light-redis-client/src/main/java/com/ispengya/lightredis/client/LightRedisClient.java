package com.ispengya.lightredis.client;

import java.util.concurrent.CompletableFuture;

/**
 * light-redis 客户端核心接口。
 *
 * <p>面板和后续 SDK 都通过该接口访问服务端。</p>
 */
public interface LightRedisClient extends AutoCloseable {

    /**
     * 启动客户端并初始化底层连接组件。
     */
    void start();

    /**
     * 同步执行一条原始命令。
     *
     * @param command 原始命令行，例如 SET key value
     * @return 服务端返回的文本结果
     * @throws Exception 网络异常或调用超时
     */
    String execute(String command) throws Exception;

    /**
     * 异步执行一条原始命令。
     *
     * @param command 原始命令行
     * @return 以服务端文本结果为值的异步结果
     */
    CompletableFuture<String> executeAsync(String command);

    /**
     * 设置发布订阅消息监听器。
     *
     * @param listener 监听器，null 表示丢弃后续推送
     */
    void setPubSubListener(PubSubListener listener);

    /**
     * 关闭客户端并释放底层资源。
     */
    @Override
    void close();
}
