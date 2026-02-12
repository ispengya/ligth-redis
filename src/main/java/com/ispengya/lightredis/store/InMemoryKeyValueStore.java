package com.ispengya.lightredis.store;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryKeyValueStore implements KeyValueStore {

    private final ConcurrentMap<String, String> data = new ConcurrentHashMap<>();

    @Override
    public String get(String key) {
        if (key == null) {
            return null;
        }
        return data.get(key);
    }

    @Override
    public void set(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }
        data.put(key, value);
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
}
