package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * TTL 命令执行器。
 *
 * <p>返回剩余秒数，按四舍五入处理；-2 表示 key 不存在，-1 表示没有过期时间。</p>
 */
public class TtlCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for TTL";
        }
        long ttl = store.pttl(args.get(0));
        if (ttl < 0) {
            return Long.toString(ttl);
        }
        return Long.toString((ttl + 500L) / 1000L);
    }
}
