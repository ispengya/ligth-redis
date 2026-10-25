package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * TYPE 命令执行器。
 *
 * <p>返回 key 的数据类型，key 不存在时返回 none。</p>
 */
public class TypeCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for TYPE";
        }
        return store.type(args.get(0));
    }
}
