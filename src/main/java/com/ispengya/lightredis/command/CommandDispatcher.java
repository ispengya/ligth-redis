package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.HashMap;
import java.util.Map;

public class CommandDispatcher {

    private final Map<String, CommandExecutor> executors = new HashMap<>();
    private final KeyValueStore store;

    public CommandDispatcher(KeyValueStore store) {
        if (store == null) {
            throw new IllegalArgumentException("Store must not be null");
        }
        this.store = store;
    }

    public void register(String name, CommandExecutor executor) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Command name must not be empty");
        }
        if (executor == null) {
            throw new IllegalArgumentException("Executor must not be null");
        }
        executors.put(name.toUpperCase(), executor);
    }

    public String dispatch(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("Command must not be null");
        }
        CommandExecutor executor = executors.get(command.getName());
        if (executor == null) {
            return "ERR unknown command " + command.getName();
        }
        return executor.execute(command, store);
    }
}
