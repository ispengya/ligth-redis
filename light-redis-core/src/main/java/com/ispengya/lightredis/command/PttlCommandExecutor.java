package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * PTTL 命令执行器。
 *
 * <p>返回剩余毫秒数，-2 表示 key 不存在，-1 表示没有过期时间。</p>
 */
public class PttlCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for PTTL";
        }
        return Long.toString(store.pttl(args.get(0)));
    }
}
