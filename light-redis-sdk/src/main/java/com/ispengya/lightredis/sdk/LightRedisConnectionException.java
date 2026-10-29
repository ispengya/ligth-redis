package com.ispengya.lightredis.sdk;

/**
 * 连接、网络或调用超时相关异常。
 *
 * <p>原始网络异常通过 {@link #getCause()} 保留。</p>
 */
public class LightRedisConnectionException extends LightRedisException {

    /**
     * 使用错误信息和原因创建异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public LightRedisConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
