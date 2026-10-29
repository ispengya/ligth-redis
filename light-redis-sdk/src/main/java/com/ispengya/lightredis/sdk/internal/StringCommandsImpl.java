package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.StringCommands;

/**
 * String 命令实现。
 */
public final class StringCommandsImpl implements StringCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public StringCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public void set(String key, String value) {
        Args.requireKey(key);
        Args.requireText(value, "Value");
        invoker.execute("SET", key, value);
    }

    @Override
    public String get(String key) {
        Args.requireKey(key);
        return ReplyParser.string(invoker.execute("GET", key));
    }
}
