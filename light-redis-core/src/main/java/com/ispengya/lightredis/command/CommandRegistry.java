package com.ispengya.lightredis.command;

import com.ispengya.lightredis.metrics.ServerStats;

/**
 * light-redis 命令注册表。
 *
 * <p>写命令显式标记，供 {@link CommandService} 决定是否需要先写 AOF。</p>
 */
public final class CommandRegistry {

    private CommandRegistry() {
    }

    /**
     * 注册全部支持的命令。
     *
     * @param dispatcher 命令分发器
     * @param stats 服务端统计信息
     */
    public static void registerAll(CommandDispatcher dispatcher, ServerStats stats) {
        if (dispatcher == null) {
            throw new IllegalArgumentException("Dispatcher must not be null");
        }
        if (stats == null) {
            throw new IllegalArgumentException("Stats must not be null");
        }

        dispatcher.register("PING", new PingCommandExecutor());
        dispatcher.register("SET", new SetCommandExecutor(), true);
        dispatcher.register("GET", new GetCommandExecutor());
        dispatcher.register("DEL", new DelCommandExecutor(), true);
        dispatcher.register("EXISTS", new ExistsCommandExecutor());
        dispatcher.register("INFO", new InfoCommandExecutor(stats));
        dispatcher.register("KEYS", new KeysCommandExecutor());
        dispatcher.register("TYPE", new TypeCommandExecutor());
        dispatcher.register("DBSIZE", new DbsizeCommandExecutor());

        dispatcher.register("LPUSH", new LpushCommandExecutor(), true);
        dispatcher.register("RPUSH", new RpushCommandExecutor(), true);
        dispatcher.register("LPOP", new LpopCommandExecutor(), true);
        dispatcher.register("RPOP", new RpopCommandExecutor(), true);
        dispatcher.register("LLEN", new LlenCommandExecutor());
        dispatcher.register("LRANGE", new LrangeCommandExecutor());
        dispatcher.register("LINDEX", new LindexCommandExecutor());
        dispatcher.register("LSET", new LsetCommandExecutor(), true);
        dispatcher.register("LREM", new LremCommandExecutor(), true);

        dispatcher.register("HSET", new HsetCommandExecutor(), true);
        dispatcher.register("HGET", new HgetCommandExecutor());
        dispatcher.register("HDEL", new HdelCommandExecutor(), true);
        dispatcher.register("HEXISTS", new HexistsCommandExecutor());
        dispatcher.register("HLEN", new HlenCommandExecutor());
        dispatcher.register("HGETALL", new HgetallCommandExecutor());
        dispatcher.register("HKEYS", new HkeysCommandExecutor());
        dispatcher.register("HVALS", new HvalsCommandExecutor());

        dispatcher.register("SADD", new SaddCommandExecutor(), true);
        dispatcher.register("SREM", new SremCommandExecutor(), true);
        dispatcher.register("SMEMBERS", new SmembersCommandExecutor());
        dispatcher.register("SISMEMBER", new SismemberCommandExecutor());
        dispatcher.register("SCARD", new ScardCommandExecutor());

        dispatcher.register("ZADD", new ZaddCommandExecutor(), true);
        dispatcher.register("ZREM", new ZremCommandExecutor(), true);
        dispatcher.register("ZSCORE", new ZscoreCommandExecutor());
        dispatcher.register("ZCARD", new ZcardCommandExecutor());
        dispatcher.register("ZRANGE", new ZrangeCommandExecutor());
        dispatcher.register("ZRANK", new ZrankCommandExecutor());
    }
}
