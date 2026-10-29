package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.LightRedisProtocolException;
import com.ispengya.lightredis.sdk.RedisCommandException;
import com.ispengya.lightredis.sdk.command.ZSetEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务端文本回复解析工具。
 *
 * <p>服务端以文本表示所有结果：错误带固定前缀，空值使用
 * {@code (nil)}，集合使用换行分隔。这里集中处理这些约定。</p>
 */
public final class ReplyParser {

    /**
     * 空值在文本协议中的表示。
     */
    private static final String NIL = "(nil)";

    /**
     * 服务端错误回复前缀。
     */
    private static final String[] ERROR_PREFIXES = {
            "ERR ", "WRONGTYPE ", "NOAUTH ", "READONLY "
    };

    /**
     * 工具类不允许实例化。
     */
    private ReplyParser() {
    }

    /**
     * 如果回复是错误则抛出异常。
     *
     * @param reply 服务端回复
     */
    public static void checkError(String reply) {
        if (reply == null) {
            throw new LightRedisProtocolException("Server returned a null reply");
        }
        for (String prefix : ERROR_PREFIXES) {
            if (reply.startsWith(prefix)) {
                throw new RedisCommandException(reply);
            }
        }
    }

    /**
     * 判断回复是否为空值。
     *
     * @param reply 服务端回复
     * @return 空值返回 true
     */
    public static boolean isNil(String reply) {
        return NIL.equals(reply);
    }

    /**
     * 解析字符串回复，空值返回 null。
     *
     * @param reply 服务端回复
     * @return 字符串或 null
     */
    public static String string(String reply) {
        return isNil(reply) ? null : reply;
    }

    /**
     * 解析长整数回复。
     *
     * @param reply 服务端回复
     * @return 长整数
     */
    public static long longValue(String reply) {
        try {
            return Long.parseLong(reply);
        } catch (NumberFormatException e) {
            throw protocolError("Expected an integer but was: " + reply);
        }
    }

    /**
     * 解析整数回复。
     *
     * @param reply 服务端回复
     * @return 整数
     */
    public static int intValue(String reply) {
        try {
            return Integer.parseInt(reply);
        } catch (NumberFormatException e) {
            throw protocolError("Expected an integer but was: " + reply);
        }
    }

    /**
     * 解析可空长整数回复。
     *
     * @param reply 服务端回复
     * @return 长整数或 null
     */
    public static Long longOrNull(String reply) {
        return isNil(reply) ? null : longValue(reply);
    }

    /**
     * 解析浮点数回复。
     *
     * @param reply 服务端回复
     * @return 浮点数
     */
    public static double doubleValue(String reply) {
        try {
            return Double.parseDouble(reply);
        } catch (NumberFormatException e) {
            throw protocolError("Expected a number but was: " + reply);
        }
    }

    /**
     * 解析可空浮点数回复。
     *
     * @param reply 服务端回复
     * @return 浮点数或 null
     */
    public static Double doubleOrNull(String reply) {
        return isNil(reply) ? null : doubleValue(reply);
    }

    /**
     * 解析 1/0 布尔回复。
     *
     * @param reply 服务端回复
     * @return 1 返回 true，0 返回 false
     */
    public static boolean booleanValue(String reply) {
        if ("1".equals(reply)) {
            return true;
        }
        if ("0".equals(reply)) {
            return false;
        }
        throw protocolError("Expected 0 or 1 but was: " + reply);
    }

    /**
     * 解析逐行结果。
     *
     * @param reply 服务端回复
     * @return 结果列表；空回复返回空列表
     */
    public static List<String> lines(String reply) {
        if (reply.isEmpty()) {
            return new ArrayList<>();
        }
        String[] parts = reply.split("\n", -1);
        List<String> result = new ArrayList<>(parts.length);
        Collections.addAll(result, parts);
        return result;
    }

    /**
     * 解析字段和值逐行交替的映射。
     *
     * @param reply 服务端回复
     * @return 字段到值的映射；空回复返回空映射
     */
    public static Map<String, String> pairs(String reply) {
        List<String> lines = lines(reply);
        if (lines.size() % 2 != 0) {
            throw protocolError("Expected field/value pairs but was: " + reply);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i += 2) {
            result.put(lines.get(i), lines.get(i + 1));
        }
        return result;
    }

    /**
     * 解析 INFO 的 key:value 逐行结果。
     *
     * @param reply 服务端回复
     * @return 指标映射
     */
    public static Map<String, String> keyValues(String reply) {
        List<String> lines = lines(reply);
        Map<String, String> result = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator < 0) {
                throw protocolError("Expected key:value but was: " + line);
            }
            result.put(line.substring(0, separator), line.substring(separator + 1));
        }
        return result;
    }

    /**
     * 解析 ZRANGE WITHSCORES 的成员和分值。
     *
     * <p>服务端把成员和分值放在同一行，使用最后一个空格分隔；
     * 因此成员本身可以包含空格。</p>
     *
     * @param reply 服务端回复
     * @return 成员分值列表
     */
    public static List<ZSetEntry> scoredEntries(String reply) {
        List<String> lines = lines(reply);
        List<ZSetEntry> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            int separator = line.lastIndexOf(' ');
            if (separator < 0) {
                throw protocolError("Expected member and score but was: " + line);
            }
            String member = line.substring(0, separator);
            double score = doubleValue(line.substring(separator + 1));
            result.add(new ZSetEntry(member, score));
        }
        return result;
    }

    /**
     * 解析 PUBSUB NUMSUB 的频道和数量。
     *
     * @param reply 服务端回复
     * @return 频道到订阅数数量的映射
     */
    public static Map<String, Long> counts(String reply) {
        List<String> lines = lines(reply);
        Map<String, Long> result = new LinkedHashMap<>();
        for (String line : lines) {
            int separator = line.lastIndexOf(' ');
            if (separator < 0) {
                throw protocolError("Expected channel and count but was: " + line);
            }
            String channel = line.substring(0, separator);
            long count = longValue(line.substring(separator + 1));
            result.put(channel, count);
        }
        return result;
    }

    /**
     * 创建协议异常。
     *
     * @param message 错误信息
     * @return 协议异常
     */
    private static LightRedisProtocolException protocolError(String message) {
        return new LightRedisProtocolException(message);
    }
}
