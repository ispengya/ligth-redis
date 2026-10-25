package com.ispengya.lightredis.store;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 集合值。
 *
 * <p>使用 {@link LinkedHashSet} 去重并保留插入顺序。</p>
 */
public final class SetValue implements RedisValue {

    /**
     * 集合成员。
     */
    private final LinkedHashSet<String> members = new LinkedHashSet<>();

    @Override
    public RedisType getType() {
        return RedisType.SET;
    }

    @Override
    public boolean isEmpty() {
        return members.isEmpty();
    }

    /**
     * 添加成员。
     *
     * @param member 成员
     * @return 成员原来不存在时返回 true
     */
    public boolean add(String member) {
        return members.add(member);
    }

    /**
     * 删除成员。
     *
     * @param member 成员
     * @return 成员存在并被删除时返回 true
     */
    public boolean remove(String member) {
        return members.remove(member);
    }

    /**
     * 判断成员是否存在。
     *
     * @param member 成员
     * @return 存在时返回 true
     */
    public boolean contains(String member) {
        return members.contains(member);
    }

    /**
     * 返回成员数量。
     *
     * @return 成员数量
     */
    public int size() {
        return members.size();
    }

    /**
     * 返回所有成员。
     *
     * @return 成员列表
     */
    public List<String> members() {
        return new ArrayList<>(members);
    }
}
