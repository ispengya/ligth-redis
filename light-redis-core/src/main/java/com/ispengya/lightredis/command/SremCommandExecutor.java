package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.SetValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * SREM 命令执行器。
 *
 * <p>删除一个或多个成员，返回实际删除的数量；集合为空时删除 key。</p>
 */
public class SremCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 2) {
            return "ERR wrong number of arguments for SREM";
        }
        String key = args.get(0);
        SetValue set = ValueSupport.set(store, key, false);
        if (set == null) {
            return "0";
        }
        int removed = 0;
        for (int i = 1; i < args.size(); i++) {
            if (set.remove(args.get(i))) {
                removed++;
            }
        }
        store.removeIfEmpty(key, set);
        return Integer.toString(removed);
    }
}
