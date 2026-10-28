package com.ispengya.lightredis.persistence;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandRegistry;
import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.protocol.CommandParser;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * AOF 后台重写测试。
 */
public class AofRewriteTest {

    private Path dataDir;

    private PersistenceConfig config;

    private FileCommandLog commandLog;

    private CommandService commandService;

    private CommandDispatcher dispatcher;

    @Before
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("light-redis-aof-rewrite-");
        config = new PersistenceConfig(dataDir.toString(), true);
        open(config);
    }

    @After
    public void tearDown() throws IOException {
        close();
        deleteRecursively(dataDir);
    }

    @Test
    public void rewritesAllTypesAndSurvivesRestart() {
        assertEquals("OK", execute("SET string:key hello"));
        assertEquals("3", execute("RPUSH list:key a b c"));
        assertEquals("2", execute("HSET hash:key name tom age 20"));
        assertEquals("2", execute("SADD set:key a b"));
        assertEquals("2", execute("ZADD zset:key 1 one 2 two"));
        assertEquals("OK", execute("SET transient value"));
        assertEquals("1", execute("DEL transient"));

        String start = execute("BGREWRITEAOF");
        assertTrue(start.startsWith("Background"));
        assertTrue(commandLog.awaitRewriteCompletion(10000L));
        assertTrue(commandLog.isLastRewriteSucceeded());
        Path rewrittenDir = commandLog.getActiveDir();
        assertNotEquals(dataDir.toAbsolutePath().normalize(), rewrittenDir);

        assertEquals("OK", execute("SET after:rewrite value"));
        close();
        open(config);

        assertEquals("hello", executeRead("GET string:key"));
        assertEquals("a\nb\nc", executeRead("LRANGE list:key 0 -1"));
        assertEquals("name\ntom\nage\n20", executeRead("HGETALL hash:key"));
        assertEquals("a\nb", executeRead("SMEMBERS set:key"));
        assertEquals("one 1\ntwo 2", executeRead("ZRANGE zset:key 0 -1 WITHSCORES"));
        assertEquals("value", executeRead("GET after:rewrite"));
        assertEquals("0", executeRead("EXISTS transient"));
        assertEquals("6", executeRead("DBSIZE"));
        assertEquals(rewrittenDir, commandLog.getActiveDir());
    }

    @Test
    public void keepsWritesThatHappenDuringRewrite() {
        for (int i = 0; i < 800; i++) {
            assertEquals("OK", execute("SET seed:" + i + " seed-" + i));
        }

        assertTrue(commandLog.startRewrite());
        for (int i = 0; i < 200; i++) {
            assertEquals("OK", execute("SET during:" + i + " during-" + i));
        }
        assertTrue(commandLog.awaitRewriteCompletion(15000L));
        assertTrue(commandLog.isLastRewriteSucceeded());

        close();
        open(config);
        assertEquals("seed-799", executeRead("GET seed:799"));
        assertEquals("during-199", executeRead("GET during:199"));
        assertEquals("1000", executeRead("DBSIZE"));
    }

    @Test
    public void canRewriteAgainWithoutRestart() {
        assertEquals("OK", execute("SET first v1"));
        assertTrue(commandLog.startRewrite());
        assertTrue(commandLog.awaitRewriteCompletion(10000L));
        assertTrue(commandLog.isLastRewriteSucceeded());

        assertEquals("OK", execute("SET second v2"));
        assertTrue(commandLog.startRewrite());
        assertTrue(commandLog.awaitRewriteCompletion(10000L));
        assertTrue(commandLog.isLastRewriteSucceeded());

        close();
        open(config);
        assertEquals("v1", executeRead("GET first"));
        assertEquals("v2", executeRead("GET second"));
        assertEquals("2", executeRead("DBSIZE"));
    }

    @Test
    public void autoRewriteTriggersWhenSizeThresholdIsReached() {
        close();
        config = new PersistenceConfig(dataDir.toString(), true, 1L, 100, 20L);
        open(config);

        assertEquals("OK", execute("SET auto:key value"));
        long deadline = System.currentTimeMillis() + 10000L;
        while (System.currentTimeMillis() < deadline
                && !commandLog.isLastRewriteSucceeded()
                && commandLog.getActiveDir().equals(dataDir.toAbsolutePath().normalize())) {
            sleep(20L);
        }

        assertTrue(commandLog.isLastRewriteSucceeded());
        assertNotEquals(dataDir.toAbsolutePath().normalize(), commandLog.getActiveDir());
        close();
        open(config);
        assertEquals("value", executeRead("GET auto:key"));
    }

    private void open(PersistenceConfig persistenceConfig) {
        dispatcher = new CommandDispatcher(new InMemoryKeyValueStore());
        CommandRegistry.registerAll(dispatcher, new ServerStats());
        commandLog = FileCommandLog.open(persistenceConfig, dispatcher);
        CommandRegistry.registerPersistenceCommands(dispatcher, commandLog);
        commandService = new CommandService(dispatcher, commandLog);
    }

    private void close() {
        if (commandLog != null) {
            commandLog.close();
            commandLog = null;
        }
    }

    private String execute(String text) {
        return commandService.execute(CommandParser.parse(text));
    }

    private String executeRead(String text) {
        return dispatcher.dispatch(CommandParser.parse(text));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Windows mmap 释放延迟
                }
            });
        }
    }
}
