package com.ispengya.lightredis.bootstrap;

import com.ispengya.lightredis.persistence.PersistenceConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 服务端启动参数测试。
 */
public class ServerOptionsTest {

    @Test
    public void usesPersistenceDefaults() {
        ServerOptions options = ServerOptions.parse(new String[0]);

        assertEquals(PersistenceConfig.DEFAULT_DATA_DIR, options.getDataDir());
        assertTrue(options.isAppendOnly());
        assertFalse(options.isHelp());
    }

    @Test
    public void parsesExplicitOptions() {
        ServerOptions options = ServerOptions.parse(new String[]{
                "--data-dir=./target/aof", "--appendonly", "no"
        });

        assertEquals("./target/aof", options.getDataDir());
        assertFalse(options.isAppendOnly());
    }

    @Test
    public void parsesHelp() {
        assertTrue(ServerOptions.parse(new String[]{"--help"}).isHelp());
    }

    @Test
    public void rejectsUnknownOption() {
        try {
            ServerOptions.parse(new String[]{"--unknown"});
            fail("unknown option should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void rejectsInvalidAppendOnlyValue() {
        try {
            ServerOptions.parse(new String[]{"--appendonly=maybe"});
            fail("invalid appendonly value should be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
