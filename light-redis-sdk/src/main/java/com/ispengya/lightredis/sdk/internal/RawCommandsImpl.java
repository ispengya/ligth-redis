package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.RawCommands;

/**
 * 原始命令出口实现。
 */
public final class RawCommandsImpl implements RawCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public RawCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public String execute(String command) {
        Args.requireText(command, "Command");
        return invoker.executeRaw(command);
    }

    @Override
    public String execute(String command, String... args) {
        Args.requireText(command, "Command");
        return invoker.execute(command, args);
    }
}
