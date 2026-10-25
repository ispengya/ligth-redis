package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LPUSH 命令执行器。
 *
 * <p>将一个或多个元素插入列表表头，返回插入后的列表长度。</p>
 */
public class LpushCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() < 2) {
            return "ERR wrong number of arguments for LPUSH";
        }
        ListValue list = ValueSupport.list(store, args.get(0), true);
        return Integer.toString(list.pushLeft(args.subList(1, args.size())));
    }
}
