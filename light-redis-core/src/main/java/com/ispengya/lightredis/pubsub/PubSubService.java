package com.ispengya.lightredis.pubsub;

import com.ispengya.lightredis.protocol.GlobPattern;
import com.ispengya.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * 发布订阅服务。
 *
 * <p>订阅关系属于连接级别，不写入 AOF；连接关闭时自动清理。
 * 命令在命令单线程执行，推送通过 {@link PushSender} 写到目标连接。</p>
 */
public final class PubSubService {

    private static final Logger log = LoggerFactory.getLogger(PubSubService.class);

    /**
     * 推送发送器。
     */
    private final PushSender sender;

    /**
     * 连接及其订阅关系。
     */
    private final ConcurrentMap<Channel, ClientSubscriptions> clients = new ConcurrentHashMap<>();

    /**
     * 频道到订阅连接。
     */
    private final ConcurrentMap<String, Set<Channel>> channelIndex = new ConcurrentHashMap<>();

    /**
     * 模式到订阅连接。
     */
    private final ConcurrentMap<String, Set<Channel>> patternIndex = new ConcurrentHashMap<>();

    /**
     * 已编译的模式缓存。
     */
    private final ConcurrentMap<String, Pattern> patternCache = new ConcurrentHashMap<>();

    /**
     * 创建发布订阅服务。
     *
     * @param sender 推送发送器
     */
    public PubSubService(PushSender sender) {
        if (sender == null) {
            throw new IllegalArgumentException("Push sender must not be null");
        }
        this.sender = sender;
    }

    /**
     * 判断命令是否由发布订阅服务处理。
     *
     * @param commandName 命令名
     * @return 是发布订阅命令时返回 true
     */
    public boolean handles(String commandName) {
        if (commandName == null) {
            return false;
        }
        return "SUBSCRIBE".equals(commandName)
                || "UNSUBSCRIBE".equals(commandName)
                || "PSUBSCRIBE".equals(commandName)
                || "PUNSUBSCRIBE".equals(commandName)
                || "PUBLISH".equals(commandName)
                || "PUBSUB".equals(commandName);
    }

    /**
     * 执行发布订阅命令。
     *
     * @param commandName 命令名
     * @param args 命令参数
     * @param channel 当前连接
     * @return 文本回复
     */
    public String execute(String commandName, List<String> args, Channel channel) {
        if (channel == null) {
            return "ERR connection is not available";
        }
        switch (commandName) {
            case "SUBSCRIBE":
                return subscribe(channel, args, false);
            case "PSUBSCRIBE":
                return subscribe(channel, args, true);
            case "UNSUBSCRIBE":
                return unsubscribe(channel, args, false);
            case "PUNSUBSCRIBE":
                return unsubscribe(channel, args, true);
            case "PUBLISH":
                return publish(args);
            case "PUBSUB":
                return pubsub(args);
            default:
                return "ERR unknown command " + commandName;
        }
    }

    private String subscribe(Channel channel, List<String> args, boolean patternMode) {
        String commandName = patternMode ? "PSUBSCRIBE" : "SUBSCRIBE";
        if (args.isEmpty()) {
            return "ERR wrong number of arguments for " + commandName;
        }

        ClientSubscriptions client = clients.computeIfAbsent(channel, this::registerClient);
        List<String> lines = new ArrayList<>(args.size());
        for (String name : args) {
            if (patternMode) {
                client.patterns.add(name);
                patternIndex.computeIfAbsent(name, key -> ConcurrentHashMap.newKeySet())
                        .add(channel);
                patternCache.computeIfAbsent(name, GlobPattern::compile);
                lines.add("psubscribed " + name);
            } else {
                client.channels.add(name);
                channelIndex.computeIfAbsent(name, key -> ConcurrentHashMap.newKeySet())
                        .add(channel);
                lines.add("subscribed " + name);
            }
        }
        return String.join("\n", lines);
    }

    private String unsubscribe(Channel channel, List<String> args, boolean patternMode) {
        String commandName = patternMode ? "PUNSUBSCRIBE" : "UNSUBSCRIBE";
        ClientSubscriptions client = clients.get(channel);
        List<String> names;
        if (args.isEmpty()) {
            Set<String> current = patternMode
                    ? (client == null ? Collections.emptySet() : client.patterns)
                    : (client == null ? Collections.emptySet() : client.channels);
            names = new ArrayList<>(current);
            Collections.sort(names);
            if (names.isEmpty()) {
                return patternMode ? "punsubscribed all" : "unsubscribed all";
            }
        } else {
            names = args;
        }

        List<String> lines = new ArrayList<>(names.size());
        for (String name : names) {
            if (patternMode) {
                if (client != null && client.patterns.remove(name)) {
                    removePattern(name, channel);
                }
                lines.add("punsubscribed " + name);
            } else {
                if (client != null && client.channels.remove(name)) {
                    removeChannel(name, channel);
                }
                lines.add("unsubscribed " + name);
            }
        }
        return String.join("\n", lines);
    }

    private String publish(List<String> args) {
        if (args.size() != 2) {
            return "ERR wrong number of arguments for PUBLISH";
        }
        String channelName = args.get(0);
        String payload = args.get(1);
        int delivered = 0;

        Set<Channel> exactSubscribers = channelIndex.get(channelName);
        if (exactSubscribers != null) {
            for (Channel subscriber : exactSubscribers) {
                if (push(subscriber, PubSubProtocol.TYPE_MESSAGE,
                        channelName, null, payload)) {
                    delivered++;
                }
            }
        }

        for (Map.Entry<String, Set<Channel>> entry : patternIndex.entrySet()) {
            Pattern pattern = patternCache.computeIfAbsent(entry.getKey(), GlobPattern::compile);
            if (!pattern.matcher(channelName).matches()) {
                continue;
            }
            for (Channel subscriber : entry.getValue()) {
                if (push(subscriber, PubSubProtocol.TYPE_PMESSAGE,
                        channelName, entry.getKey(), payload)) {
                    delivered++;
                }
            }
        }
        return Integer.toString(delivered);
    }

    private String pubsub(List<String> args) {
        if (args.isEmpty()) {
            return "ERR wrong number of arguments for PUBSUB";
        }
        String subcommand = args.get(0).toUpperCase(Locale.ROOT);
        switch (subcommand) {
            case "CHANNELS":
                return pubsubChannels(args);
            case "NUMSUB":
                return pubsubNumsub(args);
            case "NUMPAT":
                if (args.size() != 1) {
                    return "ERR wrong number of arguments for PUBSUB NUMPAT";
                }
                return Integer.toString(patternIndex.size());
            default:
                return "ERR unknown PUBSUB subcommand " + subcommand;
        }
    }

    private String pubsubChannels(List<String> args) {
        if (args.size() > 2) {
            return "ERR wrong number of arguments for PUBSUB CHANNELS";
        }
        Pattern pattern = GlobPattern.compile(args.size() == 2 ? args.get(1) : "*");
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Set<Channel>> entry : channelIndex.entrySet()) {
            if (!entry.getValue().isEmpty() && pattern.matcher(entry.getKey()).matches()) {
                result.add(entry.getKey());
            }
        }
        Collections.sort(result);
        return String.join("\n", result);
    }

    private String pubsubNumsub(List<String> args) {
        if (args.size() < 2) {
            return "";
        }
        List<String> lines = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            String name = args.get(i);
            Set<Channel> subscribers = channelIndex.get(name);
            lines.add(name + " " + (subscribers == null ? 0 : subscribers.size()));
        }
        return String.join("\n", lines);
    }

    private boolean push(Channel channel, String type, String channelName,
                         String pattern, String payload) {
        if (!channel.isActive()) {
            removeClient(channel);
            return false;
        }
        Map<String, String> fields = new HashMap<>();
        fields.put(PubSubProtocol.FIELD_TYPE, type);
        fields.put(PubSubProtocol.FIELD_CHANNEL, channelName);
        if (pattern != null) {
            fields.put(PubSubProtocol.FIELD_PATTERN, pattern);
        }
        RemotingCommand message = RemotingCommand.createPushCommand(
                PubSubProtocol.PUSH_CODE, fields);
        message.setBody(payload.getBytes(StandardCharsets.UTF_8));
        try {
            sender.push(channel, message);
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to push message to channel {}", channelName, e);
            removeClient(channel);
            return false;
        }
    }

    private ClientSubscriptions registerClient(Channel channel) {
        ClientSubscriptions client = new ClientSubscriptions();
        channel.closeFuture().addListener(future -> removeClient(channel));
        return client;
    }

    private void removeClient(Channel channel) {
        ClientSubscriptions client = clients.remove(channel);
        if (client == null) {
            return;
        }
        for (String name : client.channels) {
            removeChannel(name, channel);
        }
        for (String pattern : client.patterns) {
            removePattern(pattern, channel);
        }
    }

    private void removeChannel(String name, Channel channel) {
        Set<Channel> subscribers = channelIndex.get(name);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(channel);
        if (subscribers.isEmpty()) {
            channelIndex.remove(name, subscribers);
        }
    }

    private void removePattern(String name, Channel channel) {
        Set<Channel> subscribers = patternIndex.get(name);
        if (subscribers == null) {
            return;
        }
        subscribers.remove(channel);
        if (subscribers.isEmpty()) {
            patternIndex.remove(name, subscribers);
            patternCache.remove(name);
        }
    }

    /**
     * 一个连接上的订阅关系。
     */
    private static final class ClientSubscriptions {

        /**
         * 精确订阅的频道。
         */
        private final Set<String> channels = ConcurrentHashMap.newKeySet();

        /**
         * 模式订阅。
         */
        private final Set<String> patterns = ConcurrentHashMap.newKeySet();
    }
}
