package com.ispengya.lightredis.pubsub;

/**
 * 发布订阅推送消息的协议约定。
 *
 * <p>客户端需要与这里保持相同的消息码和字段名。</p>
 */
public final class PubSubProtocol {

    /**
     * 推送消息码。
     */
    public static final int PUSH_CODE = 2;

    /**
     * 推送类型字段。
     */
    public static final String FIELD_TYPE = "type";

    /**
     * 频道字段。
     */
    public static final String FIELD_CHANNEL = "channel";

    /**
     * 模式字段，仅 pmessage 使用。
     */
    public static final String FIELD_PATTERN = "pattern";

    /**
     * 精确频道消息。
     */
    public static final String TYPE_MESSAGE = "message";

    /**
     * 模式匹配消息。
     */
    public static final String TYPE_PMESSAGE = "pmessage";

    private PubSubProtocol() {
    }
}
