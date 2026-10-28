package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * EXPIRE 命令执行器。
 *
 * <p>使用秒为单位设置相对过期时间，非正数会立即删除 key。</p>
 */
public class ExpireCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for EXPIRE";
        }
        Long seconds = CommandSupport.parseLong(args.get(1));
        if (seconds == null) {
            return CommandSupport.ERR_EXPIRE_TIME;
        }
        return store.expire(args.get(0), CommandSupport.secondsToMillis(seconds)) ? "1" : "0";
    }
}
