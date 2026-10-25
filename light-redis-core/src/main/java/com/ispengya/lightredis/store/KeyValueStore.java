package com.ispengya.lightredis.store;

import java.util.List;

public interface KeyValueStore {

    String get(String key);

    void set(String key, String value);

    long del(List<String> keys);

    boolean exists(String key);

    /**
     * 返回当前所有 key 的快照。
     *
     * @return key 列表
     */
    List<String> keys();

    /**
     * 返回当前 key 的总数。
     *
     * @return key 数量
     */
    long size();

    /**
     * 返回指定 key 的类型名称。
     *
     * @param key 要查询的 key
     * @return 存在时返回 string，不存在返回 none
     */
    String type(String key);
}
