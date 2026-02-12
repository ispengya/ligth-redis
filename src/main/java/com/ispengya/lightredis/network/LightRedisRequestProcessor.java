package com.ispengya.lightredis.network;

import com.ispengya.lightredis.command.CommandDispatcher;
import com.ispengya.lightredis.command.DelCommandExecutor;
import com.ispengya.lightredis.command.ExistsCommandExecutor;
import com.ispengya.lightredis.command.GetCommandExecutor;
import com.ispengya.lightredis.command.PingCommandExecutor;
import com.ispengya.lightredis.command.SetCommandExecutor;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.protocol.CommandParser;
import com.ispengya.lightredis.store.InMemoryKeyValueStore;
import com.ispengya.lightredis.store.KeyValueStore;
import com.ispengya.remoting.netty.NettyRequestProcessor;
import com.ispengya.remoting.protocol.RemotingCommand;
import io.netty.channel.ChannelHandlerContext;

public class LightRedisRequestProcessor implements NettyRequestProcessor {

    private final CommandDispatcher dispatcher;

    public LightRedisRequestProcessor() {
        KeyValueStore store = new InMemoryKeyValueStore();
        this.dispatcher = new CommandDispatcher(store);
        registerCommands();
    }

    private void registerCommands() {
        dispatcher.register("PING", new PingCommandExecutor());
        dispatcher.register("SET", new SetCommandExecutor());
        dispatcher.register("GET", new GetCommandExecutor());
        dispatcher.register("DEL", new DelCommandExecutor());
        dispatcher.register("EXISTS", new ExistsCommandExecutor());
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) {
        byte[] body = request.getBody();
        String result;
        try {
            Command command = CommandParser.parse(body);
            result = dispatcher.dispatch(command);
        } catch (Exception e) {
            result = "ERR " + e.getMessage();
        }
        RemotingCommand response = RemotingCommand.createResponseCommand(0, "OK");
        response.setBody(result.getBytes());
        return response;
    }
}
