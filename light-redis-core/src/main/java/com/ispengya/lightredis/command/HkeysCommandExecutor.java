package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HKEYS 命令执行器。
 *
 * <p>返回哈希表的所有字段名，逐行输出。</p>
 */
public class HkeysCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for HKEYS";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), false);
        if (hash == null) {
            return "";
        }
        return CommandSupport.lines(hash.fieldNames());
    }
}
