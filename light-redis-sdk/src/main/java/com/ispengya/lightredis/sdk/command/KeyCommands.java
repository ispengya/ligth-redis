package com.ispengya.lightredis.sdk.command;

import java.util.List;

/**
 * 通用 key 与过期时间命令。
 */
public interface KeyCommands {

    /**
     * 删除一个或多个 key。
     *
     * @param keys 至少一个 key
     * @return 实际删除的 key 数量
     */
    long del(String... keys);

    /**
     * 判断 key 是否存在。
     *
     * @param key key
     * @return 存在时返回 true
     */
    boolean exists(String key);

    /**
     * 返回 key 的数据类型。
     *
     * @param key key
     * @return string、list、hash、set、zset 或 none
     */
    String type(String key);

    /**
     * 按 glob 模式查找 key。
     *
     * @param pattern glob 模式，支持 * 和 ?
     * @return 按字典序排列的 key 列表
     */
    List<String> keys(String pattern);

    /**
     * 返回当前 key 总数。
     *
     * @return key 数量
     */
    long dbsize();

    /**
     * 以秒为单位设置 key 的过期时间。
     *
     * @param key key
     * @param seconds 过期秒数，非正数会立即删除 key
     * @return 设置成功时返回 true
     */
    boolean expire(String key, long seconds);

    /**
     * 以毫秒为单位设置 key 的过期时间。
     *
     * @param key key
     * @param millis 过期毫秒数，非正数会立即删除 key
     * @return 设置成功时返回 true
     */
    boolean pexpire(String key, long millis);

    /**
     * 以 Unix 秒时间戳设置 key 的绝对过期时间。
     *
     * @param key key
     * @param epochSeconds Unix 秒时间戳
     * @return 设置成功时返回 true
     */
    boolean expireAt(String key, long epochSeconds);

    /**
     * 以 Unix 毫秒时间戳设置 key 的绝对过期时间。
     *
     * @param key key
     * @param epochMillis Unix 毫秒时间戳
     * @return 设置成功时返回 true
     */
    boolean pexpireAt(String key, long epochMillis);

    /**
     * 返回 key 的剩余过期秒数。
     *
     * @param key key
     * @return 剩余秒数；-1 表示没有过期时间，-2 表示 key 不存在
     */
    long ttl(String key);

    /**
     * 返回 key 的剩余过期毫秒数。
     *
     * @param key key
     * @return 剩余毫秒数；-1 表示没有过期时间，-2 表示 key 不存在
     */
    long pttl(String key);

    /**
     * 移除 key 的过期时间。
     *
     * @param key key
     * @return 实际移除了过期时间时返回 true
     */
    boolean persist(String key);
}
