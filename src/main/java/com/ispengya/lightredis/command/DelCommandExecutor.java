package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

public class DelCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.isEmpty()) {
            return "ERR wrong number of arguments for DEL";
        }
        long count = store.del(args);
        return Long.toString(count);
    }
}
