package com.ispengya.lightredis.sdk;

/**
 * 服务端返回错误回复时抛出的异常。
 *
 * <p>例如命令参数错误、key 类型不匹配、持久化不可用等。
 * 原始回复保存在 {@link #getReply()} 中。</p>
 */
public class RedisCommandException extends LightRedisException {

    /**
     * 服务端返回的原始文本回复。
     */
    private final String reply;

    /**
     * 使用服务端回复创建异常。
     *
     * @param reply 服务端错误回复
     */
    public RedisCommandException(String reply) {
        super(reply);
        this.reply = reply;
    }

    /**
     * 返回服务端原始错误回复。
     *
     * @return 原始回复
     */
    public String getReply() {
        return reply;
    }
}
