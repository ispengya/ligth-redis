package com.ispengya.lightredis.sdk.internal;

import java.util.Collection;
import java.util.Map;

/**
 * SDK 参数校验工具。
 */
public final class Args {

    /**
     * 工具类不允许实例化。
     */
    private Args() {
    }

    /**
     * 校验字符串非空引用。
     *
     * @param value 字符串
     * @param name 参数名称，用于错误信息
     * @return 原字符串
     */
    public static String requireText(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }

    /**
     * 校验 key 并拒绝换行符。
     *
     * <p>key 会出现在 KEYS 等逐行返回的结果中，包含换行会导致
     * 客户端无法区分 key 边界。</p>
     *
     * @param key key
     * @return 原 key
     */
    public static String requireKey(String key) {
        return requireLineSafe(key, "Key");
    }

    /**
     * 校验值可以安全出现在逐行结果中。
     *
     * @param value 值
     * @param name 参数名称，用于错误信息
     * @return 原值
     */
    public static String requireLineSafe(String value, String name) {
        requireText(value, name);
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(name + " must not contain line breaks");
        }
        return value;
    }

    /**
     * 校验数组至少包含一个元素，且每个元素都不含换行。
     *
     * @param values 值数组
     * @param name 参数名称，用于错误信息
     */
    public static void requireLineSafeAll(String[] values, String name) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        for (String value : values) {
            requireLineSafe(value, name);
        }
    }

    /**
     * 校验集合非空。
     *
     * @param values 集合
     * @param name 参数名称，用于错误信息
     */
    public static void requireNonEmpty(Collection<?> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }

    /**
     * 校验映射非空。
     *
     * @param values 映射
     * @param name 参数名称，用于错误信息
     */
    public static void requireNonEmpty(Map<?, ?> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
