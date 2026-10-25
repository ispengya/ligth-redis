package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HSET 命令执行器。
 *
 * <p>设置一个或多个字段，返回新增字段的数量；已有字段只更新值。</p>
 */
public class HsetCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 3 || args.size() % 2 == 0) {
            return "ERR wrong number of arguments for HSET";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), true);
        int added = 0;
        for (int i = 1; i < args.size(); i += 2) {
            if (hash.put(args.get(i), args.get(i + 1))) {
                added++;
            }
        }
        return Integer.toString(added);
    }
}
