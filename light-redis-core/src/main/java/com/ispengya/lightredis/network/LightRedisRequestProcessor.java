package com.ispengya.lightredis.network;

import com.ispengya.lightredis.command.CommandService;
import com.ispengya.lightredis.metrics.ServerStats;
import com.ispengya.lightredis.persistence.PersistenceException;
import com.ispengya.lightredis.protocol.Command;
import com.ispengya.lightredis.protocol.CommandParser;
import com.ispengya.lightredis.pubsub.PubSubService;
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

    /**
     * 发布订阅服务。
     */
    private final PubSubService pubSubService;

    /**
     * 创建请求处理器。
     *
     * @param commandService 命令服务
     * @param stats 服务端统计
     * @param pubSubService 发布订阅服务
     */
    public LightRedisRequestProcessor(CommandService commandService, ServerStats stats,
                                      PubSubService pubSubService) {
        if (commandService == null) {
            throw new IllegalArgumentException("Command service must not be null");
        }
        if (stats == null) {
            throw new IllegalArgumentException("Stats must not be null");
        }
        if (pubSubService == null) {
            throw new IllegalArgumentException("Pub/sub service must not be null");
        }
        this.commandService = commandService;
        this.stats = stats;
        this.pubSubService = pubSubService;
    }

    @Override
    public RemotingCommand processRequest(ChannelHandlerContext ctx, RemotingCommand request) {
        stats.trackChannel(ctx);
        stats.incrementCommandCount();

        byte[] body = request.getBody();
        String result;
        try {
            Command command = CommandParser.parse(body);
            if (pubSubService.handles(command.getName())) {
                result = pubSubService.execute(command.getName(), command.getArgs(), ctx.channel());
            } else {
                result = commandService.execute(command);
            }
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
