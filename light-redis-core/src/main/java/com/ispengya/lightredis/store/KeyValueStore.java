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

    /**
     * 为 key 设置相对过期时间。
     *
     * <p>剩余时间小于等于 0 时立即删除 key。</p>
     *
     * @param key key
     * @param ttlMillis 剩余毫秒数
     * @return key 存在并设置成功时返回 true
     */
    boolean expire(String key, long ttlMillis);

    /**
     * 为 key 设置绝对过期时间。
     *
     * <p>过期时间已经到达时立即删除 key。</p>
     *
     * @param key key
     * @param epochMillis 绝对过期时间，epoch 毫秒
     * @return key 存在并设置成功时返回 true
     */
    boolean expireAt(String key, long epochMillis);

    /**
     * 返回 key 的剩余过期时间。
     *
     * @param key key
     * @return -2 表示 key 不存在，-1 表示没有过期时间，否则为剩余毫秒数
     */
    long pttl(String key);

    /**
     * 移除 key 的过期时间。
     *
     * @param key key
     * @return 成功移除过期时间时返回 true
     */
    boolean persist(String key);

    /**
     * 返回 key 的绝对过期时间，不做惰性删除。
     *
     * <p>供 AOF 重写生成 PEXPIREAT 使用。返回的时间可能已经过期，
     * 调用方应原样记录，避免已经写入的数据失去 TTL。</p>
     *
     * @param key key
     * @return 绝对过期时间；key 没有过期时间时返回 null
     */
    Long getExpireAt(String key);

    /**
     * 抽样删除已过期的 key。
     *
     * <p>从带过期时间的 key 中轮转采样，过期比例较高时在时间预算内继续。</p>
     *
     * @param sampleLimit 每轮最多采样的 key 数量
     * @param deadlineNanos 本次清理的截止时间，System.nanoTime 时间基准
     * @return 实际删除的 key 数量
     */
    int purgeExpired(int sampleLimit, long deadlineNanos);

    /**
     * 全量删除已过期的 key。
     *
     * @return 实际删除的 key 数量
     */
    long purgeAllExpired();

    /**
     * 返回进程启动后因过期而删除的 key 数量。
     *
     * @return 过期删除数量
     */
    long getExpiredCount();
}
