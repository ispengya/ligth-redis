package com.ispengya.lightredis.persistence;

/**
 * AOF 打开、追加或刷盘失败时抛出。
 */
public class PersistenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建持久化异常。
     *
     * @param message 错误信息
     */
    public PersistenceException(String message) {
        super(message);
    }

    /**
     * 创建持久化异常。
     *
     * @param message 错误信息
     * @param cause 原始异常
     */
    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
