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
     * 启动一次后台 AOF 重写。
     *
     * @return 已启动返回 true；已有重写或持久化关闭时返回 false
     */
    boolean startRewrite();

    /**
     * 判断当前是否有重写任务。
     *
     * @return 重写进行中返回 true
     */
    boolean isRewriteInProgress();

    /**
     * 刷盘并关闭日志。
     */
    @Override
    void close();
}
