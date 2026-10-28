package com.ispengya.lightredis.command;

import com.ispengya.lightredis.persistence.CommandLog;
import com.ispengya.lightredis.persistence.NoopCommandLog;
import com.ispengya.lightredis.protocol.Command;

/**
 * 命令执行入口。
 *
 * <p>写命令先追加 AOF，追加成功后再修改内存；日志写入失败时命令不会执行。
 * 相对过期时间命令会在追加前归一化为 PEXPIREAT，保证 AOF 回放可确定。</p>
 */
public final class CommandService {

    /**
     * 命令分发器。
     */
    private final CommandDispatcher dispatcher;

    /**
     * 写命令日志。
     */
    private final CommandLog commandLog;

    /**
     * 创建命令服务。
     *
     * @param dispatcher 命令分发器
     * @param commandLog 写命令日志，null 表示关闭持久化
     */
    public CommandService(CommandDispatcher dispatcher, CommandLog commandLog) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher must not be null");
        }
        this.dispatcher = dispatcher;
        this.commandLog = commandLog == null ? NoopCommandLog.INSTANCE : commandLog;
    }

    /**
     * 执行一条命令。
     *
     * @param command 命令
     * @return 命令回复
     * @throws com.ispengya.lightredis.persistence.PersistenceException 写命令落盘失败
     */
    public String execute(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        Command effective = ExpireCommandNormalizer.normalize(
                command, System.currentTimeMillis());
        if (dispatcher.isWrite(effective.getName())) {
            commandLog.append(effective);
        }
        return dispatcher.dispatch(effective);
    }
}
