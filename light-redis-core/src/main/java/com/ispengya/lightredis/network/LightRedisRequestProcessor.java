package com.ispengya.lightredis.network;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.DbsizeCommandExecutor;
import com.ispengya.lightredis.command.DelCommandExecutor;
import com.ispengya.lightredis.command.ExistsCommandExecutor;
import com.ispengya.lightredis.command.GetCommandExecutor;
import com.ispengya.lightredis.command.HdelCommandExecutor;
import com.ispengya.lightredis.command.HexistsCommandExecutor;
import com.ispengya.lightredis.command.HgetCommandExecutor;
import com.ispengya.lightredis.command.HgetallCommandExecutor;
import com.ispengya.lightredis.command.HkeysCommandExecutor;
import com.ispengya.lightredis.command.HlenCommandExecutor;
import com.ispengya.lightredis.command.HsetCommandExecutor;
import com.ispengya.lightredis.command.HvalsCommandExecutor;
import com.ispengya.lightredis.command.InfoCommandExecutor;
import com.ispengya.lightredis.command.KeysCommandExecutor;
import com.ispengya.lightredis.command.LindexCommandExecutor;
import com.ispengya.lightredis.command.LlenCommandExecutor;
import com.ispengya.lightredis.command.LpopCommandExecutor;
import com.ispengya.lightredis.command.LpushCommandExecutor;
import com.ispengya.lightredis.command.LrangeCommandExecutor;
import com.ispengya.lightredis.command.LremCommandExecutor;
import com.ispengya.lightredis.command.LsetCommandExecutor;
import com.ispengya.lightredis.command.PingCommandExecutor;
import com.ispengya.lightredis.command.RpopCommandExecutor;
import com.ispengya.lightredis.command.RpushCommandExecutor;
import com.ispengya.lightredis.command.SaddCommandExecutor;
import com.ispengya.lightredis.command.ScardCommandExecutor;
import com.ispengya.lightredis.command.SetCommandExecutor;
import com.ispengya.lightredis.command.SismemberCommandExecutor;
import com.ispengya.lightredis.command.SmembersCommandExecutor;
import com.ispengya.lightredis.command.SremCommandExecutor;
import com.ispengya.lightredis.command.TypeCommandExecutor;
import com.ispengya.lightredis.command.ZaddCommandExecutor;
import com.ispengya.lightredis.command.ZcardCommandExecutor;
import com.ispengya.lightredis.command.ZrangeCommandExecutor;
import com.ispengya.lightredis.command.ZrankCommandExecutor;
import com.ispengya.lightredis.command.ZremCommandExecutor;
import com.ispengya.lightredis.command.ZscoreCommandExecutor;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.protocol.CommandParser;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.remoting.netty.NettyRequestProcessor;
import com.ispengya.remoting.protocol.RemotingCommand;
import io.netty.channel.ChannelHandlerContext;

import java.nio.charset.StandardCharsets;

public class LightRedisRequestProcessor implements NettyRequestProcessor {

    private final CommandDispatcher dispatcher;

    private final ServerStats stats;

    public LightRedisRequestProcessor() {
        KeyValueStore store = new InMemoryKeyValueStore();
        this.stats = new ServerStats();
        this.dispatcher = new CommandDispatcher(store);
        registerCommands();
    }

    private void registerCommands() {
        dispatcher.register("PING", new PingCommandExecutor());
        dispatcher.register("SET", new SetCommandExecutor());
        dispatcher.register("GET", new GetCommandExecutor());
        dispatcher.register("DEL", new DelCommandExecutor());
        dispatcher.register("EXISTS", new ExistsCommandExecutor());
        dispatcher.register("INFO", new InfoCommandExecutor(stats));
        dispatcher.register("KEYS", new KeysCommandExecutor());
        dispatcher.register("TYPE", new TypeCommandExecutor());
        dispatcher.register("DBSIZE", new DbsizeCommandExecutor());

        dispatcher.register("LPUSH", new LpushCommandExecutor());
        dispatcher.register("RPUSH", new RpushCommandExecutor());
        dispatcher.register("LPOP", new LpopCommandExecutor());
        dispatcher.register("RPOP", new RpopCommandExecutor());
        dispatcher.register("LLEN", new LlenCommandExecutor());
        dispatcher.register("LRANGE", new LrangeCommandExecutor());
        dispatcher.register("LINDEX", new LindexCommandExecutor());
        dispatcher.register("LSET", new LsetCommandExecutor());
        dispatcher.register("LREM", new LremCommandExecutor());

        dispatcher.register("HSET", new HsetCommandExecutor());
        dispatcher.register("HGET", new HgetCommandExecutor());
        dispatcher.register("HDEL", new HdelCommandExecutor());
        dispatcher.register("HEXISTS", new HexistsCommandExecutor());
        dispatcher.register("HLEN", new HlenCommandExecutor());
        dispatcher.register("HGETALL", new HgetallCommandExecutor());
        dispatcher.register("HKEYS", new HkeysCommandExecutor());
        dispatcher.register("HVALS", new HvalsCommandExecutor());

        dispatcher.register("SADD", new SaddCommandExecutor());
        dispatcher.register("SREM", new SremCommandExecutor());
        dispatcher.register("SMEMBERS", new SmembersCommandExecutor());
        dispatcher.register("SISMEMBER", new SismemberCommandExecutor());
        dispatcher.register("SCARD", new ScardCommandExecutor());

        dispatcher.register("ZADD", new ZaddCommandExecutor());
        dispatcher.register("ZREM", new ZremCommandExecutor());
        dispatcher.register("ZSCORE", new ZscoreCommandExecutor());
        dispatcher.register("ZCARD", new ZcardCommandExecutor());
        dispatcher.register("ZRANGE", new ZrangeCommandExecutor());
        dispatcher.register("ZRANK", new ZrankCommandExecutor());
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) {
        stats.trackChannel(ctx);
        stats.incrementCommandCount();

        byte[] body = request.getBody();
        String result;
        try {
            Command command = CommandParser.parse(body);
            result = dispatcher.dispatch(command);
        } catch (Exception e) {
            result = "ERR " + e.getMessage();
        }
        RemotingCommand response = RemotingCommand.createResponseCommand(0, "OK");
        response.setBody(result.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
