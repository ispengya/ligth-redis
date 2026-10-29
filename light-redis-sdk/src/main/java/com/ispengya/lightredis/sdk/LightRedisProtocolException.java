package com.ispengya.lightredis.sdk;

/**
 * 服务端回复无法按预期格式解析时抛出的异常。
 *
 * <p>通常表示客户端版本与服务端协议不一致，或文本协议中的多行数据
 * 出现了 SDK 无法区分的边界情况。</p>
 */
public class LightRedisProtocolException extends LightRedisException {

    /**
     * 使用错误信息创建异常。
     *
     * @param message 错误信息
     */
    public LightRedisProtocolException(String message) {
        super(message);
    }
}
