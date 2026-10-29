package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.KeyCommands;

import java.util.List;

/**
 * 通用 key 与过期时间命令实现。
 */
public final class KeyCommandsImpl implements KeyCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public KeyCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public long del(String... keys) {
        Args.requireLineSafeAll(keys, "Key");
        return ReplyParser.longValue(invoker.execute("DEL", keys));
    }

    @Override
    public boolean exists(String key) {
        Args.requireKey(key);
        return ReplyParser.booleanValue(invoker.execute("EXISTS", key));
    }

    @Override
    public String type(String key) {
        Args.requireKey(key);
        return invoker.execute("TYPE", key);
    }

    @Override
    public List<String> keys(String pattern) {
        Args.requireText(pattern, "Pattern");
        return ReplyParser.lines(invoker.execute("KEYS", pattern));
    }

    @Override
    public long dbsize() {
        return ReplyParser.longValue(invoker.execute("DBSIZE"));
    }

    @Override
    public boolean expire(String key, long seconds) {
        Args.requireKey(key);
        return ReplyParser.booleanValue(invoker.execute("EXPIRE", key, Long.toString(seconds)));
    }

    @Override
    public boolean pexpire(String key, long millis) {
        Args.requireKey(key);
        return ReplyParser.booleanValue(invoker.execute("PEXPIRE", key, Long.toString(millis)));
    }

    @Override
    public boolean expireAt(String key, long epochSeconds) {
        Args.requireKey(key);
        return ReplyParser.booleanValue(invoker.execute("EXPIREAT", key, Long.toString(epochSeconds)));
    }

    @Override
    public boolean pexpireAt(String key, long epochMillis) {
        Args.requireKey(key);
        return ReplyParser.booleanValue(invoker.execute("PEXPIREAT", key, Long.toString(epochMillis)));
    }

    @Override
    public long ttl(String key) {
        Args.requireKey(key);
        return ReplyParser.longValue(invoker.execute("TTL", key));
    }

    @Override
    public long pttl(String key) {
        Args.requireKey(key);
        return ReplyParser.longValue(invoker.execute("PTTL", key));
    }

    @Override
    public boolean persist(String key) {
        Args.requireKey(key);
        return ReplyParser.booleanValue(invoker.execute("PERSIST", key));
    }
}
