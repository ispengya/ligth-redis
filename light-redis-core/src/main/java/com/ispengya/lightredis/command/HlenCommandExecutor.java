package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ValueSupport;

import java.util.List;

/**
 * HLEN 命令执行器。
 *
 * <p>返回哈希表字段数量，key 不存在时返回 0。</p>
 */
public class HlenCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 1) {
            return "ERR wrong number of arguments for HLEN";
        }
        HashValue hash = ValueSupport.hash(store, args.get(0), false);
        return hash == null ? "0" : Integer.toString(hash.size());
    }
}
