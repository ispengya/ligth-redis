package com.ispengya.lightredis.sdk.command;

/**
 * String 命令。
 */
public interface StringCommands {

    /**
     * 设置字符串值，覆盖原有值和过期时间。
     *
     * @param key key
     * @param value 字符串值，允许空串
     */
    void set(String key, String value);

    /**
     * 获取字符串值。
     *
     * @param key key
     * @return 字符串值；key 不存在或类型不是 string 时返回 null
     */
    String get(String key);
}
