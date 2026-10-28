package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * EXPIREAT 命令执行器。
 *
 * <p>使用 Unix 秒时间戳设置绝对过期时间。</p>
 */
public class ExpireatCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for EXPIREAT";
        }
        Long seconds = CommandSupport.parseLong(args.get(1));
        if (seconds == null) {
            return CommandSupport.ERR_EXPIRE_TIME;
        }
        return store.expireAt(args.get(0), CommandSupport.secondsToMillis(seconds)) ? "1" : "0";
    }
}
