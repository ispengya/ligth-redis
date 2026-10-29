package com.ispengya.lightredis.sdk.command;

import java.util.List;

/**
 * List 命令。
 *
 * <p>列表元素不能包含换行符：服务端以换行分隔多个元素，
 * 包含换行的元素无法被可靠解析。</p>
 */
public interface ListCommands {

    /**
     * 从列表左侧插入一个或多个元素。
     *
     * @param key key
     * @param values 至少一个元素
     * @return 插入后的列表长度
     */
    long lpush(String key, String... values);

    /**
     * 从列表右侧插入一个或多个元素。
     *
     * @param key key
     * @param values 至少一个元素
     * @return 插入后的列表长度
     */
    long rpush(String key, String... values);

    /**
     * 从列表左侧弹出一个元素。
     *
     * @param key key
     * @return 元素；列表不存在或为空时返回 null
     */
    String lpop(String key);

    /**
     * 从列表右侧弹出一个元素。
     *
     * @param key key
     * @return 元素；列表不存在或为空时返回 null
     */
    String rpop(String key);

    /**
     * 返回列表长度。
     *
     * @param key key
     * @return 列表长度，key 不存在时返回 0
     */
    long llen(String key);

    /**
     * 返回指定区间的元素，区间包含 start 和 stop。
     *
     * @param key key
     * @param start 起始索引，支持负数
     * @param stop 结束索引，支持负数
     * @return 元素列表，key 不存在时返回空列表
     */
    List<String> lrange(String key, int start, int stop);

    /**
     * 返回指定索引的元素。
     *
     * @param key key
     * @param index 索引，支持负数
     * @return 元素；索引越界或 key 不存在时返回 null
     */
    String lindex(String key, int index);

    /**
     * 修改指定索引的元素。
     *
     * @param key key
     * @param index 索引，支持负数
     * @param value 新元素
     */
    void lset(String key, int index, String value);

    /**
     * 从列表中删除指定元素。
     *
     * @param key key
     * @param count 删除方向与数量：正数从左往右，负数从右往左，0 表示全部
     * @param value 要删除的元素
     * @return 实际删除的元素数量
     */
    long lrem(String key, int count, String value);
}
