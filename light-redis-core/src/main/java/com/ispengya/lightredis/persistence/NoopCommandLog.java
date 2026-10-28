package com.ispengya.lightredis.persistence;

import com.ispengya.lightredis.protocol.Command;

/**
 * 关闭持久化时使用的空日志。
 */
public final class NoopCommandLog implements CommandLog {

    /**
     * 全局共享实例。
     */
    public static final NoopCommandLog INSTANCE = new NoopCommandLog();

    private NoopCommandLog() {
    }

    @Override
    public void append(Command command) {
        // 持久化关闭时不做任何事
    }

    @Override
    public void flush() {
        // 持久化关闭时不做任何事
    }

    @Override
    public boolean startRewrite() {
        return false;
    }

    @Override
    public boolean isRewriteInProgress() {
        return false;
    }

    @Override
    public void close() {
        // 持久化关闭时不做任何事
    }
}
