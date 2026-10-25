package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HGET 命令执行器。
 *
 * <p>返回哈希表中指定字段的值，字段或 key 不存在时返回 nil。</p>
 */
public class HgetCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for HGET";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), false);
        if (hash == null) {
            return CommandSupport.NIL;
        }
        String value = hash.get(args.get(1));
        return value == null ? CommandSupport.NIL : value;
    }
}
