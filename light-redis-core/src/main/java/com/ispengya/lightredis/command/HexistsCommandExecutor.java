package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HEXISTS 命令执行器。
 *
 * <p>判断哈希表中是否存在指定字段。</p>
 */
public class HexistsCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for HEXISTS";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), false);
        return hash != null && hash.contains(args.get(1)) ? "1" : "0";
    }
}
