package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;
import com.ispengya.lightredis.store.ZSetValue;

import java.util.ArrayList;
import java.util.List;

/**
 * ZRANGE 命令执行器。
 *
 * <p>按分值升序返回区间内的成员；可选的 WITHSCORES 会在每行末尾追加分值。</p>
 */
public class ZrangeCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 3 && args.size() != 4) {
            return "ERR wrong number of arguments for ZRANGE";
        }
        boolean withScores = false;
        if (args.size() == 4) {
            if (!"WITHSCORES".equalsIgnoreCase(args.get(3))) {
                return "ERR syntax error";
            }
            withScores = true;
        }

        Integer start = CommandSupport.parseInteger(args.get(1));
        Integer stop = CommandSupport.parseInteger(args.get(2));
        if (start == null || stop == null) {
            return CommandSupport.ERR_INTEGER;
        }

        ZSetValue zset = ValueSupport.zset(store, args.get(0), false);
        if (zset == null) {
            return "";
        }

        List<String> result = new ArrayList<>();
        for (ZSetValue.ScoredMember member : zset.range(start, stop)) {
            if (withScores) {
                result.add(member.getMember() + " " + CommandSupport.formatScore(member.getScore()));
            } else {
                result.add(member.getMember());
            }
        }
        return CommandSupport.lines(result);
    }
}
