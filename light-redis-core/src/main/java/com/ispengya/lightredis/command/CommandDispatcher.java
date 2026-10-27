package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.WrongTypeException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandDispatcher {

    private final Map<String, CommandExecutor> executors = new HashMap<>();
    private final Set<String> writeCommands = new HashSet<>();
    private final KeyValueStore store;

    public CommandDispatcher(KeyValueStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Store must not be null");
        }
        this.store = store;
    }

    public void register(String name, CommandExecutor executor) {
        register(name, executor, false);
    }

    /**
     * 注册命令，并标记是否为写命令。
     *
     * @param name 命令名称
     * @param executor 命令执行器
     * @param write 是否会修改数据
     */
    public void register(String name, CommandExecutor executor, boolean write) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Command name must not be empty");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Executor must not be null");
        }
        String normalized = name.toUpperCase(Locale.ROOT);
        executors.put(normalized, executor);
        if (write) {
            writeCommands.add(normalized);
        } else {
            writeCommands.remove(normalized);
        }
    }

    /**
     * 判断命令是否为写命令。
     *
     * @param name 命令名称
     * @return 写命令返回 true
     */
    public boolean isWrite(String name) {
        return name != null && writeCommands.contains(name.toUpperCase(Locale.ROOT));
    }

    public String dispatch(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        CommandExecutor executor = executors.get(command.getName());
        if (executor == null) {
            return "ERR unknown command " + command.getName();
        }
        try {
            return executor.execute(command, store);
        } catch (WrongTypeException e) {
            return e.getMessage();
        }
    }
}
