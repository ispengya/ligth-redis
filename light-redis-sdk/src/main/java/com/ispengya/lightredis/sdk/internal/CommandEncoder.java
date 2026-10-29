package com.ispengya.lightredis.sdk.internal;

/**
 * 把命令名和参数编码为服务端文本协议。
 *
 * <p>所有参数都会加上双引号，并转义反斜杠和双引号。
 * 服务端解析器支持引号和反斜杠转义，因此参数中的空格、
 * 引号和反斜杠都可以安全往返。</p>
 */
public final class CommandEncoder {

    /**
     * 工具类不允许实例化。
     */
    private CommandEncoder() {
    }

    /**
     * 编码一条命令。
     *
     * @param command 命令名
     * @param args 命令参数
     * @return 完整命令文本
     */
    public static String encode(String command, String... args) {
        if (command == null || command.trim().isEmpty()) {
            throw new IllegalArgumentException("Command must not be empty");
        }
        for (int i = 0; i < command.length(); i++) {
            if (Character.isWhitespace(command.charAt(i))) {
                throw new IllegalArgumentException("Command must not contain whitespace");
            }
        }
        if (args == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }

        StringBuilder builder = new StringBuilder(command.length() + args.length * 8 + 8);
        builder.append(command);
        for (String arg : args) {
            builder.append(' ');
            appendQuoted(builder, arg);
        }
        return builder.toString();
    }

    /**
     * 追加一个带引号并已转义的参数。
     *
     * @param builder 命令缓冲区
     * @param value 参数值
     */
    private static void appendQuoted(StringBuilder builder, String value) {
        if (value == null) {
            throw new IllegalArgumentException("Command argument must not be null");
        }
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' || current == '"') {
                builder.append('\\');
            }
            builder.append(current);
        }
        builder.append('"');
    }
}
