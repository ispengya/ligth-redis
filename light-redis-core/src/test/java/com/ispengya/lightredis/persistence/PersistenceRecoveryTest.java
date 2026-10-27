package com.ispengya.lightredis.persistence;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandRegistry;
import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.protocol.CommandParser;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * AOF 写入与启动恢复集成测试。
 */
public class PersistenceRecoveryTest {

    private Path dataDir;

    private FileCommandLog commandLog;

    private CommandService commandService;

    @Before
    public void setUp() throws IOException {
        dataDir = Files.createTempDirectory("light-redis-aof-");
        openNewService();
    }

    @After
    public void tearDown() throws IOException {
        if (commandLog != null) {
            commandLog.close();
        }
        deleteRecursively(dataDir);
    }

    @Test
    public void restoresAllDataTypesAfterRestart() {
        assertEquals("OK", execute("SET string:key hello"));
        assertEquals("3", execute("RPUSH list:key a b c"));
        assertEquals("2", execute("HSET hash:key name tom age 20"));
        assertEquals("2", execute("SADD set:key a b"));
        assertEquals("2", execute("ZADD zset:key 1 one 2 two"));

        reopen();

        assertEquals("hello", execute("GET string:key"));
        assertEquals("a\nb\nc", execute("LRANGE list:key 0 -1"));
        assertEquals("name\ntom\nage\n20", execute("HGETALL hash:key"));
        assertEquals("a\nb", execute("SMEMBERS set:key"));
        assertEquals("one 1\ntwo 2", execute("ZRANGE zset:key 0 -1 WITHSCORES"));
        assertEquals("5", execute("DBSIZE"));
    }

    @Test
    public void doesNotLogReadCommands() {
        execute("SET key value");
        execute("GET key");
        execute("PING");
        execute("DBSIZE");

        reopen();

        assertEquals(1L, commandLog.getRecoveredCommands());
        assertEquals(0L, commandLog.getRecoveredFailures());
        assertEquals("value", execute("GET key"));
    }

    @Test
    public void replaysLoggedFailedWriteWithoutChangingState() {
        assertEquals("OK", execute("SET key value"));
        assertTrue(execute("LPUSH key x").startsWith("WRONGTYPE"));

        reopen();

        assertEquals("value", execute("GET key"));
        assertEquals(2L, commandLog.getRecoveredCommands());
        assertEquals(1L, commandLog.getRecoveredFailures());
    }

    @Test
    public void removesEmptyCollectionAfterReplay() {
        assertEquals("1", execute("RPUSH temp x"));
        assertEquals("x", execute("LPOP temp"));

        reopen();

        assertEquals("0", execute("EXISTS temp"));
        assertEquals(2L, commandLog.getRecoveredCommands());
    }

    private void openNewService() {
        CommandDispatcher dispatcher = newDispatcher();
        commandLog = FileCommandLog.open(new PersistenceConfig(dataDir.toString(), true), dispatcher);
        commandService = new CommandService(dispatcher, commandLog);
    }

    private void reopen() {
        commandLog.close();
        openNewService();
    }

    private CommandDispatcher newDispatcher() {
        CommandDispatcher dispatcher = new CommandDispatcher(new InMemoryKeyValueStore());
        CommandRegistry.registerAll(dispatcher, new ServerStats());
        return dispatcher;
    }

    private String execute(String text) {
        Command command = CommandParser.parse(text);
        return commandService.execute(command);
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Windows 下 mmap 释放存在延迟，测试目录删除失败不影响断言
                }
            });
        }
    }
}
