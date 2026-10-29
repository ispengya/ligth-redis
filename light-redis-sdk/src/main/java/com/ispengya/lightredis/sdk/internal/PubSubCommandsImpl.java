package com.ispengya.lightredis.sdk.internal;

import com.ispengya.lightredis.sdk.command.PubSubCommands;
import com.ispengya.lightredis.sdk.pubsub.MessageListener;
import com.ispengya.lightredis.sdk.pubsub.Subscription;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 发布订阅命令实现。
 */
public final class PubSubCommandsImpl implements PubSubCommands {

    /**
     * 命令调用器。
     */
    private final CommandInvoker invoker;

    /**
     * 订阅管理器。
     */
    private final PubSubManager manager;

    /**
     * 创建命令实现。
     *
     * @param invoker 命令调用器
     * @param manager 订阅管理器
     */
    public PubSubCommandsImpl(CommandInvoker invoker, PubSubManager manager) {
        if (invoker == null) {
            throw new IllegalArgumentException("Invoker must not be null");
        }
        if (manager == null) {
            throw new IllegalArgumentException("Manager must not be null");
        }
        this.invoker = invoker;
        this.manager = manager;
    }

    @Override
    public long publish(String channel, String message) {
        Args.requireLineSafe(channel, "Channel");
        Args.requireText(message, "Message");
        return ReplyParser.longValue(invoker.execute("PUBLISH", channel, message));
    }

    @Override
    public Subscription subscribe(String channel, MessageListener listener) {
        Args.requireLineSafe(channel, "Channel");
        return manager.subscribe(Collections.singleton(channel), false, listener);
    }

    @Override
    public Subscription subscribe(Collection<String> channels, MessageListener listener) {
        return manager.subscribe(channels, false, listener);
    }

    @Override
    public Subscription psubscribe(String pattern, MessageListener listener) {
        Args.requireLineSafe(pattern, "Pattern");
        return manager.subscribe(Collections.singleton(pattern), true, listener);
    }

    @Override
    public Subscription psubscribe(Collection<String> patterns, MessageListener listener) {
        return manager.subscribe(patterns, true, listener);
    }

    @Override
    public List<String> channels() {
        return ReplyParser.lines(invoker.execute("PUBSUB", "CHANNELS"));
    }

    @Override
    public List<String> channels(String pattern) {
        Args.requireLineSafe(pattern, "Pattern");
        return ReplyParser.lines(invoker.execute("PUBSUB", "CHANNELS", pattern));
    }

    @Override
    public Map<String, Long> numSub(String... channels) {
        if (channels == null || channels.length == 0) {
            return Collections.emptyMap();
        }
        String[] args = new String[channels.length + 1];
        args[0] = "NUMSUB";
        for (int i = 0; i < channels.length; i++) {
            args[i + 1] = Args.requireLineSafe(channels[i], "Channel");
        }
        return ReplyParser.counts(invoker.execute("PUBSUB", args));
    }

    @Override
    public long numPat() {
        return ReplyParser.longValue(invoker.execute("PUBSUB", "NUMPAT"));
    }
}
