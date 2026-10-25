package com.ispengya.lightredis.store;

import java.util.List;

/**
 * key-value 存储接口。
 */
public interface KeyValueStore {

    /**
     * 读取 key 对应的值。
     *
     * @param key key
     * @return 值，key 不存在时返回 null
     */
    RedisValue get(String key);

    /**
     * 写入值，覆盖原有内容以及原有数据类型。
     *
     * @param key key
     * @param value 值
     */
    void set(String key, RedisValue value);

    /**
     * 当值已经没有内容时删除对应的 key。
     *
     * @param key key
     * @param value 当前值
     */
    void removeIfEmpty(String key, RedisValue value);

    /**
     * 删除一个或多个 key。
     *
     * @param keys 要删除的 key
     * @return 实际删除的 key 数量
     */
    long del(List<String> keys);

    /**
     * 判断 key 是否存在。
     *
     * @param key key
     * @return 存在时返回 true
     */
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
     * @return 类型名称，key 不存在时返回 none
     */
    String type(String key);
}
