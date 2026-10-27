package com.ispengya.lightredis.persistence;

import com.ispengya.lightredis.protocol.Command;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * AOF 记录编解码测试。
 */
public class AofCodecTest {

    private final AofCodec codec = new AofCodec();

    @Test
    public void roundTripsCommandNameAndArgs() {
        Command command = new Command("SET", Arrays.asList("key with space", "hello world", "中文"));
        AofRecord decoded = codec.decode(codec.encode(AofRecord.from(command)));

        assertEquals("SET", decoded.getCommandName());
        assertEquals(command.getArgs(), decoded.getArgs());
        assertEquals("GET", codec.decode(codec.encode(new AofRecord("get", Arrays.asList("k")))).getCommandName());
    }

    @Test
    public void rejectsUnsupportedVersion() {
        byte[] encoded = codec.encode(new AofRecord("PING", Arrays.asList()));
        encoded[0] = 2;
        try {
            codec.decode(encoded);
            fail("unsupported version should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void rejectsTrailingBytes() {
        byte[] encoded = codec.encode(new AofRecord("PING", Arrays.asList()));
        byte[] withTrailingByte = Arrays.copyOf(encoded, encoded.length + 1);
        try {
            codec.decode(withTrailingByte);
            fail("trailing bytes should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
