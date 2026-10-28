package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * PEXPIRE 命令执行器。
 *
 * <p>使用毫秒为单位设置相对过期时间，非正数会立即删除 key。</p>
 */
public class PexpireCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for PEXPIRE";
        }
        Long millis = CommandSupport.parseLong(args.get(1));
        if (millis == null) {
            return CommandSupport.ERR_EXPIRE_TIME;
        }
        return store.expire(args.get(0), millis) ? "1" : "0";
    }
}
