package com.ispengya.lightredis.command;

import java.util.List;

/**
 * 命令执行器共享的格式化与参数解析工具。
 */
final class CommandSupport {

    /**
     * 整数参数非法时返回的错误。
     */
    static final String ERR_INTEGER = "ERR value is not an integer or out of range";

    /**
     * 分值参数非法时返回的错误。
     */
    static final String ERR_FLOAT = "ERR value is not a valid float";

    /**
     * 过期时间参数非法时返回的错误。
     */
    static final String ERR_EXPIRE_TIME = "ERR invalid expire time";

    /**
     * 不存在的值在文本协议中的表示。
     */
    static final String NIL = "(nil)";

    private CommandSupport() {
    }

    /**
     * 将多个结果拼接成逐行返回的文本。
     *
     * @param values 结果列表
     * @return 以换行分隔的文本
     */
    static String lines(List<String> values) {
        return String.join("\n", values);
    }

    /**
     * 解析整数参数。
     *
     * @param value 参数文本
     * @return 解析结果，非法时返回 null
     */
    static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析长整数参数。
     *
     * @param value 参数文本
     * @return 解析结果，非法时返回 null
     */
    static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 把秒转换为毫秒，溢出时饱和到 long 边界。
     *
     * @param seconds 秒数
     * @return 毫秒数
     */
    static long secondsToMillis(long seconds) {
        return saturatingMultiply(seconds, 1000L);
    }

    /**
     * 乘法，溢出时饱和到 long 边界。
     *
     * @param value 被乘数
     * @param factor 乘数
     * @return 乘积
     */
    static long saturatingMultiply(long value, long factor) {
        if (value > Long.MAX_VALUE / factor) {
            return Long.MAX_VALUE;
        }
        if (value < Long.MIN_VALUE / factor) {
            return Long.MIN_VALUE;
        }
        return value * factor;
    }

    /**
     * 加法，溢出时饱和到 long 边界。
     *
     * @param left 左操作数
     * @param right 右操作数
     * @return 和
     */
    static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return right > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }

    /**
     * 解析有序集合分值。
     *
     * @param value 分值文本
     * @return 解析结果，非法或非有限数时返回 null
     */
    static Double parseScore(String value) {
        try {
            double score = Double.parseDouble(value);
            if (Double.isNaN(score) || Double.isInfinite(score)) {
                return null;
            }
            return score;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 格式化有序集合分值，整数不输出小数点。
     *
     * @param score 分值
     * @return 分值文本
     */
    static String formatScore(double score) {
        if (score == Math.rint(score) && Math.abs(score) < 1.0E15) {
            return Long.toString((long) score);
        }
        return Double.toString(score);
    }
}
