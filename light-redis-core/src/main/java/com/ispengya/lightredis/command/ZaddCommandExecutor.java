package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;
import com.ispengya.lightredis.store.ZSetValue;

import java.util.ArrayList;
import java.util.List;

/**
 * ZADD 命令执行器。
 *
 * <p>添加或更新一个或多个成员的分值，返回新增成员的数量。</p>
 */
public class ZaddCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 3 || (args.size() - 1) % 2 != 0) {
            return "ERR wrong number of arguments for ZADD";
        }

        List<String> members = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (int i = 1; i < args.size(); i += 2) {
            Double score = CommandSupport.parseScore(args.get(i));
            if (score == null) {
                return CommandSupport.ERR_FLOAT;
            }
            scores.add(score);
            members.add(args.get(i + 1));
        }

        ZSetValue zset = ValueSupport.zset(store, args.get(0), true);
        int added = 0;
        for (int i = 0; i < members.size(); i++) {
            if (zset.add(members.get(i), scores.get(i))) {
                added++;
            }
        }
        return Integer.toString(added);
    }
}
