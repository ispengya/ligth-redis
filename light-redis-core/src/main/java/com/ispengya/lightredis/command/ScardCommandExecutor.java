package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.SetValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * SCARD 命令执行器。
 *
 * <p>返回集合成员数量，key 不存在时返回 0。</p>
 */
public class ScardCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for SCARD";
        }
        SetValue set = ValueSupport.set(store, args.get(0), false);
        return set == null ? "0" : Integer.toString(set.size());
    }
}
