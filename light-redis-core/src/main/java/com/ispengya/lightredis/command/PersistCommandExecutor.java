package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * PERSIST 命令执行器。
 *
 * <p>移除 key 的过期时间，成功返回 1。</p>
 */
public class PersistCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for PERSIST";
        }
        return store.persist(args.get(0)) ? "1" : "0";
    }
}
