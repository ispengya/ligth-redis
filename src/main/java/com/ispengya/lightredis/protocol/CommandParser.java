package com.ispengya.lightredis.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

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
        String[] parts = trimmed.split("\\s+");
        String name = parts[0].toUpperCase();
        List<String> args = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            args.add(parts[i]);
        }
        return new Command(name, args);
    }
}
