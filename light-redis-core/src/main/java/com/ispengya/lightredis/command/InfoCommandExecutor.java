package com.ispengya.lightredis.command;

import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.store.KeyValueStore;

/**
 * INFO 命令执行器。
 *
 * <p>以逐行 key:value 的形式输出服务端运行指标。</p>
 */
public class InfoCommandExecutor implements CommandExecutor {

    /**
     * 服务端统计信息。
     */
    private final ServerStats stats;

    /**
     * 创建 INFO 命令执行器。
     *
     * @param stats 服务端统计信息
     */
    public InfoCommandExecutor(ServerStats stats) {
        if (stats == null) {
            throw new IllegalArgumentException("Stats must not be null");
        }
        this.stats = stats;
    }

    @Override
    public String execute(Command command, KeyValueStore store) {
        StringBuilder result = new StringBuilder();
        append(result, "uptime_in_seconds", stats.getUptimeSeconds());
        append(result, "total_commands_processed", stats.getCommandCount());
        append(result, "connected_clients", stats.getConnectedClients());
        append(result, "keys", store.size());
        append(result, "jvm_used_memory_mb", stats.getUsedMemoryMb());
        append(result, "jvm_threads", stats.getJvmThreads());
        return result.toString();
    }

    private void append(StringBuilder result, String name, Object value) {
        if (result.length() > 0) {
            result.append('\n');
        }
        result.append(name).append(':').append(value);
    }
}
