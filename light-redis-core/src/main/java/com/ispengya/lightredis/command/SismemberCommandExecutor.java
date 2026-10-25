package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.SetValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * SISMEMBER 命令执行器。
 *
 * <p>判断成员是否存在于集合中。</p>
 */
public class SismemberCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for SISMEMBER";
        }
        SetValue set = ValueSupport.set(store, args.get(0), false);
        return set != null && set.contains(args.get(1)) ? "1" : "0";
    }
}
