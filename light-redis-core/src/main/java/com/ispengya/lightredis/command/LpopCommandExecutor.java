package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LPOP 命令执行器。
 *
 * <p>弹出列表表头元素；列表为空时删除 key 并返回 nil。</p>
 */
public class LpopCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for LPOP";
        }
        String key = args.get(0);
        ListValue list = ValueSupport.list(store, key, false);
        if (list == null) {
            return CommandSupport.NIL;
        }
        String value = list.popLeft();
        store.removeIfEmpty(key, list);
        return value == null ? CommandSupport.NIL : value;
    }
}
