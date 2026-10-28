package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

import java.util.List;

/**
 * PEXPIREAT 命令执行器。
 *
 * <p>使用 epoch 毫秒时间戳设置绝对过期时间，也是 AOF 中保存 TTL 的命令形式。</p>
 */
public class PexpireatCommandExecutor implements CommandExecutor {

    @Override
    public String execute(Command command, KeyValueStore store) {
        List<String> args = command.getArgs();
        if (args.size() != 2) {
            return "ERR wrong number of arguments for PEXPIREAT";
        }
        Long millis = CommandSupport.parseLong(args.get(1));
        if (millis == null) {
            return CommandSupport.ERR_EXPIRE_TIME;
        }
        return store.expireAt(args.get(0), millis) ? "1" : "0";
    }
}
