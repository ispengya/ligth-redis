package com.ispengya.lightredis.command;

import com.ispengya.lightredis.persistence.CommandLog;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * BGREWRITEAOF 命令执行器。
 */
public class BgRewriteAofCommandExecutor implements CommandExecutor {

    /**
     * 命令日志。
     */
    private final CommandLog commandLog;

    /**
     * 创建 BGREWRITEAOF 执行器。
     *
     * @param commandLog 命令日志
     */
    public BgRewriteAofCommandExecutor(CommandLog commandLog) {
        if (commandLog == null) {
            throw new IllegalArgumentException("Command log must not be null");
        }
        this.commandLog = commandLog;
    }

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (!args.isEmpty()) {
            return "ERR wrong number of arguments for BGREWRITEAOF";
        }
        if (commandLog.isRewriteInProgress()) {
            return "ERR AOF rewrite already in progress";
        }
        if (commandLog.startRewrite()) {
            return "Background append only file rewriting started";
        }
        return "ERR persistence disabled";
    }
}
