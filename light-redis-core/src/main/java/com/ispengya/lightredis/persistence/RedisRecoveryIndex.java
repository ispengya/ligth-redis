package com.ispengya.lightredis.persistence;

import com.ispengya.file.core.RecordFrame;
import com.ispengya.file.index.RecordIndex;
import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.CommandReplies;
import com.ispengya.lightredis.protocol.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 启动时把 AOF 命令回放到内存 store 的恢复索引。
 *
 * <p>启动阶段由 mini-file-storage 的 IndexHost 驱动 {@link #onFrame}，
 * 解码命令并执行到 {@link CommandDispatcher}。恢复完成后索引转为被动模式，
 * 运行时写命令由命令线程执行，避免重复应用。</p>
 */
public final class RedisRecoveryIndex implements RecordIndex {

    private static final Logger log = LoggerFactory.getLogger(RedisRecoveryIndex.class);

    /**
     * 命令分发器。
     */
    private final CommandDispatcher dispatcher;

    /**
     * AOF 编解码器。
     */
    private final AofCodec codec;

    /**
     * 是否已经完成启动恢复。
     */
    private volatile boolean recovered;

    /**
     * 已处理的日志位点，仅用于诊断。
     */
    private volatile long progress;

    /**
     * 恢复过程中的致命错误。
     */
    private volatile Throwable failure;

    /**
     * 恢复的命令数量。
     */
    private volatile long replayedCommands;

    /**
     * 恢复时返回错误的命令数量。
     */
    private volatile long failedCommands;

    /**
     * 创建恢复索引。
     *
     * @param dispatcher 命令分发器
     * @param codec AOF 编解码器
     */
    public RedisRecoveryIndex(CommandDispatcher dispatcher, AofCodec codec) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher must not be null");
        }
        if (codec == null) {
            throw new IllegalArgumentException("Codec must not be null");
        }
        this.dispatcher = dispatcher;
        this.codec = codec;
    }

    @Override
    public String name() {
        return "redis-memory-store";
    }

    @Override
    public long replayFrom(long logMaxOffset) {
        // 内存 store 每次启动都是空的，需要从日志起点全量恢复
        return 0L;
    }

    @Override
    public void onFrame(long offset, RecordFrame.Frame frame, byte[] body) {
        try {
            long end = offset + frame.getTotalLen();
            if (recovered) {
                progress = end;
                return;
            }

            AofRecord record = codec.decode(body);
            Command command = record.toCommand();
            String reply = dispatcher.dispatch(command);
            replayedCommands++;
            if (CommandReplies.isError(reply)) {
                failedCommands++;
                log.warn("AOF replay command returned an error: {} {} -> {}",
                        command.getName(), command.getArgs(), reply);
            }
            progress = end;
        } catch (RuntimeException e) {
            failure = e;
            throw e;
        }
    }

    @Override
    public long progress() {
        return progress;
    }

    @Override
    public void flush() {
        // 索引只存在于内存中，不需要落盘
    }

    /**
     * 标记启动恢复已经完成，之后的帧不再重复执行。
     */
    public void markRecovered() {
        this.recovered = true;
    }

    /**
     * 返回恢复阶段的致命错误。
     *
     * @return 没有错误时返回 null
     */
    public Throwable getFailure() {
        return failure;
    }

    /**
     * 返回启动阶段恢复的命令数量。
     *
     * @return 命令数量
     */
    public long getReplayedCommands() {
        return replayedCommands;
    }

    /**
     * 返回恢复阶段返回错误的命令数量。
     *
     * @return 失败命令数量
     */
    public long getFailedCommands() {
        return failedCommands;
    }
}
