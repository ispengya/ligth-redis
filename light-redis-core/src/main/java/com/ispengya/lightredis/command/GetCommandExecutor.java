package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.StringValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

public class GetCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for GET";
        }
        StringValue value = ValueSupport.string(store, args.get(0));
        if (value == null) {
            return "(nil)";
        }
        return value.getValue();
    }
}
