package com.ispengya.lightredis.client;

/**
 * 发布订阅消息监听器。
 *
 * <p>监听器在网络事件循环中执行，不应阻塞。</p>
 */
@FunctionalInterface
public interface PubSubListener {

    /**
     * 处理一条订阅消息。
     *
     * @param message 订阅消息
     */
    void onMessage(PubSubMessage message);
}
