package com.ispengya.lightredis.sdk.command;

/**
 * 原始命令出口。
 *
 * <p>用于调试、兼容尚未类型化封装的能力。服务端错误同样会抛出
 * {@code RedisCommandException}。</p>
 */
public interface RawCommands {

    /**
     * 原样发送一条命令文本。
     *
     * <p>调用方自行处理引号和转义，例如 {@code SET "a b" "c d"}。</p>
     *
     * @param command 完整命令文本
     * @return 服务端原始回复
     */
    String execute(String command);

    /**
     * 按命令名和参数发送命令，参数会自动加引号并转义。
     *
     * @param command 命令名，例如 SET
     * @param args 命令参数
     * @return 服务端原始回复
     */
    String execute(String command, String... args);
}
