package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.SetCommands;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Set 命令实现。
 */
public final class SetCommandsImpl implements SetCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public SetCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public long sadd(String key, String... members) {
        Args.requireKey(key);
        Args.requireLineSafeAll(members, "Set member");
        return ReplyParser.longValue(invoker.execute("SADD", prepend(key, members)));
    }

    @Override
    public long srem(String key, String... members) {
        Args.requireKey(key);
        Args.requireLineSafeAll(members, "Set member");
        return ReplyParser.longValue(invoker.execute("SREM", prepend(key, members)));
    }

    @Override
    public Set<String> smembers(String key) {
        Args.requireKey(key);
        List<String> members = ReplyParser.lines(invoker.execute("SMEMBERS", key));
        return new LinkedHashSet<>(members);
    }

    @Override
    public boolean sismember(String key, String member) {
        Args.requireKey(key);
        Args.requireLineSafe(member, "Set member");
        return ReplyParser.booleanValue(invoker.execute("SISMEMBER", key, member));
    }

    @Override
    public long scard(String key) {
        Args.requireKey(key);
        return ReplyParser.longValue(invoker.execute("SCARD", key));
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
