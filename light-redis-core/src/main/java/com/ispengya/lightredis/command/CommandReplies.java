package com.ispengya.lightredis.command;

/**
 * 命令回复判断工具。
 */
public final class CommandReplies {

    /**
     * 文本协议中表示错误的回复前缀。
     */
    private static final String[] ERROR_PREFIXES = {
            "ERR ", "WRONGTYPE ", "NOAUTH ", "READONLY "
    };

    private CommandReplies() {
    }

    /**
     * 判断命令回复是否表示错误。
     *
     * @param reply 命令回复
     * @return 错误回复或空回复返回 true
     */
    public static boolean isError(String reply) {
        if (reply == null) {
            return true;
        }
        for (String prefix : ERROR_PREFIXES) {
            if (reply.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
