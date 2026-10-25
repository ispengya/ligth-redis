package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * LLEN 命令执行器。
 *
 * <p>返回列表长度，key 不存在时返回 0。</p>
 */
public class LlenCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for LLEN";
        }
        ListValue list = ValueSupport.list(store, args.get(0), false);
        return list == null ? "0" : Integer.toString(list.size());
    }
}
