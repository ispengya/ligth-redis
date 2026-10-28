package com.ispengya.lightredis.store;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandRegistry;
import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.persistence.NoopCommandLog;
import com.ispengya.lightredis.protocol.CommandParser;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 过期时间与删除策略测试。
 */
public class ExpirationTest {

    private InMemoryKeyValueStore store;

    private CommandService commandService;

    @Before
    public void setUp() {
        store = new InMemoryKeyValueStore();
        CommandDispatcher dispatcher = new CommandDispatcher(store);
        CommandRegistry.registerAll(dispatcher, new ServerStats());
        commandService = new CommandService(dispatcher, NoopCommandLog.INSTANCE);
    }

    @Test
    public void readsAndRemovesTtl() {
        assertEquals("OK", execute("SET key value"));
        assertEquals("-1", execute("TTL key"));
        assertEquals("-1", execute("PTTL key"));

        assertEquals("1", execute("EXPIRE key 60"));
        long seconds = Long.parseLong(execute("TTL key"));
        long millis = Long.parseLong(execute("PTTL key"));
        assertTrue(seconds >= 59 && seconds <= 60);
        assertTrue(millis > 59000 && millis <= 60000);

        assertEquals("1", execute("PERSIST key"));
        assertEquals("-1", execute("TTL key"));
        assertEquals("0", execute("PERSIST key"));
    }

    @Test
    public void expireOnMissingKeyReturnsZero() {
        assertEquals("-2", execute("TTL missing"));
        assertEquals("-2", execute("PTTL missing"));
        assertEquals("0", execute("EXPIRE missing 10"));
        assertEquals("0", execute("PEXPIRE missing 10000"));
        assertEquals("0", execute("PERSIST missing"));
    }

    @Test
    public void nonPositiveTtlDeletesKey() {
        assertEquals("OK", execute("SET key value"));
        assertEquals("1", execute("EXPIRE key 0"));
        assertEquals("0", execute("EXISTS key"));
        assertEquals("-2", execute("TTL key"));

        assertEquals("OK", execute("SET key2 value"));
        assertEquals("1", execute("PEXPIRE key2 -1"));
        assertEquals("0", execute("EXISTS key2"));

        assertEquals("OK", execute("SET key3 value"));
        long past = System.currentTimeMillis() - 1000L;
        assertEquals("1", execute("PEXPIREAT key3 " + past));
        assertEquals("0", execute("EXISTS key3"));
    }

    @Test
    public void relativeExpireIsStoredAsAbsoluteTime() {
        assertEquals("OK", execute("SET key value"));
        long before = System.currentTimeMillis();
        assertEquals("1", execute("EXPIRE key 60"));

        Long expireAt = store.getExpireAt("key");
        assertNotNull(expireAt);
        assertTrue(expireAt >= before + 60000L);

        assertEquals("1", execute("PEXPIRE key 5000"));
        Long updated = store.getExpireAt("key");
        assertNotNull(updated);
        assertTrue(updated >= before + 5000L);
        assertTrue(updated < expireAt);
    }

    @Test
    public void lazyDeletionWorksForAllTypes() {
        assertEquals("OK", execute("SET string:key value"));
        assertEquals("2", execute("RPUSH list:key a b"));
        assertEquals("1", execute("HSET hash:key field value"));
        assertEquals("1", execute("SADD set:key a"));
        assertEquals("1", execute("ZADD zset:key 1 one"));

        long expireAt = System.currentTimeMillis() + 100L;
        assertEquals("1", execute("PEXPIREAT string:key " + expireAt));
        assertEquals("1", execute("PEXPIREAT list:key " + expireAt));
        assertEquals("1", execute("PEXPIREAT hash:key " + expireAt));
        assertEquals("1", execute("PEXPIREAT set:key " + expireAt));
        assertEquals("1", execute("PEXPIREAT zset:key " + expireAt));
        sleep(250L);

        assertEquals("(nil)", execute("GET string:key"));
        assertEquals("0", execute("EXISTS list:key"));
        assertEquals("none", execute("TYPE hash:key"));
        assertEquals("", execute("LRANGE list:key 0 -1"));
        assertEquals("", execute("HGETALL hash:key"));
        assertEquals("", execute("SMEMBERS set:key"));
        assertEquals("0", execute("ZCARD zset:key"));
        assertEquals("", execute("KEYS *"));
        assertEquals("0", execute("DBSIZE"));
    }

    @Test
    public void setClearsTtlAndCollectionWritesKeepIt() {
        assertEquals("OK", execute("SET key value"));
        assertEquals("1", execute("EXPIRE key 60"));
        assertEquals("OK", execute("SET key value2"));
        assertEquals("-1", execute("TTL key"));

        assertEquals("1", execute("RPUSH list a"));
        assertEquals("1", execute("PEXPIRE list 60000"));
        long before = Long.parseLong(execute("PTTL list"));
        assertEquals("2", execute("RPUSH list b"));
        long after = Long.parseLong(execute("PTTL list"));
        assertTrue(after > 0L);
        assertTrue(after <= before);
        assertEquals("a\nb", execute("LRANGE list 0 -1"));
    }

    @Test
    public void deleteExpiredKeyReturnsZero() {
        assertEquals("OK", execute("SET key value"));
        assertEquals("1", execute("PEXPIREAT key " + (System.currentTimeMillis() + 100L)));
        sleep(250L);

        assertEquals("0", execute("DEL key"));
        assertEquals("0", execute("EXISTS key"));
    }

    @Test
    public void activeCycleRemovesUntouchedKeys() {
        assertEquals("OK", execute("SET keep value"));
        assertEquals("OK", execute("SET expired:one value"));
        assertEquals("OK", execute("SET expired:two value"));
        long expireAt = System.currentTimeMillis() + 100L;
        assertEquals("1", execute("PEXPIREAT expired:one " + expireAt));
        assertEquals("1", execute("PEXPIREAT expired:two " + expireAt));
        sleep(250L);

        int removed = new ActiveExpireCycle(store).runOnce();
        assertEquals(2, removed);
        assertEquals("1", execute("DBSIZE"));
        assertEquals("value", execute("GET keep"));
        assertEquals(2L, store.getExpiredCount());
    }

    @Test
    public void purgeAllRemovesExpiredKeysAndClearsTtl() {
        assertEquals("OK", execute("SET expired value"));
        assertEquals("OK", execute("SET keep value"));
        assertEquals("1", execute("PEXPIREAT expired " + (System.currentTimeMillis() + 100L)));
        sleep(250L);
        assertNotNull(store.getExpireAt("expired"));

        assertEquals(1L, store.purgeAllExpired());
        assertNull(store.getExpireAt("expired"));
        assertEquals("1", execute("DBSIZE"));
    }

    @Test
    public void rejectsInvalidExpireArguments() {
        assertEquals("OK", execute("SET key value"));
        assertEquals("ERR invalid expire time", execute("EXPIRE key abc"));
        assertEquals("ERR invalid expire time", execute("PEXPIRE key 1.5"));
        assertEquals("ERR wrong number of arguments for EXPIRE", execute("EXPIRE key"));
        assertEquals("-1", execute("TTL key"));
    }

    private String execute(String text) {
        return commandService.execute(CommandParser.parse(text));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
