package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LINDEX 命令执行器。
 *
 * <p>返回指定索引的元素，支持负数索引；索引越界时返回 nil。</p>
 */
public class LindexCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for LINDEX";
        }
        Integer index = CommandSupport.parseInteger(args.get(1));
        if (index == null) {
            return CommandSupport.ERR_INTEGER;
        }
        ListValue list = ValueSupport.list(store, args.get(0), false);
        if (list == null) {
            return CommandSupport.NIL;
        }
        String value = list.get(index);
        return value == null ? CommandSupport.NIL : value;
    }
}
