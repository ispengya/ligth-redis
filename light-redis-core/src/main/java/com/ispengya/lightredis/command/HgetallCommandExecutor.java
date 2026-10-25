package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HGETALL 命令执行器。
 *
 * <p>返回全部字段和值，按字段、值交替逐行输出。</p>
 */
public class HgetallCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for HGETALL";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), false);
        if (hash == null) {
            return "";
        }
        List<String> result = new ArrayList<>(hash.size() * 2);
        for (Map.Entry<String, String> entry : hash.entries()) {
            result.add(entry.getKey());
            result.add(entry.getValue());
        }
        return CommandSupport.lines(result);
    }
}
