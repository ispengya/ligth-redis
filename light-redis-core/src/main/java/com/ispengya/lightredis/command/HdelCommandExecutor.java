package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HDEL 命令执行器。
 *
 * <p>删除一个或多个字段，返回实际删除的数量；哈希表为空时删除 key。</p>
 */
public class HdelCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 2) {
            return "ERR wrong number of arguments for HDEL";
        }
        String key = args.get(0);
        HashValue hash = ValueSupport.hash(store, key, false);
        if (hash == null) {
            return "0";
        }
        int removed = 0;
        for (int i = 1; i < args.size(); i++) {
            if (hash.remove(args.get(i))) {
                removed++;
            }
        }
        store.removeIfEmpty(key, hash);
        return Integer.toString(removed);
    }
}
