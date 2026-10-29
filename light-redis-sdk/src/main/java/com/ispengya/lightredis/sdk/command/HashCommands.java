package com.ispengya.lightredis.sdk.command;

import java.util.List;
import java.util.Map;

/**
 * Hash 命令。
 *
 * <p>字段名和字段值不能包含换行符，原因与列表元素相同。</p>
 */
public interface HashCommands {

    /**
     * 设置一个字段。
     *
     * @param key key
     * @param field 字段名
     * @param value 字段值
     * @return 新增字段的数量，更新已有字段时返回 0
     */
    long hset(String key, String field, String value);

    /**
     * 批量设置字段。
     *
     * @param key key
     * @param fields 字段与值，至少一个
     * @return 新增字段的数量
     */
    long hset(String key, Map<String, String> fields);

    /**
     * 获取字段值。
     *
     * @param key key
     * @param field 字段名
     * @return 字段值；字段或 key 不存在时返回 null
     */
    String hget(String key, String field);

    /**
     * 删除一个或多个字段。
     *
     * @param key key
     * @param fields 至少一个字段名
     * @return 实际删除的字段数量
     */
    long hdel(String key, String... fields);

    /**
     * 判断字段是否存在。
     *
     * @param key key
     * @param field 字段名
     * @return 存在时返回 true
     */
    boolean hexists(String key, String field);

    /**
     * 返回字段数量。
     *
     * @param key key
     * @return 字段数量，key 不存在时返回 0
     */
    long hlen(String key);

    /**
     * 返回全部字段和值。
     *
     * @param key key
     * @return 字段到值的映射，key 不存在时返回空映射
     */
    Map<String, String> hgetall(String key);

    /**
     * 返回全部字段名。
     *
     * @param key key
     * @return 字段名列表，key 不存在时返回空列表
     */
    List<String> hkeys(String key);

    /**
     * 返回全部字段值。
     *
     * @param key key
     * @return 字段值列表，key 不存在时返回空列表
     */
    List<String> hvals(String key);
}
