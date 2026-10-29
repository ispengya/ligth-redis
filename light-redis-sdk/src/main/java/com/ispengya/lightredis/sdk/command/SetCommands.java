package com.ispengya.lightredis.sdk.command;

import java.util.Set;

/**
 * Set 命令。
 *
 * <p>集合成员不能包含换行符，原因与列表元素相同。</p>
 */
public interface SetCommands {

    /**
     * 添加一个或多个成员。
     *
     * @param key key
     * @param members 至少一个成员
     * @return 实际新增的成员数量
     */
    long sadd(String key, String... members);

    /**
     * 删除一个或多个成员。
     *
     * @param key key
     * @param members 至少一个成员
     * @return 实际删除的成员数量
     */
    long srem(String key, String... members);

    /**
     * 返回全部成员。
     *
     * @param key key
     * @return 成员集合，key 不存在时返回空集合
     */
    Set<String> smembers(String key);

    /**
     * 判断成员是否存在。
     *
     * @param key key
     * @param member 成员
     * @return 存在时返回 true
     */
    boolean sismember(String key, String member);

    /**
     * 返回成员数量。
     *
     * @param key key
     * @return 成员数量，key 不存在时返回 0
     */
    long scard(String key);
}
