package com.ispengya.lightredis.sdk.internal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * 命令编码器测试。
 */
public class CommandEncoderTest {

    @Test
    public void encodesPlainArguments() {
        assertEquals("SET \"key\" \"value\"", CommandEncoder.encode("SET", "key", "value"));
    }

    @Test
    public void encodesEmptyStrings() {
        assertEquals("SET \"\" \"\"", CommandEncoder.encode("SET", "", ""));
    }

    @Test
    public void escapesSpacesQuotesAndBackslashes() {
        String encoded = CommandEncoder.encode("SET", "a b", "c\"d", "e\\f");
        assertEquals("SET \"a b\" \"c\\\"d\" \"e\\\\f\"", encoded);
    }

    @Test
    public void keepsLineBreaksInsideQuotes() {
        assertEquals("SET \"k\" \"a\nb\"", CommandEncoder.encode("SET", "k", "a\nb"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNullArgument() {
        CommandEncoder.encode("SET", "k", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCommandWithWhitespace() {
        CommandEncoder.encode("S ET");
    }

    @Test
    public void rejectsEmptyCommand() {
        try {
            CommandEncoder.encode("");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertEquals("Command must not be empty", expected.getMessage());
        }
    }
}
