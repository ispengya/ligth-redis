package com.ispengya.lightredis.sdk.command;

import com.ispengya.lightredis.sdk.pubsub.MessageListener;
import com.ispengya.lightredis.sdk.pubsub.Subscription;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 发布订阅命令。
 *
 * <p>订阅关系属于当前连接，不写入 AOF；连接关闭后服务端自动清理。
 * 同一连接可以同时订阅频道和模式。</p>
 */
public interface PubSubCommands {

    /**
     * 向频道发布消息。
     *
     * @param channel 频道
     * @param message 消息内容，允许换行
     * @return 收到消息的订阅数量
     */
    long publish(String channel, String message);

    /**
     * 订阅单个频道。
     *
     * @param channel 频道
     * @param listener 消息监听器
     * @return 订阅句柄
     */
    Subscription subscribe(String channel, MessageListener listener);

    /**
     * 订阅多个频道。
     *
     * @param channels 频道集合，至少一个
     * @param listener 消息监听器
     * @return 订阅句柄
     */
    Subscription subscribe(Collection<String> channels, MessageListener listener);

    /**
     * 订阅单个 glob 模式。
     *
     * @param pattern glob 模式
     * @param listener 消息监听器
     * @return 订阅句柄
     */
    Subscription psubscribe(String pattern, MessageListener listener);

    /**
     * 订阅多个 glob 模式。
     *
     * @param patterns 模式集合，至少一个
     * @param listener 消息监听器
     * @return 订阅句柄
     */
    Subscription psubscribe(Collection<String> patterns, MessageListener listener);

    /**
     * 返回当前有订阅者的所有频道。
     *
     * @return 按字典序排列的频道列表
     */
    List<String> channels();

    /**
     * 返回匹配模式且有订阅者的频道。
     *
     * @param pattern glob 模式
     * @return 按字典序排列的频道列表
     */
    List<String> channels(String pattern);

    /**
     * 返回指定频道的订阅者数量。
     *
     * @param channels 频道，可以为空
     * @return 频道到订阅数数量的映射
     */
    Map<String, Long> numSub(String... channels);

    /**
     * 返回模式订阅关系的总数。
     *
     * @return 模式订阅数量
     */
    long numPat();
}
