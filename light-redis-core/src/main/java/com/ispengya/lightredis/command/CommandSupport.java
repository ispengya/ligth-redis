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
