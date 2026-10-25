package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.SetValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * SMEMBERS 命令执行器。
 *
 * <p>返回集合中的所有成员，逐行输出。</p>
 */
public class SmembersCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for SMEMBERS";
        }
        SetValue set = ValueSupport.set(store, args.get(0), false);
        if (set == null) {
            return "";
        }
        return CommandSupport.lines(set.members());
    }
}
