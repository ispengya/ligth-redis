package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.ServerCommands;

import java.util.Map;

/**
 * 服务端管理命令实现。
 */
public final class ServerCommandsImpl implements ServerCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     */
    public ServerCommandsImpl(CommandInvoker invoker) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        this.invoker = invoker;
    }

    @Override
    public String ping() {
        return invoker.execute("PING");
    }

    @Override
    public Map<String, String> info() {
        return ReplyParser.keyValues(invoker.execute("INFO"));
    }

    @Override
    public String bgRewriteAof() {
        return invoker.execute("BGREWRITEAOF");
    }
}
