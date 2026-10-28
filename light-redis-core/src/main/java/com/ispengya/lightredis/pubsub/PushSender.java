package com.ispengya.lightredis.pubsub;

import com.ispengya.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;

/**
 * 向订阅连接发送推送消息。
 */
@FunctionalInterface
public interface PushSender {

    /**
     * 推送一条消息。
     *
     * @param channel 目标连接
     * @param message 推送消息
     */
    void push(Channel channel, RemotingCommand message);
}
