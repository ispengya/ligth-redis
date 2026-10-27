package com.ispengya.lightredis.persistence;

import com.ispengya.lightredis.protocol.Command;

/**
 * 写命令日志。
 *
 * <p>写命令在真正修改内存之前追加到日志；日志追加成功后再执行命令。</p>
 */
public interface CommandLog extends AutoCloseable {

    /**
     * 追加一条写命令。
     *
     * @param command 写命令
     * @throws PersistenceException 日志追加失败
     */
    void append(Command command);

    /**
     * 强制刷盘。
     *
     * @throws PersistenceException 刷盘失败
     */
    void flush();

    /**
     * 刷盘并关闭日志。
     */
    @Override
    void close();
}
