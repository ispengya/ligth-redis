package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LSET 命令执行器。
 *
 * <p>修改指定索引的元素，支持负数索引。</p>
 */
public class LsetCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 3) {
            return "ERR wrong number of arguments for LSET";
        }
        Integer index = CommandSupport.parseInteger(args.get(1));
        if (index == null) {
            return CommandSupport.ERR_INTEGER;
        }
        ListValue list = ValueSupport.list(store, args.get(0), false);
        if (list == null) {
            return "ERR no such key";
        }
        if (!list.set(index, args.get(2))) {
            return "ERR index out of range";
        }
        return "OK";
    }
}
