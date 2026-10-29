package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.SortedSetCommands;
import com.ispengya.lightredis.sdk.command.ZSetEntry;

import java.util.List;
import java.util.Map;

/**
 * Sorted Set 命令实现。
 */
public final class SortedSetCommandsImpl implements SortedSetCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public SortedSetCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public long zadd(String key, double score, String member) {
        Args.requireKey(key);
        Args.requireLineSafe(member, "Sorted set member");
        validateScore(score);
        return ReplyParser.longValue(invoker.execute("ZADD", key,
                Double.toString(score), member));
    }

    @Override
    public long zadd(String key, Map<String, Double> members) {
        Args.requireKey(key);
        Args.requireNonEmpty(members, "Sorted set members");
        String[] args = new String[1 + members.size() * 2];
        args[0] = key;
        int index = 1;
        for (Map.Entry<String, Double> entry : members.entrySet()) {
            Double score = entry.getValue();
            validateScore(score);
            args[index++] = Double.toString(score);
            args[index++] = Args.requireLineSafe(entry.getKey(), "Sorted set member");
        }
        return ReplyParser.longValue(invoker.execute("ZADD", args));
    }

    @Override
    public long zrem(String key, String... members) {
        Args.requireKey(key);
        Args.requireLineSafeAll(members, "Sorted set member");
        return ReplyParser.longValue(invoker.execute("ZREM", prepend(key, members)));
    }

    @Override
    public Double zscore(String key, String member) {
        Args.requireKey(key);
        Args.requireLineSafe(member, "Sorted set member");
        return ReplyParser.doubleOrNull(invoker.execute("ZSCORE", key, member));
    }

    @Override
    public long zcard(String key) {
        Args.requireKey(key);
        return ReplyParser.longValue(invoker.execute("ZCARD", key));
    }

    @Override
    public List<String> zrange(String key, int start, int stop) {
        Args.requireKey(key);
        return ReplyParser.lines(invoker.execute("ZRANGE", key,
                Integer.toString(start), Integer.toString(stop)));
    }

    @Override
    public List<ZSetEntry> zrangeWithScores(String key, int start, int stop) {
        Args.requireKey(key);
        return ReplyParser.scoredEntries(invoker.execute("ZRANGE", key,
                Integer.toString(start), Integer.toString(stop), "WITHSCORES"));
    }

    @Override
    public Long zrank(String key, String member) {
        Args.requireKey(key);
        Args.requireLineSafe(member, "Sorted set member");
        return ReplyParser.longOrNull(invoker.execute("ZRANK", key, member));
    }

    /**
     * 校验分值不为空且是有限数。
     *
     * @param score 分值
     */
    private void validateScore(Double score) {
        if (score == null) {
            throw new IllegalArgumentException("Score must not be null");
        }
        if (score.isNaN() || score.isInfinite()) {
            throw new IllegalArgumentException("Score must be a finite number");
        }
    }

    /**
     * 把 key 放到参数数组首位。
     *
     * @param key key
     * @param members 成员
     * @return 新参数数组
     */
    private String[] prepend(String key, String[] members) {
        String[] args = new String[members.length + 1];
        args[0] = key;
        System.arraycopy(members, 0, args, 1, members.length);
        return args;
    }
}
