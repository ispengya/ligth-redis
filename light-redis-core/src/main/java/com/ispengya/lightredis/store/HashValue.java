package com.ispengya.lightredis.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 哈希表值。
 *
 * <p>使用 {@link LinkedHashMap} 保留字段的插入顺序，方便在 CLI 和面板中稳定展示。</p>
 */
public final class HashValue implements RedisValue {

    /**
     * 字段与值的映射。
     */
    private final LinkedHashMap<String, String> fields = new LinkedHashMap<>();

    @Override
    public RedisType getType() {
        return RedisType.HASH;
    }

    @Override
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * 设置字段值。
     *
     * @param field 字段名
     * @param value 字段值
     * @return 字段原来不存在时返回 true
     */
    public boolean put(String field, String value) {
        return fields.put(field, value) == null;
    }

    /**
     * 读取字段值。
     *
     * @param field 字段名
     * @return 字段值，字段不存在时返回 null
     */
    public String get(String field) {
        return fields.get(field);
    }

    /**
     * 删除字段。
     *
     * @param field 字段名
     * @return 字段存在并被删除时返回 true
     */
    public boolean remove(String field) {
        return fields.remove(field) != null;
    }

    /**
     * 判断字段是否存在。
     *
     * @param field 字段名
     * @return 存在时返回 true
     */
    public boolean contains(String field) {
        return fields.containsKey(field);
    }

    /**
     * 返回字段数量。
     *
     * @return 字段数量
     */
    public int size() {
        return fields.size();
    }

    /**
     * 返回所有字段名。
     *
     * @return 字段名列表
     */
    public List<String> fieldNames() {
        return new ArrayList<>(fields.keySet());
    }

    /**
     * 返回所有字段值。
     *
     * @return 字段值列表
     */
    public List<String> values() {
        return new ArrayList<>(fields.values());
    }

    /**
     * 返回所有字段和值的条目。
     *
     * @return 条目列表
     */
    public List<Map.Entry<String, String>> entries() {
        return new ArrayList<>(fields.entrySet());
    }
}
