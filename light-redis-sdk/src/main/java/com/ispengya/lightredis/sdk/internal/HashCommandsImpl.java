package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.HashCommands;

import java.util.List;
import java.util.Map;

/**
 * Hash 命令实现。
 */
public final class HashCommandsImpl implements HashCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public HashCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public long hset(String key, String field, String value) {
        Args.requireKey(key);
        Args.requireLineSafe(field, "Hash field");
        Args.requireLineSafe(value, "Hash value");
        return ReplyParser.longValue(invoker.execute("HSET", key, field, value));
    }

    @Override
    public long hset(String key, Map<String, String> fields) {
        Args.requireKey(key);
        Args.requireNonEmpty(fields, "Hash fields");
        String[] args = new String[1 + fields.size() * 2];
        args[0] = key;
        int index = 1;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            args[index++] = Args.requireLineSafe(entry.getKey(), "Hash field");
            args[index++] = Args.requireLineSafe(entry.getValue(), "Hash value");
        }
        return ReplyParser.longValue(invoker.execute("HSET", args));
    }

    @Override
    public String hget(String key, String field) {
        Args.requireKey(key);
        Args.requireLineSafe(field, "Hash field");
        return ReplyParser.string(invoker.execute("HGET", key, field));
    }

    @Override
    public long hdel(String key, String... fields) {
        Args.requireKey(key);
        Args.requireLineSafeAll(fields, "Hash field");
        return ReplyParser.longValue(invoker.execute("HDEL", prepend(key, fields)));
    }

    @Override
    public boolean hexists(String key, String field) {
        Args.requireKey(key);
        Args.requireLineSafe(field, "Hash field");
        return ReplyParser.booleanValue(invoker.execute("HEXISTS", key, field));
    }

    @Override
    public long hlen(String key) {
        Args.requireKey(key);
        return ReplyParser.longValue(invoker.execute("HLEN", key));
    }

    @Override
    public Map<String, String> hgetall(String key) {
        Args.requireKey(key);
        return ReplyParser.pairs(invoker.execute("HGETALL", key));
    }

    @Override
    public List<String> hkeys(String key) {
        Args.requireKey(key);
        return ReplyParser.lines(invoker.execute("HKEYS", key));
    }

    @Override
    public List<String> hvals(String key) {
        Args.requireKey(key);
        return ReplyParser.lines(invoker.execute("HVALS", key));
    }

    /**
     * 把 key 放到参数数组首位。
     *
     * @param key key
     * @param fields 字段
     * @return 新参数数组
     */
    private String[] prepend(String key, String[] fields) {
        String[] args = new String[fields.length + 1];
        args[0] = key;
        System.arraycopy(fields, 0, args, 1, fields.length);
        return args;
    }
}
