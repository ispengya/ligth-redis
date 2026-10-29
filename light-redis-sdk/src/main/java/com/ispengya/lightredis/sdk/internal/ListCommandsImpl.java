package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.ListCommands;

import java.util.List;

/**
 * List 命令实现。
 */
public final class ListCommandsImpl implements ListCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public ListCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public long lpush(String key, String... values) {
        Args.requireKey(key);
        Args.requireLineSafeAll(values, "List value");
        return ReplyParser.longValue(invoker.execute("LPUSH", prepend(key, values)));
    }

    @Override
    public long rpush(String key, String... values) {
        Args.requireKey(key);
        Args.requireLineSafeAll(values, "List value");
        return ReplyParser.longValue(invoker.execute("RPUSH", prepend(key, values)));
    }

    @Override
    public String lpop(String key) {
        Args.requireKey(key);
        return ReplyParser.string(invoker.execute("LPOP", key));
    }

    @Override
    public String rpop(String key) {
        Args.requireKey(key);
        return ReplyParser.string(invoker.execute("RPOP", key));
    }

    @Override
    public long llen(String key) {
        Args.requireKey(key);
        return ReplyParser.longValue(invoker.execute("LLEN", key));
    }

    @Override
    public List<String> lrange(String key, int start, int stop) {
        Args.requireKey(key);
        return ReplyParser.lines(invoker.execute("LRANGE", key,
                Integer.toString(start), Integer.toString(stop)));
    }

    @Override
    public String lindex(String key, int index) {
        Args.requireKey(key);
        return ReplyParser.string(invoker.execute("LINDEX", key, Integer.toString(index)));
    }

    @Override
    public void lset(String key, int index, String value) {
        Args.requireKey(key);
        Args.requireLineSafe(value, "List value");
        invoker.execute("LSET", key, Integer.toString(index), value);
    }

    @Override
    public long lrem(String key, int count, String value) {
        Args.requireKey(key);
        Args.requireLineSafe(value, "List value");
        return ReplyParser.longValue(invoker.execute("LREM", key,
                Integer.toString(count), value));
    }

    /**
     * 把 key 放到参数数组首位。
     *
     * @param key key
     * @param values 元素
     * @return 新参数数组
     */
    private String[] prepend(String key, String[] values) {
        String[] args = new String[values.length + 1];
        args[0] = key;
        System.arraycopy(values, 0, args, 1, values.length);
        return args;
    }
}
