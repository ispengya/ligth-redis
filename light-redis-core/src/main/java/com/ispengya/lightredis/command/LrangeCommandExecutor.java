package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LRANGE 命令执行器。
 *
 * <p>返回区间内的列表元素，区间包含 start 和 stop，结果逐行输出。</p>
 */
public class LrangeCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 3) {
            return "ERR wrong number of arguments for LRANGE";
        }
        Integer start = CommandSupport.parseInteger(args.get(1));
        Integer stop = CommandSupport.parseInteger(args.get(2));
        if (start == null || stop == null) {
            return CommandSupport.ERR_INTEGER;
        }
        ListValue list = ValueSupport.list(store, args.get(0), false);
        if (list == null) {
            return "";
        }
        return CommandSupport.lines(list.range(start, stop));
    }
}
