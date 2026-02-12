package com.ispengya.lightredis.store;

import java.util.List;

public interface KeyValueStore {

    String get(String key);

    void set(String key, String value);

    long del(List<String> keys);

    boolean exists(String key);
}
