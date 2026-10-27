package com.ispengya.lightredis.network;

import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.persistence.PersistenceException;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.protocol.CommandParser;
import com.ispengya.remoting.netty.NettyRequestProcessor;
import com.ispengya.remoting.protocol.RemotingCommand;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class LightRedisRequestProcessor implements NettyRequestProcessor {

    private static final Logger log = LoggerFactory.getLogger(LightRedisRequestProcessor.class);

    private final CommandService commandService;

    private final ServerStats stats;

    public LightRedisRequestProcessor(CommandService commandService, ServerStats stats) {
        if (commandService == null) {
            throw new IllegalArgumentException("Command service must not be null");
        }
        if (stats == null) {
            throw new IllegalArgumentException("Stats must not be null");
        }
        this.commandService = commandService;
        this.stats = stats;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) {
        stats.trackChannel(ctx);
        stats.incrementCommandCount();

        byte[] body = request.getBody();
        String result;
        try {
            Command command = CommandParser.parse(body);
            result = commandService.execute(command);
        } catch (PersistenceException e) {
            log.error("AOF append failed, command was not executed", e);
            result = "ERR persistence unavailable";
        } catch (Exception e) {
            result = "ERR " + e.getMessage();
        }
        RemotingCommand response = RemotingCommand.createResponseCommand(0, "OK");
        response.setBody(result.getBytes(StandardCharsets.UTF_8));
        return response;
    }
}
