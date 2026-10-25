package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;
import com.ispengya.lightredis.store.ZSetValue;

import java.util.List;

/**
 * ZRANK 命令执行器。
 *
 * <p>返回成员按分值升序排列的名次，从 0 开始。</p>
 */
public class ZrankCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for ZRANK";
        }
        ZSetValue zset = ValueSupport.zset(store, args.get(0), false);
        if (zset == null) {
            return CommandSupport.NIL;
        }
        int rank = zset.rank(args.get(1));
        return rank < 0 ? CommandSupport.NIL : Integer.toString(rank);
    }
}
