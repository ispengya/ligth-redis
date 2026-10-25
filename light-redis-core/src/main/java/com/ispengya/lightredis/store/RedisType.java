package com.ispengya.lightredis.store;

/**
 * light-redis 支持的数据类型。
 */
public enum RedisType {

    /**
     * 字符串。
     */
    STRING("string"),

    /**
     * 列表。
     */
    LIST("list"),

    /**
     * 哈希表。
     */
    HASH("hash"),

    /**
     * 集合。
     */
    SET("set"),

    /**
     * 有序集合。
     */
    ZSET("zset");

    /**
     * TYPE 命令返回的类型名称。
     */
    private final String code;

    /**
     * 创建类型枚举。
     *
     * @param code TYPE 命令返回的类型名称
     */
    RedisType(String code) {
        this.code = code;
    }

    /**
     * 返回 TYPE 命令使用的类型名称。
     *
     * @return 类型名称
     */
    public String getCode() {
        return code;
    }
}
