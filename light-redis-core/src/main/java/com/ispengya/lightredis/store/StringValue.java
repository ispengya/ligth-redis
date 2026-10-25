package com.ispengya.lightredis.store;

/**
 * 字符串值。
 */
public final class StringValue implements RedisValue {

    /**
     * 字符串内容。
     */
    private final String value;

    /**
     * 创建字符串值。
     *
     * @param value 字符串内容，不允许为 null
     */
    public StringValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("String value must not be null");
        }
        this.value = value;
    }

    @Override
    public RedisType getType() {
        return RedisType.STRING;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    /**
     * 返回字符串内容。
     *
     * @return 字符串内容
     */
    public String getValue() {
        return value;
    }
}
