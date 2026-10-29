package com.ispengya.lightredis.sdk.pubsub;

/**
 * 一条发布订阅消息。
 */
public final class PubSubMessage {

    /**
     * 消息来源类型。
     */
    public enum Type {

        /**
         * 精确频道订阅收到的消息。
         */
        MESSAGE,

        /**
         * 模式订阅收到的消息。
         */
        PMESSAGE
    }

    /**
     * 消息类型。
     */
    private final Type type;

    /**
     * 实际发布消息的频道。
     */
    private final String channel;

    /**
     * 命中的模式，仅 PMESSAGE 有值。
     */
    private final String pattern;

    /**
     * 消息内容。
     */
    private final String payload;

    /**
     * 创建发布订阅消息。
     *
     * @param type 消息类型
     * @param channel 频道
     * @param pattern 命中模式
     * @param payload 消息内容
     */
    public PubSubMessage(Type type, String channel, String pattern, String payload) {
        this.type = type;
        this.channel = channel;
        this.pattern = pattern;
        this.payload = payload;
    }

    /**
     * 返回消息类型。
     *
     * @return 消息类型
     */
    public Type getType() {
        return type;
    }

    /**
     * 返回消息频道。
     *
     * @return 频道
     */
    public String getChannel() {
        return channel;
    }

    /**
     * 返回命中的模式。
     *
     * @return 模式；精确频道消息返回 null
     */
    public String getPattern() {
        return pattern;
    }

    /**
     * 返回消息内容。
     *
     * @return 消息内容
     */
    public String getPayload() {
        return payload;
    }

    /**
     * 判断消息是否来自模式订阅。
     *
     * @return 模式消息返回 true
     */
    public boolean isPatternMessage() {
        return type == Type.PMESSAGE;
    }

    /**
     * 返回可读文本。
     *
     * @return 消息描述
     */
    @Override
    public String toString() {
        if (isPatternMessage()) {
            return "[pattern:" + pattern + "] " + channel + " -> " + payload;
        }
        return "[" + channel + "] " + payload;
    }
}
