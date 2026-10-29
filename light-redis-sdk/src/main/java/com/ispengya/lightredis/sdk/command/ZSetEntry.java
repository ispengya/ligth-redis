package com.ispengya.lightredis.sdk.command;

/**
 * 有序集合中的一个成员及其分值。
 */
public final class ZSetEntry {

    /**
     * 成员。
     */
    private final String member;

    /**
     * 分值。
     */
    private final double score;

    /**
     * 创建一个成员分值对。
     *
     * @param member 成员
     * @param score 分值
     */
    public ZSetEntry(String member, double score) {
        this.member = member;
        this.score = score;
    }

    /**
     * 返回成员。
     *
     * @return 成员
     */
    public String getMember() {
        return member;
    }

    /**
     * 返回分值。
     *
     * @return 分值
     */
    public double getScore() {
        return score;
    }

    /**
     * 判断两个成员分值对是否相同。
     *
     * @param other 另一个对象
     * @return 成员和分值都相同时返回 true
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ZSetEntry)) {
            return false;
        }
        ZSetEntry entry = (ZSetEntry) other;
        return Double.compare(entry.score, score) == 0
                && (member == null ? entry.member == null : member.equals(entry.member));
    }

    /**
     * 返回成员分值对的哈希值。
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        int result = member == null ? 0 : member.hashCode();
        long bits = Double.doubleToLongBits(score);
        result = 31 * result + (int) (bits ^ (bits >>> 32));
        return result;
    }

    /**
     * 返回可读文本。
     *
     * @return member=score
     */
    @Override
    public String toString() {
        return member + "=" + score;
    }
}
