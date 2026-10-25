package com.ispengya.lightredis.panel;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 面板使用的极简 JSON 输出工具。
 *
 * <p>面板只需要输出少量结构化数据，因此不引入额外的 JSON 依赖。</p>
 */
public final class PanelJson {

    private PanelJson() {
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param value 支持 Map、Iterable、String、Number、Boolean 和 null
     * @return JSON 字符串
     */
    public static String write(Object value) {
        StringBuilder result = new StringBuilder();
        writeValue(result, value);
        return result.toString();
    }

    private static void writeValue(StringBuilder result, Object value) {
        if (value == null) {
            result.append("null");
            return;
        }
        if (value instanceof String) {
            writeString(result, (String) value);
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            result.append(value);
            return;
        }
        if (value instanceof Map) {
            writeMap(result, (Map<?, ?>) value);
            return;
        }
        if (value instanceof Iterable) {
            writeIterable(result, (Iterable<?>) value);
            return;
        }
        writeString(result, String.valueOf(value));
    }

    private static void writeMap(StringBuilder result, Map<?, ?> map) {
        result.append('{');
        Iterator<? extends Map.Entry<?, ?>> iterator = map.entrySet().iterator();
        boolean first = true;
        while (iterator.hasNext()) {
            Map.Entry<?, ?> entry = iterator.next();
            if (!first) {
                result.append(',');
            }
            first = false;
            writeString(result, String.valueOf(entry.getKey()));
            result.append(':');
            writeValue(result, entry.getValue());
        }
        result.append('}');
    }

    private static void writeIterable(StringBuilder result, Iterable<?> values) {
        result.append('[');
        boolean first = true;
        for (Object value : values) {
            if (!first) {
                result.append(',');
            }
            first = false;
            writeValue(result, value);
        }
        result.append(']');
    }

    private static void writeString(StringBuilder result, String value) {
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"':
                    result.append("\\\"");
                    break;
                case '\\':
                    result.append("\\\\");
                    break;
                case '\b':
                    result.append("\\b");
                    break;
                case '\f':
                    result.append("\\f");
                    break;
                case '\n':
                    result.append("\\n");
                    break;
                case '\r':
                    result.append("\\r");
                    break;
                case '\t':
                    result.append("\\t");
                    break;
                default:
                    if (current < 0x20) {
                        result.append(String.format(Locale.ROOT, "\\u%04x", (int) current));
                    } else {
                        result.append(current);
                    }
                    break;
            }
        }
        result.append('"');
    }
}
