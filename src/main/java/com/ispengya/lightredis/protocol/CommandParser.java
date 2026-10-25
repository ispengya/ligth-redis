package com.ispengya.lightredis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 简化文本协议的命令解析器。
 *
 * <p>参数以空白分隔，支持单引号、双引号和反斜杠转义。
 * 反斜杠会原样转义下一个字符，例如 {@code \"} 表示双引号，
 * {@code \\} 表示反斜杠，{@code \ } 表示空格。</p>
 */
public final class CommandParser {

    private CommandParser() {
    }

    public static Command parse(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Empty command");
        }
        String text = new String(data, StandardCharsets.UTF_8);
        return parse(text);
    }

    public static Command parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Empty command");
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }

        List<String> tokens = tokenize(trimmed);
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }

        String name = tokens.get(0).toUpperCase(Locale.ROOT);
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Empty command");
        }

        List<String> args = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            args.add(tokens.get(i));
        }
        return new Command(name, args);
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean hasToken = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean escaping = false;

        for (int i = 0; i < text.length(); i++) {
            char currentChar = text.charAt(i);

            if (escaping) {
                current.append(currentChar);
                hasToken = true;
                escaping = false;
                continue;
            }

            if (currentChar == '\\') {
                escaping = true;
                hasToken = true;
                continue;
            }

            if (inSingleQuote) {
                if (currentChar == '\'') {
                    inSingleQuote = false;
                } else {
                    current.append(currentChar);
                }
                hasToken = true;
                continue;
            }

            if (inDoubleQuote) {
                if (currentChar == '"') {
                    inDoubleQuote = false;
                } else {
                    current.append(currentChar);
                }
                hasToken = true;
                continue;
            }

            if (currentChar == '\'') {
                inSingleQuote = true;
                hasToken = true;
                continue;
            }

            if (currentChar == '"') {
                inDoubleQuote = true;
                hasToken = true;
                continue;
            }

            if (Character.isWhitespace(currentChar)) {
                if (hasToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    hasToken = false;
                }
                continue;
            }

            current.append(currentChar);
            hasToken = true;
        }

        if (escaping) {
            current.append('\\');
            hasToken = true;
        }
        if (inSingleQuote) {
            throw new IllegalArgumentException("Unterminated single quote");
        }
        if (inDoubleQuote) {
            throw new IllegalArgumentException("Unterminated double quote");
        }
        if (hasToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
