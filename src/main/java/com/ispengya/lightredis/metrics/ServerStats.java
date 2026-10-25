package com.ispengya.lightredis.metrics;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 服务端运行期统计信息。
 *
 * <p>记录启动时间、已处理命令数、活跃连接数以及 JVM 运行指标，
 * 供 INFO 命令读取。</p>
 */
public class ServerStats {

    /**
     * 服务启动时间，单位毫秒。
     */
    private final long startTimeMillis;

    /**
     * 已处理命令计数器。
     */
    private final LongAdder commandCount;

    /**
     * 已发送过命令的连接集合。
     */
    private final Set<ChannelId> activeChannels;

    /**
     * 创建统计信息实例并记录启动时间。
     */
    public ServerStats() {
        this.startTimeMillis = System.currentTimeMillis();
        this.commandCount = new LongAdder();
        this.activeChannels = ConcurrentHashMap.newKeySet();
    }

    /**
     * 命令处理计数加一。
     */
    public void incrementCommandCount() {
        commandCount.increment();
    }

    /**
     * 获取已处理的命令总数。
     *
     * @return 命令总数
     */
    public long getCommandCount() {
        return commandCount.sum();
    }

    /**
     * 跟踪一个客户端连接。
     *
     * <p>连接首次发送命令时登记，连接关闭后自动移除。</p>
     *
     * @param ctx 当前请求的通道上下文
     */
    public void trackChannel(ChannelHandlerContext ctx) {
        final ChannelId channelId = ctx.channel().id();
        if (activeChannels.add(channelId)) {
            ctx.channel().closeFuture().addListener(future -> activeChannels.remove(channelId));
        }
    }

    /**
     * 获取发送过命令的活跃连接数。
     *
     * @return 活跃连接数
     */
    public int getConnectedClients() {
        return activeChannels.size();
    }

    /**
     * 获取服务运行时长。
     *
     * @return 运行秒数
     */
    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTimeMillis) / 1000L;
    }

    /**
     * 获取 JVM 已使用的堆内存。
     *
     * @return 以 MB 为单位、保留一位小数的字符串
     */
    public String getUsedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        return String.format(Locale.ROOT, "%.1f", usedBytes / 1024.0 / 1024.0);
    }

    /**
     * 获取当前 JVM 线程数。
     *
     * @return 线程数
     */
    public int getJvmThreads() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }
}
