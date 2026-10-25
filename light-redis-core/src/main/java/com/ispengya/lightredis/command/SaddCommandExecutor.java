package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.SetValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * SADD 命令执行器。
 *
 * <p>添加一个或多个成员，返回实际新增的数量。</p>
 */
public class SaddCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 2) {
            return "ERR wrong number of arguments for SADD";
        }
        SetValue set = ValueSupport.set(store, args.get(0), true);
        int added = 0;
        for (int i = 1; i < args.size(); i++) {
            if (set.add(args.get(i))) {
                added++;
            }
        }
        return Integer.toString(added);
    }
}
