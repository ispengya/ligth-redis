package com.ispengya.lightredis.store;

/**
 * 对 key 执行了与当前数据类型不匹配的命令时抛出。
 */
public class WrongTypeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 与 Redis 保持一致的错误信息。
     */
    public static final String MESSAGE =
            "WRONGTYPE Operation against a key holding the wrong kind of value";

    /**
     * 创建类型不匹配异常。
     */
    public WrongTypeException() {
        super(MESSAGE);
    }
}
