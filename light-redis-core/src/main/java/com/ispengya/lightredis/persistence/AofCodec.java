package com.ispengya.lightredis.persistence;

import com.ispengya.file.codec.Codec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AOF 记录编解码。
 *
 * <p>格式：</p>
 * <pre>
 * [1B version]
 * [4B nameLength][name UTF-8]
 * [4B argCount]
 * repeat:
 *   [4B argLength][arg UTF-8]
 * </pre>
 *
 * <p>CommitLog 帧本身已经提供长度和 CRC 校验，这里负责校验记录内部结构。</p>
 */
public final class AofCodec implements Codec<AofRecord> {

    /**
     * 当前记录版本。
     */
    private static final int VERSION = 1;

    /**
     * 参数数量的保护上限，避免脏数据触发超大集合分配。
     */
    private static final int MAX_ARGS = 1_000_000;

    @Override
    public byte[] encode(AofRecord value) {
        if (value == null) {
            throw new IllegalArgumentException("AOF record must not be null");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(VERSION);
            writeString(output, value.getCommandName());
            output.writeInt(value.getArgs().size());
            for (String arg : value.getArgs()) {
                writeString(output, arg);
            }
            output.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode AOF record", e);
        }
    }

    @Override
    public AofRecord decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("AOF record body must not be empty");
        }
        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(data));
            int version = input.readUnsignedByte();
            if (version != VERSION) {
                throw new IllegalArgumentException("Unsupported AOF record version: " + version);
            }
            String commandName = readString(input);
            if (commandName.isEmpty()) {
                throw new IllegalArgumentException("AOF command name must not be empty");
            }
            int argCount = input.readInt();
            if (argCount < 0 || argCount > MAX_ARGS) {
                throw new IllegalArgumentException("Invalid AOF argument count: " + argCount);
            }
            if (argCount > input.available() / Integer.BYTES) {
                throw new IllegalArgumentException("AOF argument count exceeds remaining bytes: " + argCount);
            }
            List<String> args = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) {
                args.add(readString(input));
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("Trailing bytes in AOF record: " + input.available());
            }
            return new AofRecord(commandName, args);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid AOF record", e);
        }
    }

    /**
     * 写入一个长度前缀的 UTF-8 字符串。
     *
     * @param output 输出流
     * @param value 字符串
     * @throws IOException 写入失败
     */
    private void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    /**
     * 读取一个长度前缀的 UTF-8 字符串。
     *
     * @param input 输入流
     * @return 字符串
     * @throws IOException 读取失败或长度非法
     */
    private String readString(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available()) {
            throw new IOException("Invalid AOF field length: " + length);
        }
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
