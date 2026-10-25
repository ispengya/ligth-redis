package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;
import com.ispengya.lightredis.store.ZSetValue;

import java.util.List;

/**
 * ZREM 命令执行器。
 *
 * <p>删除一个或多个成员，返回实际删除的数量；有序集合为空时删除 key。</p>
 */
public class ZremCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 2) {
            return "ERR wrong number of arguments for ZREM";
        }
        String key = args.get(0);
        ZSetValue zset = ValueSupport.zset(store, key, false);
        if (zset == null) {
            return "0";
        }
        int removed = 0;
        for (int i = 1; i < args.size(); i++) {
            if (zset.remove(args.get(i))) {
                removed++;
            }
        }
        store.removeIfEmpty(key, zset);
        return Integer.toString(removed);
    }
}
