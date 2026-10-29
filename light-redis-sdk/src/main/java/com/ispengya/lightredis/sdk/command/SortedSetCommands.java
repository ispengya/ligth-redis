package com.ispengya.lightredis.sdk.command;

import java.util.List;
import java.util.Map;

/**
 * Sorted Set 命令。
 *
 * <p>成员不能包含换行符；带分值的查询结果按成员和分值拆分。</p>
 */
public interface SortedSetCommands {

    /**
     * 添加或更新一个成员的分值。
     *
     * @param key key
     * @param score 分值
     * @param member 成员
     * @return 新增成员的数量，更新已有成员时返回 0
     */
    long zadd(String key, double score, String member);

    /**
     * 批量添加或更新成员的分值。
     *
     * @param key key
     * @param members 成员到分值的映射，至少一个
     * @return 新增成员的数量
     */
    long zadd(String key, Map<String, Double> members);

    /**
     * 删除一个或多个成员。
     *
     * @param key key
     * @param members 至少一个成员
     * @return 实际删除的成员数量
     */
    long zrem(String key, String... members);

    /**
     * 返回成员的分值。
     *
     * @param key key
     * @param member 成员
     * @return 分值；成员或 key 不存在时返回 null
     */
    Double zscore(String key, String member);

    /**
     * 返回成员数量。
     *
     * @param key key
     * @return 成员数量，key 不存在时返回 0
     */
    long zcard(String key);

    /**
     * 按分值升序返回指定区间的成员。
     *
     * @param key key
     * @param start 起始索引，支持负数
     * @param stop 结束索引，支持负数
     * @return 成员列表，key 不存在时返回空列表
     */
    List<String> zrange(String key, int start, int stop);

    /**
     * 按分值升序返回指定区间的成员及其分值。
     *
     * @param key key
     * @param start 起始索引，支持负数
     * @param stop 结束索引，支持负数
     * @return 成员和分值列表，key 不存在时返回空列表
     */
    List<ZSetEntry> zrangeWithScores(String key, int start, int stop);

    /**
     * 返回成员按分值升序排列的名次。
     *
     * @param key key
     * @param member 成员
     * @return 从 0 开始的名次；成员或 key 不存在时返回 null
     */
    Long zrank(String key, String member);
}
