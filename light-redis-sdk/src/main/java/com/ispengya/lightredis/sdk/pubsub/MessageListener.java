package com.ispengya.lightredis.sdk.pubsub;

/**
 * 发布订阅消息监听器。
 *
 * <p>回调在 SDK 的单个分发线程上串行执行，同一客户端的消息保持到达顺序。
 * 回调中可以安全地执行普通命令或关闭订阅，但应避免长时间阻塞。</p>
 */
@FunctionalInterface
public interface MessageListener {

    /**
     * 处理一条订阅消息。
     *
     * @param message 订阅消息
     */
    void onMessage(PubSubMessage message);
}
