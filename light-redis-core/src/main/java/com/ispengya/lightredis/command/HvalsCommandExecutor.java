package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HVALS 命令执行器。
 *
 * <p>返回哈希表的所有字段值，逐行输出。</p>
 */
public class HvalsCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for HVALS";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), false);
        if (hash == null) {
            return "";
        }
        return CommandSupport.lines(hash.values());
    }
}
