package com.ispengya.lightredis.sdk;

/**
 * light-redis SDK 的基础运行时异常。
 *
 * <p>SDK 的同步 API 不抛受检异常，所有失败都以该异常或其子类返回。</p>
 */
public class LightRedisException extends RuntimeException {

    /**
     * 使用错误信息创建异常。
     *
     * @param message 错误信息
     */
    public LightRedisException(String message) {
        super(message);
    }

    /**
     * 使用错误信息和原因创建异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public LightRedisException(String message, Throwable cause) {
        super(message, cause);
    }
}
