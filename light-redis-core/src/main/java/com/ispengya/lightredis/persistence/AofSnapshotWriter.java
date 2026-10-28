package com.ispengya.lightredis.persistence;

import com.ispengya.file.api.MiniFileStore;
import com.ispengya.file.api.StoreConfig;
import com.ispengya.lightredis.store.HashValue;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.lightredis.store.ListValue;
import com.ispengya.lightredis.store.RedisValue;
import com.ispengya.lightredis.store.SetValue;
import com.ispengya.lightredis.store.StringValue;
import com.ispengya.lightredis.store.ZSetValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 把当前内存状态转换成紧凑的 AOF 重建命令。
 */
final class AofSnapshotWriter {

    private static final Logger log = LoggerFactory.getLogger(AofSnapshotWriter.class);

    /**
     * 单条记录允许的最大 body 大小。
     */
    private static final int MAX_RECORD_BYTES = StoreConfig.DEFAULT_MAX_BODY_SIZE;

    /**
     * 集合类型单条命令的控制大小，给帧头留余量。
     */
    private static final int CHUNK_TARGET_BYTES = 900 * 1024;

    /**
     * 新 generation 的写入目标。
     */
    private final MiniFileStore<AofRecord> target;

    /**
     * 重写取消检查。
     */
    private final BooleanSupplier aborted;

    /**
     * 已写入记录数。
     */
    private long recordsWritten;

    AofSnapshotWriter(MiniFileStore<AofRecord> target, BooleanSupplier aborted) {
        if (target == null) {
            throw new IllegalArgumentException("Target store must not be null");
        }
        if (aborted == null) {
            throw new IllegalArgumentException("Aborted checker must not be null");
        }
        this.target = target;
        this.aborted = aborted;
    }

    /**
     * 写入全部 key 的快照。
     *
     * @param source 临时内存 store
     */
    void write(KeyValueStore source) {
        List<String> keys = new ArrayList<>(source.keys());
        Collections.sort(keys);
        for (String key : keys) {
            checkAborted();
            RedisValue value = source.get(key);
            if (value == null || value.isEmpty()) {
                continue;
            }
            switch (value.getType()) {
                case STRING:
                    writeString(key, (StringValue) value);
                    break;
                case LIST:
                    writeList(key, (ListValue) value);
                    break;
                case HASH:
                    writeHash(key, (HashValue) value);
                    break;
                case SET:
                    writeSet(key, (SetValue) value);
                    break;
                case ZSET:
                    writeZSet(key, (ZSetValue) value);
                    break;
                default:
                    throw new PersistenceException("Unsupported value type: " + value.getType());
            }
        }
        log.info("AOF snapshot generated: keys={}, records={}", keys.size(), recordsWritten);
    }

    private void writeString(String key, StringValue value) {
        List<String> args = new ArrayList<>(2);
        args.add(key);
        args.add(value.getValue());
        writeRecord("SET", args);
    }

    private void writeList(String key, ListValue value) {
        writeChunked("RPUSH", key, value.range(0, -1), 1);
    }

    private void writeHash(String key, HashValue value) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : value.entries()) {
            parts.add(entry.getKey());
            parts.add(entry.getValue());
        }
        writeChunked("HSET", key, parts, 2);
    }

    private void writeSet(String key, SetValue value) {
        writeChunked("SADD", key, value.members(), 1);
    }

    private void writeZSet(String key, ZSetValue value) {
        List<String> parts = new ArrayList<>();
        for (ZSetValue.ScoredMember member : value.range(0, -1)) {
            parts.add(formatScore(member.getScore()));
            parts.add(member.getMember());
        }
        writeChunked("ZADD", key, parts, 2);
    }

    /**
     * 按目标大小分块写集合类命令。
     *
     * @param command 命令名
     * @param key key
     * @param parts 扁平参数
     * @param groupSize 每组参数数量
     */
    private void writeChunked(String command, String key, List<String> parts, int groupSize) {
        if (parts.isEmpty()) {
            return;
        }
        if (parts.size() % groupSize != 0) {
            throw new PersistenceException("Invalid snapshot parts for " + command);
        }

        List<String> chunk = new ArrayList<>();
        for (int i = 0; i < parts.size(); i += groupSize) {
            checkAborted();
            List<String> group = parts.subList(i, i + groupSize);
            List<String> candidate = new ArrayList<>(chunk.size() + group.size());
            candidate.addAll(chunk);
            candidate.addAll(group);

            if (!chunk.isEmpty() && bodySize(command, key, candidate) > CHUNK_TARGET_BYTES) {
                flushChunk(command, key, chunk);
                chunk.clear();
                candidate.clear();
                candidate.addAll(group);
            }

            if (bodySize(command, key, candidate) > MAX_RECORD_BYTES) {
                throw new PersistenceException("Snapshot command exceeds max body size: "
                        + command + " " + key);
            }
            chunk.addAll(group);
        }
        if (!chunk.isEmpty()) {
            flushChunk(command, key, chunk);
        }
    }

    private void flushChunk(String command, String key, List<String> chunk) {
        List<String> args = new ArrayList<>(chunk.size() + 1);
        args.add(key);
        args.addAll(chunk);
        writeRecord(command, args);
    }

    private void writeRecord(String command, List<String> args) {
        checkAborted();
        if (bodySize(command, args) > MAX_RECORD_BYTES) {
            throw new PersistenceException("Snapshot record exceeds max body size: " + command);
        }
        target.put(new AofRecord(command, args));
        recordsWritten++;
    }

    private long bodySize(String command, String key, List<String> chunk) {
        List<String> args = new ArrayList<>(chunk.size() + 1);
        args.add(key);
        args.addAll(chunk);
        return bodySize(command, args);
    }

    private long bodySize(String command, List<String> args) {
        long size = 1L + 4L + utf8Length(command) + 4L;
        for (String arg : args) {
            size += 4L + utf8Length(arg);
        }
        return size;
    }

    private int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private String formatScore(double score) {
        if (score == Math.rint(score) && Math.abs(score) < 1.0E15) {
            return Long.toString((long) score);
        }
        return Double.toString(score);
    }

    private void checkAborted() {
        if (aborted.getAsBoolean()) {
            throw new RewriteAbortedException();
        }
    }
}
