package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * DBSIZE 命令执行器。
 *
 * <p>返回当前存储中的 key 总数。</p>
 */
public class DbsizeCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (!args.isEmpty()) {
            return "ERR wrong number of arguments for DBSIZE";
        }
        return Long.toString(store.size());
    }
}
