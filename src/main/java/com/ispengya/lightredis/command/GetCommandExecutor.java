package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

public class GetCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for GET";
        }
        String key = args.get(0);
        String value = store.get(key);
        if (value == null) {
            return "(nil)";
        }
        return value;
    }
}
