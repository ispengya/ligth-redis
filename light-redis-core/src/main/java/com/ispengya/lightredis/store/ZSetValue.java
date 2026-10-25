package com.ispengya.lightredis.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 有序集合值。
 *
 * <p>成员到分值的映射使用哈希表保存，读取时按分值升序排序；
 * 分值相同时按成员字典序排列，便于得到稳定的结果。</p>
 */
public final class ZSetValue implements RedisValue {

    /**
     * 成员与分值的映射。
     */
    private final Map<String, Double> scores = new HashMap<>();

    @Override
    public RedisType getType() {
        return RedisType.ZSET;
    }

    @Override
    public boolean isEmpty() {
        return scores.isEmpty();
    }

    /**
     * 添加或更新成员分值。
     *
     * @param member 成员
     * @param score 分值
     * @return 成员原来不存在时返回 true
     */
    public boolean add(String member, double score) {
        return scores.put(member, score) == null;
    }

    /**
     * 返回成员分值。
     *
     * @param member 成员
     * @return 分值，成员不存在时返回 null
     */
    public Double score(String member) {
        return scores.get(member);
    }

    /**
     * 删除成员。
     *
     * @param member 成员
     * @return 成员存在并被删除时返回 true
     */
    public boolean remove(String member) {
        return scores.remove(member) != null;
    }

    /**
     * 返回成员数量。
     *
     * @return 成员数量
     */
    public int size() {
        return scores.size();
    }

    /**
     * 返回成员按分值升序排列的名次。
     *
     * @param member 成员
     * @return 从 0 开始的名次，成员不存在时返回 -1
     */
    public int rank(String member) {
        List<ScoredMember> sorted = sorted();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getMember().equals(member)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 返回按分值升序排列的指定区间成员。
     *
     * @param start 起始索引，负数表示从表尾开始计数
     * @param stop 结束索引，负数表示从表尾开始计数
     * @return 区间内的成员，区间无效时返回空列表
     */
    public List<ScoredMember> range(int start, int stop) {
        List<ScoredMember> sorted = sorted();
        int size = sorted.size();
        if (size == 0) {
            return Collections.emptyList();
        }

        long startIndex = start < 0 ? (long) size + start : start;
        long stopIndex = stop < 0 ? (long) size + stop : stop;
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (stopIndex < 0 || startIndex >= size || startIndex > stopIndex) {
            return Collections.emptyList();
        }
        if (stopIndex >= size) {
            stopIndex = size - 1;
        }
        return new ArrayList<>(sorted.subList((int) startIndex, (int) stopIndex + 1));
    }

    /**
     * 返回按分值升序排列的全部成员。
     *
     * @return 排好序的成员列表
     */
    private List<ScoredMember> sorted() {
        List<ScoredMember> result = new ArrayList<>(scores.size());
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            result.add(new ScoredMember(entry.getKey(), entry.getValue()));
        }
        Collections.sort(result, new Comparator<ScoredMember>() {
            @Override
            public int compare(ScoredMember left, ScoredMember right) {
                int byScore = Double.compare(left.getScore(), right.getScore());
                if (byScore != 0) {
                    return byScore;
                }
                return left.getMember().compareTo(right.getMember());
            }
        });
        return result;
    }

    /**
     * 有序集合中的一个成员及其分值。
     */
    public static final class ScoredMember {

        /**
         * 成员内容。
         */
        private final String member;

        /**
         * 成员分值。
         */
        private final double score;

        /**
         * 创建成员条目。
         *
         * @param member 成员内容
         * @param score 成员分值
         */
        public ScoredMember(String member, double score) {
            this.member = member;
            this.score = score;
        }

        /**
         * 返回成员内容。
         *
         * @return 成员内容
         */
        public String getMember() {
            return member;
        }

        /**
         * 返回成员分值。
         *
         * @return 成员分值
         */
        public double getScore() {
            return score;
        }
    }
}
