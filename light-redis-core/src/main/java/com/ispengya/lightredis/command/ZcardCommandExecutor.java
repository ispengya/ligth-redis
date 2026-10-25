package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;
import com.ispengya.lightredis.store.ZSetValue;

import java.util.List;

/**
 * ZCARD 命令执行器。
 *
 * <p>返回有序集合成员数量，key 不存在时返回 0。</p>
 */
public class ZcardCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for ZCARD";
        }
        ZSetValue zset = ValueSupport.zset(store, args.get(0), false);
        return zset == null ? "0" : Integer.toString(zset.size());
    }
}
