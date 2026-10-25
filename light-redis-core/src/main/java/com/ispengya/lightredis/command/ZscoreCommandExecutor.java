package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;
import com.ispengya.lightredis.store.ZSetValue;

import java.util.List;

/**
 * ZSCORE 命令执行器。
 *
 * <p>返回有序集合中指定成员的分值。</p>
 */
public class ZscoreCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for ZSCORE";
        }
        ZSetValue zset = ValueSupport.zset(store, args.get(0), false);
        if (zset == null) {
            return CommandSupport.NIL;
        }
        Double score = zset.score(args.get(1));
        return score == null ? CommandSupport.NIL : CommandSupport.formatScore(score);
    }
}
