package com.ispengya.lightredis.command;

import com.ispengya.lightredis.protocol.Command;

import java.util.Arrays;
import java.util.List;

/**
 * 把相对过期时间命令归一化为绝对时间的 PEXPIREAT。
 *
 * <p>EXPIRE、PEXPIRE、EXPIREAT 在写入 AOF 前统一转换为 PEXPIREAT，
 * 避免重启后从新的时间点重新计算 TTL。参数非法时保留原命令，
 * 由执行器返回错误，回放时会被跳过。</p>
 */
final class ExpireCommandNormalizer {

    private ExpireCommandNormalizer() {
    }

    /**
     * 归一化过期命令。
     *
     * @param command 原始命令
     * @param nowMillis 当前 epoch 毫秒
     * @return PEXPIREAT 命令；不需要或无法归一化时返回原命令
     */
    static Command normalize(Command command, long nowMillis) {
        if (command == null) {
            return null;
        }
        List<String> args = command.getArgs();
        String name = command.getName();
        if (args == null || args.size() != 2 || name == null) {
            return command;
        }
        Long value = CommandSupport.parseLong(args.get(1));
        if (value == null) {
            return command;
        }

        long absolute;
        if ("EXPIRE".equalsIgnoreCase(name)) {
            absolute = CommandSupport.saturatingAdd(
                    nowMillis, CommandSupport.secondsToMillis(value));
        } else if ("PEXPIRE".equalsIgnoreCase(name)) {
            absolute = CommandSupport.saturatingAdd(nowMillis, value);
        } else if ("EXPIREAT".equalsIgnoreCase(name)) {
            absolute = CommandSupport.secondsToMillis(value);
        } else {
            return command;
        }
        return new Command("PEXPIREAT",
                Arrays.asList(args.get(0), Long.toString(absolute)));
    }
}
