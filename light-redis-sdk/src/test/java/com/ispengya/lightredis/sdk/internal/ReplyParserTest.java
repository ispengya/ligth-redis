package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.LightRedisProtocolException;
import com.ispengya.lightredis.sdk.RedisCommandException;
import com.ispengya.lightredis.sdk.command.ZSetEntry;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 回复解析器测试。
 */
public class ReplyParserTest {

    @Test
    public void parsesStringsAndNil() {
        assertEquals("hello", ReplyParser.string("hello"));
        assertEquals("", ReplyParser.string(""));
        assertNull(ReplyParser.string("(nil)"));
    }

    @Test
    public void parsesNumbersAndFlags() {
        assertEquals(42L, ReplyParser.longValue("42"));
        assertEquals(-2L, ReplyParser.longValue("-2"));
        assertEquals(7, ReplyParser.intValue("7"));
        assertEquals(1.5d, ReplyParser.doubleValue("1.5"), 0.0d);
        assertNull(ReplyParser.longOrNull("(nil)"));
        assertNull(ReplyParser.doubleOrNull("(nil)"));
        assertTrue(ReplyParser.booleanValue("1"));
        assertFalse(ReplyParser.booleanValue("0"));
    }

    @Test
    public void parsesLinesAndPairs() {
        assertEquals(Arrays.asList("a", "b", "c"), ReplyParser.lines("a\nb\nc"));
        assertEquals(Collections.emptyList(), ReplyParser.lines(""));
        assertEquals(Arrays.asList("a", ""), ReplyParser.lines("a\n"));

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("name", "tom");
        expected.put("age", "20");
        assertEquals(expected, ReplyParser.pairs("name\ntom\nage\n20"));
        assertEquals(Collections.emptyMap(), ReplyParser.pairs(""));
    }

    @Test
    public void parsesInfoAndCounts() {
        Map<String, String> info = ReplyParser.keyValues("keys:2\nuptime_in_seconds:10");
        assertEquals("2", info.get("keys"));
        assertEquals("10", info.get("uptime_in_seconds"));

        Map<String, Long> counts = ReplyParser.counts("demo:channel 2\nother 0");
        assertEquals(Long.valueOf(2), counts.get("demo:channel"));
        assertEquals(Long.valueOf(0), counts.get("other"));
    }

    @Test
    public void parsesScoredEntriesWithSpacesInMember() {
        List<ZSetEntry> entries = ReplyParser.scoredEntries("hello world 1.5\nplain 2");
        assertEquals(Arrays.asList(
                new ZSetEntry("hello world", 1.5d),
                new ZSetEntry("plain", 2.0d)), entries);
    }

    @Test
    public void detectsServerErrors() {
        try {
            ReplyParser.checkError("WRONGTYPE Operation against a key holding the wrong kind of value");
            fail("Expected RedisCommandException");
        } catch (RedisCommandException expected) {
            assertTrue(expected.getReply().startsWith("WRONGTYPE "));
        }
    }

    @Test(expected = LightRedisProtocolException.class)
    public void rejectsMalformedNumber() {
        ReplyParser.longValue("not-a-number");
    }

    @Test(expected = LightRedisProtocolException.class)
    public void rejectsOddPairCount() {
        ReplyParser.pairs("field\nvalue\nextra");
    }

    @Test(expected = LightRedisProtocolException.class)
    public void rejectsNullReply() {
        ReplyParser.checkError(null);
    }
}
