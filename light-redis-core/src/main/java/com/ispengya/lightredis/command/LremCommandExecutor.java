package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LREM 命令执行器。
 *
 * <p>删除列表中的指定元素，count 控制删除方向和数量。</p>
 */
public class LremCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 3) {
            return "ERR wrong number of arguments for LREM";
        }
        Integer count = CommandSupport.parseInteger(args.get(1));
        if (count == null) {
            return CommandSupport.ERR_INTEGER;
        }
        String key = args.get(0);
        ListValue list = ValueSupport.list(store, key, false);
        if (list == null) {
            return "0";
        }
        int removed = list.remove(args.get(2), count);
        store.removeIfEmpty(key, list);
        return Integer.toString(removed);
    }
}
