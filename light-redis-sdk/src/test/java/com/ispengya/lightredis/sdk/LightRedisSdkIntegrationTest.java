package com.ispengya.lightredis.sdk;

import com.ispengya.lightredis.sdk.command.ZSetEntry;
import com.ispengya.lightredis.sdk.pubsub.MessageListener;
import com.ispengya.lightredis.sdk.pubsub.PubSubMessage;
import com.ispengya.lightredis.sdk.pubsub.Subscription;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 使用内嵌真实服务端验证 SDK 全部命令组。
 */
public class LightRedisSdkIntegrationTest {

    /**
     * 测试服务端。
     */
    private static EmbeddedLightRedisServer server;

    /**
     * 被测 SDK 客户端。
     */
    private static LightRedis redis;

    @BeforeClass
    public static void startServer() {
        server = new EmbeddedLightRedisServer();
        redis = LightRedis.connect("127.0.0.1", server.getPort(), 5000);
    }

    @AfterClass
    public static void stopServer() {
        if (redis != null) {
            redis.close();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    public void stringAndKeyCommands() {
        String key = newKey("string");
        assertNull(redis.strings().get(key));

        redis.strings().set(key, "hello world");
        assertEquals("hello world", redis.strings().get(key));
        assertTrue(redis.keys().exists(key));
        assertEquals("string", redis.keys().type(key));

        redis.strings().set(key, "");
        assertEquals("", redis.strings().get(key));

        String special = "quote \" slash \\ space";
        redis.strings().set(key, special);
        assertEquals(special, redis.strings().get(key));

        String multiline = "line1\nline2";
        redis.strings().set(key, multiline);
        assertEquals(multiline, redis.strings().get(key));

        assertEquals(1L, redis.keys().del(key));
        assertFalse(redis.keys().exists(key));
        assertEquals("none", redis.keys().type(key));

        String prefix = "sdk:keys:" + UUID.randomUUID();
        redis.strings().set(prefix + ":a", "1");
        redis.strings().set(prefix + ":b", "2");
        List<String> matched = redis.keys().keys(prefix + ":*");
        assertEquals(Arrays.asList(prefix + ":a", prefix + ":b"), matched);
        assertTrue(redis.keys().dbsize() >= 2L);

        redis.keys().del(prefix + ":a", prefix + ":b");
    }

    @Test
    public void ttlCommands() {
        String key = newKey("ttl");
        redis.strings().set(key, "v");
        assertEquals(-1L, redis.keys().ttl(key));
        assertEquals(-1L, redis.keys().pttl(key));

        assertTrue(redis.keys().expire(key, 600L));
        long ttl = redis.keys().ttl(key);
        assertTrue(ttl > 0L && ttl <= 600L);
        assertTrue(redis.keys().pttl(key) > 0L);

        assertTrue(redis.keys().persist(key));
        assertEquals(-1L, redis.keys().ttl(key));
        assertFalse(redis.keys().persist(key));

        assertTrue(redis.keys().pexpire(key, 600_000L));
        assertTrue(redis.keys().pttl(key) > 0L);
        assertTrue(redis.keys().expireAt(key, System.currentTimeMillis() / 1000L + 600L));
        assertTrue(redis.keys().ttl(key) > 0L);
        assertTrue(redis.keys().pexpireAt(key, System.currentTimeMillis() + 600_000L));
        assertTrue(redis.keys().pttl(key) > 0L);

        assertTrue(redis.keys().expire(key, -1L));
        assertFalse(redis.keys().exists(key));
        assertEquals(-2L, redis.keys().ttl(key));
        assertFalse(redis.keys().expire(key, 10L));

        String past = newKey("ttl-past");
        redis.strings().set(past, "v");
        assertTrue(redis.keys().expireAt(past, 1L));
        assertFalse(redis.keys().exists(past));
    }

    @Test
    public void listCommands() {
        String key = newKey("list");
        assertEquals(0L, redis.lists().llen(key));
        assertEquals(Collections.emptyList(), redis.lists().lrange(key, 0, -1));
        assertNull(redis.lists().lpop(key));
        assertNull(redis.lists().rpop(key));

        assertEquals(2L, redis.lists().rpush(key, "b", "c"));
        assertEquals(3L, redis.lists().lpush(key, "a"));
        assertEquals(Arrays.asList("a", "b", "c"), redis.lists().lrange(key, 0, -1));
        assertEquals("a", redis.lists().lindex(key, 0));
        assertEquals("c", redis.lists().lindex(key, -1));
        assertNull(redis.lists().lindex(key, 99));

        redis.lists().lset(key, 1, "B");
        assertEquals("B", redis.lists().lindex(key, 1));
        assertEquals(1L, redis.lists().lrem(key, 0, "B"));
        assertEquals(2L, redis.lists().llen(key));

        redis.lists().rpush(key, "hello world", "quote\"value");
        assertEquals("hello world", redis.lists().lindex(key, 2));
        assertEquals("quote\"value", redis.lists().lindex(key, 3));

        assertEquals("a", redis.lists().lpop(key));
        assertEquals("quote\"value", redis.lists().rpop(key));
        assertEquals(2L, redis.lists().llen(key));
        redis.keys().del(key);
    }

    @Test
    public void hashCommands() {
        String key = newKey("hash");
        assertEquals(0L, redis.hashes().hlen(key));
        assertEquals(Collections.emptyMap(), redis.hashes().hgetall(key));
        assertNull(redis.hashes().hget(key, "missing"));

        assertEquals(1L, redis.hashes().hset(key, "name", "tom"));
        assertEquals(0L, redis.hashes().hset(key, "name", "jerry"));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("age", "20");
        fields.put("city", "shanghai");
        assertEquals(2L, redis.hashes().hset(key, fields));

        assertEquals("jerry", redis.hashes().hget(key, "name"));
        assertEquals(3L, redis.hashes().hlen(key));
        assertTrue(redis.hashes().hexists(key, "age"));
        assertFalse(redis.hashes().hexists(key, "missing"));

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("name", "jerry");
        expected.putAll(fields);
        assertEquals(expected, redis.hashes().hgetall(key));
        assertTrue(redis.hashes().hkeys(key).contains("city"));
        assertTrue(redis.hashes().hvals(key).contains("shanghai"));

        assertEquals(1L, redis.hashes().hdel(key, "name"));
        assertEquals(1L, redis.hashes().hdel(key, "age", "missing"));
        assertEquals(1L, redis.hashes().hlen(key));
        assertEquals(1L, redis.hashes().hdel(key, "city"));
        assertEquals("none", redis.keys().type(key));
    }

    @Test
    public void setCommands() {
        String key = newKey("set");
        assertEquals(0L, redis.sets().scard(key));
        assertEquals(Collections.emptySet(), redis.sets().smembers(key));
        assertFalse(redis.sets().sismember(key, "a"));

        assertEquals(2L, redis.sets().sadd(key, "a", "b"));
        assertEquals(0L, redis.sets().sadd(key, "a"));
        assertEquals(2L, redis.sets().scard(key));
        assertTrue(redis.sets().sismember(key, "a"));
        assertEquals(new HashSet<>(Arrays.asList("a", "b")), redis.sets().smembers(key));

        assertEquals(1L, redis.sets().srem(key, "a"));
        assertFalse(redis.sets().sismember(key, "a"));
        assertEquals(1L, redis.sets().srem(key, "b"));
        assertEquals("none", redis.keys().type(key));
    }

    @Test
    public void sortedSetCommands() {
        String key = newKey("zset");
        assertEquals(0L, redis.sortedSets().zcard(key));
        assertEquals(Collections.emptyList(), redis.sortedSets().zrange(key, 0, -1));
        assertNull(redis.sortedSets().zscore(key, "one"));
        assertNull(redis.sortedSets().zrank(key, "one"));

        assertEquals(1L, redis.sortedSets().zadd(key, 2.0d, "two"));
        assertEquals(1L, redis.sortedSets().zadd(key, 1.0d, "one"));
        Map<String, Double> more = new LinkedHashMap<>();
        more.put("three", 3.5d);
        more.put("hello world", 0.5d);
        assertEquals(2L, redis.sortedSets().zadd(key, more));

        assertEquals(4L, redis.sortedSets().zcard(key));
        assertEquals(Double.valueOf(1.0d), redis.sortedSets().zscore(key, "one"));
        assertNull(redis.sortedSets().zscore(key, "missing"));
        assertEquals(Long.valueOf(0L), redis.sortedSets().zrank(key, "hello world"));
        assertEquals(Long.valueOf(2L), redis.sortedSets().zrank(key, "two"));
        assertNull(redis.sortedSets().zrank(key, "missing"));

        assertEquals(Arrays.asList("hello world", "one", "two", "three"),
                redis.sortedSets().zrange(key, 0, -1));
        List<ZSetEntry> entries = redis.sortedSets().zrangeWithScores(key, 0, -1);
        assertEquals(4, entries.size());
        assertEquals(new ZSetEntry("hello world", 0.5d), entries.get(0));
        assertEquals(new ZSetEntry("three", 3.5d), entries.get(3));

        assertEquals(1L, redis.sortedSets().zrem(key, "one"));
        assertEquals(3L, redis.sortedSets().zrem(key, "two", "three", "hello world"));
        assertEquals("none", redis.keys().type(key));
    }

    @Test
    public void serverAndRawCommands() {
        assertEquals("PONG", redis.server().ping());
        Map<String, String> info = redis.server().info();
        assertTrue(info.containsKey("uptime_in_seconds"));
        assertTrue(info.containsKey("keys"));

        String key = newKey("raw");
        assertEquals("OK", redis.raw().execute("SET", key, "raw-value"));
        assertEquals("raw-value", redis.strings().get(key));
        assertEquals("raw-value", redis.raw().execute("GET \"" + key + "\""));

        try {
            redis.raw().execute("NOPE");
            fail("Expected RedisCommandException");
        } catch (RedisCommandException expected) {
            assertTrue(expected.getReply().startsWith("ERR unknown command"));
        }

        try {
            redis.server().bgRewriteAof();
            fail("Expected RedisCommandException");
        } catch (RedisCommandException expected) {
            assertEquals("ERR persistence disabled", expected.getReply());
        }

        String wrongType = newKey("wrong-type");
        redis.lists().rpush(wrongType, "a");
        try {
            redis.strings().get(wrongType);
            fail("Expected RedisCommandException");
        } catch (RedisCommandException expected) {
            assertTrue(expected.getReply().startsWith("WRONGTYPE "));
        }
    }

    @Test
    public void pubSubReferenceCounting() throws Exception {
        String channel = "sdk:channel:" + UUID.randomUUID();
        CountDownLatch firstRound = new CountDownLatch(2);
        CountDownLatch secondRound = new CountDownLatch(1);
        AtomicReference<CountDownLatch> activeLatch = new AtomicReference<>(firstRound);
        AtomicInteger received = new AtomicInteger();

        MessageListener listener = message -> {
            received.incrementAndGet();
            CountDownLatch latch = activeLatch.get();
            if (latch != null) {
                latch.countDown();
            }
        };

        Subscription first = redis.pubsub().subscribe(channel, listener);
        Subscription second = redis.pubsub().subscribe(channel, listener);
        try {
            assertEquals(Long.valueOf(1L), redis.pubsub().numSub(channel).get(channel));
            assertTrue(redis.pubsub().channels().contains(channel));

            assertEquals(1L, redis.pubsub().publish(channel, "one"));
            assertTrue(firstRound.await(3, TimeUnit.SECONDS));
            assertEquals(2, received.get());

            activeLatch.set(secondRound);
            first.close();
            assertTrue(first.isClosed());
            assertFalse(second.isClosed());
            assertEquals(Long.valueOf(1L), redis.pubsub().numSub(channel).get(channel));

            assertEquals(1L, redis.pubsub().publish(channel, "two"));
            assertTrue(secondRound.await(3, TimeUnit.SECONDS));
            assertEquals(3, received.get());
        } finally {
            first.close();
            second.close();
        }

        assertEquals(Long.valueOf(0L), redis.pubsub().numSub(channel).get(channel));
        assertFalse(redis.pubsub().channels().contains(channel));
        assertEquals(0L, redis.pubsub().publish(channel, "after-close"));
    }

    @Test
    public void pubSubPatternAndIntrospection() throws Exception {
        String prefix = "sdk:pattern:" + UUID.randomUUID();
        String channel = prefix + ":topic";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PubSubMessage> received = new AtomicReference<>();
        long patternsBefore = redis.pubsub().numPat();

        Subscription subscription = redis.pubsub().psubscribe(prefix + ":*", message -> {
            received.set(message);
            latch.countDown();
        });
        try {
            assertEquals(patternsBefore + 1L, redis.pubsub().numPat());
            assertEquals(1L, redis.pubsub().publish(channel, "payload"));
            assertTrue(latch.await(3, TimeUnit.SECONDS));

            PubSubMessage message = received.get();
            assertEquals(PubSubMessage.Type.PMESSAGE, message.getType());
            assertEquals(channel, message.getChannel());
            assertEquals(prefix + ":*", message.getPattern());
            assertEquals("payload", message.getPayload());
            assertEquals(Long.valueOf(0L), redis.pubsub().numSub(channel).get(channel));
        } finally {
            subscription.close();
        }

        assertEquals(patternsBefore, redis.pubsub().numPat());
        assertEquals(0L, redis.pubsub().publish(channel, "after-close"));
    }

    @Test
    public void closeInsideCallbackDoesNotDeadlock() throws Exception {
        String channel = "sdk:callback-close:" + UUID.randomUUID();
        AtomicReference<Subscription> reference = new AtomicReference<>();
        CountDownLatch callbackDone = new CountDownLatch(1);
        Subscription subscription = redis.pubsub().subscribe(channel, message -> {
            Subscription current = reference.get();
            if (current != null) {
                current.close();
            }
            callbackDone.countDown();
        });
        reference.set(subscription);
        try {
            assertEquals(1L, redis.pubsub().publish(channel, "close-me"));
            assertTrue(callbackDone.await(3, TimeUnit.SECONDS));
            assertTrue(subscription.isClosed());
            assertEquals(0L, redis.pubsub().publish(channel, "after"));
        } finally {
            subscription.close();
        }
    }

    @Test
    public void multiTargetSubscriptionSharesServerRelationship() throws Exception {
        String first = "sdk:multi:" + UUID.randomUUID();
        String second = "sdk:multi:" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(2);
        Subscription subscription = redis.pubsub().subscribe(
                Arrays.asList(first, second), message -> latch.countDown());
        try {
            assertEquals(1L, redis.pubsub().publish(first, "a"));
            assertEquals(1L, redis.pubsub().publish(second, "b"));
            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(2, subscription.getTargets().size());
            assertFalse(subscription.isPattern());
        } finally {
            subscription.close();
        }
        assertEquals(0L, redis.pubsub().publish(first, "a"));
        assertEquals(0L, redis.pubsub().publish(second, "b"));
    }

    @Test
    public void inputValidationRejectsUnsafeValues() {
        String key = newKey("validation");
        try {
            redis.strings().set("bad\nkey", "v");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("line breaks"));
        }
        try {
            redis.lists().rpush(key, "bad\nvalue");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("line breaks"));
        }
        try {
            redis.keys().del();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be empty"));
        }
        try {
            redis.raw().execute(" ");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be empty"));
        }
        try {
            redis.pubsub().subscribe(key, null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Listener"));
        }
    }

    @Test
    public void builderConnectsAndCloses() {
        try (LightRedis built = LightRedis.builder()
                .host("127.0.0.1")
                .port(server.getPort())
                .timeoutMillis(3000L)
                .connect()) {
            assertEquals("PONG", built.server().ping());
            assertFalse(built.isClosed());
        }
    }

    @Test
    public void pubSubPayloadAllowsLineBreaks() throws Exception {
        String channel = "sdk:multiline:" + UUID.randomUUID();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PubSubMessage> received = new AtomicReference<>();
        Subscription subscription = redis.pubsub().subscribe(channel, message -> {
            received.set(message);
            latch.countDown();
        });
        try {
            String payload = "line1\nline2";
            assertEquals(1L, redis.pubsub().publish(channel, payload));
            assertTrue(latch.await(3, TimeUnit.SECONDS));
            assertEquals(payload, received.get().getPayload());
        } finally {
            subscription.close();
        }
    }

    @Test
    public void closedClientRejectsFurtherCommands() {
        LightRedis temporary = LightRedis.connect("127.0.0.1", server.getPort(), 3000);
        temporary.close();
        temporary.close();
        assertTrue(temporary.isClosed());
        try {
            temporary.server().ping();
            fail("Expected LightRedisException");
        } catch (LightRedisException expected) {
            assertEquals("Client is closed", expected.getMessage());
        }
    }

    /**
     * 生成唯一的测试 key。
     *
     * @param name key 用途
     * @return 唯一 key
     */
    private static String newKey(String name) {
        return "sdk:" + name + ":" + UUID.randomUUID();
    }
}
