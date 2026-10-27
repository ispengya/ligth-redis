package com.ispengya.lightredis.persistence;

import com.ispengya.lightredis.protocol.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * AOF 中保存的一条写命令。
 *
 * <p>命令名和参数分开保存，避免恢复时重新解析带引号的原始命令行。</p>
 */
public final class AofRecord {

    /**
     * 命令名称，统一使用大写。
     */
    private final String commandName;

    /**
     * 命令参数。
     */
    private final List<String> args;

    /**
     * 创建一条 AOF 记录。
     *
     * @param commandName 命令名称
     * @param args 命令参数
     */
    public AofRecord(String commandName, List<String> args) {
        if (commandName == null || commandName.trim().isEmpty()) {
            throw new IllegalArgumentException("Command name must not be empty");
        }
        if (args == null) {
            throw new IllegalArgumentException("Command args must not be null");
        }
        this.commandName = commandName.toUpperCase(Locale.ROOT);
        List<String> copy = new ArrayList<>(args.size());
        for (String arg : args) {
            if (arg == null) {
                throw new IllegalArgumentException("Command arg must not be null");
            }
            copy.add(arg);
        }
        this.args = Collections.unmodifiableList(copy);
    }

    /**
     * 从已解析的命令创建 AOF 记录。
     *
     * @param command 已解析命令
     * @return AOF 记录
     */
    public static AofRecord from(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        return new AofRecord(command.getName(), command.getArgs());
    }

    /**
     * 转换为命令对象，用于启动恢复。
     *
     * @return 命令对象
     */
    public Command toCommand() {
        return new Command(commandName, args);
    }

    /**
     * 返回命令名称。
     *
     * @return 命令名称
     */
    public String getCommandName() {
        return commandName;
    }

    /**
     * 返回命令参数。
     *
     * @return 不可变参数列表
     */
    public List<String> getArgs() {
        return args;
    }
}
