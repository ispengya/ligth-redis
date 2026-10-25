package com.ispengya.lightredis.store;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 基于内存的 key-value 存储。
 *
 * <p>key 的增删使用并发 Map 保证可见性；具体值由命令执行线程串行修改。</p>
 */
public class InMemoryKeyValueStore implements KeyValueStore {

    /**
     * 所有 key 及其值。
     */
    private final ConcurrentMap<String, RedisValue> data = new ConcurrentHashMap<>();

    @Override
    public RedisValue get(String key) {
        if (key == null) {
            return null;
        }
        return data.get(key);
    }

    @Override
    public void set(String key, RedisValue value) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
        data.put(key, value);
    }

    @Override
    public void removeIfEmpty(String key, RedisValue value) {
        if (key == null || value == null) {
            return;
        }
        if (value.isEmpty()) {
            data.remove(key, value);
        }
    }

    @Override
    public long del(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0L;
        }
        long count = 0L;
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            if (data.remove(key) != null) {
                count++;
            }
        }
        return count;
    }

    @Override
    public boolean exists(String key) {
        if (key == null) {
            return false;
        }
        return data.containsKey(key);
    }

    @Override
    public List<String> keys() {
        return new ArrayList<>(data.keySet());
    }

    @Override
    public long size() {
        return data.size();
    }

    @Override
    public String type(String key) {
        RedisValue value = get(key);
        return value == null ? "none" : value.getType().getCode();
    }
}
