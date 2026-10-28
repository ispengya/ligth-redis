package com.ispengya.lightredis.pubsub;

import com.ispengya.remoting.protocol.RemotingCommand;
import io.netty.channel.Channel;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * 发布订阅服务测试。
 */
public class PubSubServiceTest {

    private final List<Push> pushes = new ArrayList<>();

    private final List<EmbeddedChannel> channels = new ArrayList<>();

    private PubSubService service;

    private EmbeddedChannel publisher;

    @Before
    public void setUp() {
        service = new PubSubService((channel, message) -> pushes.add(new Push(channel, message)));
        publisher = newChannel();
    }

    @After
    public void tearDown() {
        for (EmbeddedChannel channel : channels) {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    public void publishesToExactChannel() {
        EmbeddedChannel subscriber = newChannel();
        assertEquals("subscribed news", executeOn(subscriber, "SUBSCRIBE", "news"));
        assertEquals("1", executeOn(publisher, "PUBLISH", "news", "hello"));

        assertEquals(1, pushes.size());
        Push push = pushes.get(0);
        assertSame(subscriber, push.channel);
        assertEquals(PubSubProtocol.PUSH_CODE, push.message.getCode());
        assertEquals(PubSubProtocol.TYPE_MESSAGE,
                push.message.getExtFields().get(PubSubProtocol.FIELD_TYPE));
        assertEquals("news", push.message.getExtFields().get(PubSubProtocol.FIELD_CHANNEL));
        assertEquals("hello", body(push.message));
    }

    @Test
    public void publishesToMatchingPattern() {
        EmbeddedChannel subscriber = newChannel();
        assertEquals("psubscribed news.*", executeOn(subscriber, "PSUBSCRIBE", "news.*"));

        assertEquals("0", executeOn(publisher, "PUBLISH", "sports", "hello"));
        assertEquals("1", executeOn(publisher, "PUBLISH", "news.tech", "hello"));

        assertEquals(1, pushes.size());
        Push push = pushes.get(0);
        assertSame(subscriber, push.channel);
        assertEquals(PubSubProtocol.TYPE_PMESSAGE,
                push.message.getExtFields().get(PubSubProtocol.FIELD_TYPE));
        assertEquals("news.*", push.message.getExtFields().get(PubSubProtocol.FIELD_PATTERN));
        assertEquals("news.tech", push.message.getExtFields().get(PubSubProtocol.FIELD_CHANNEL));
        assertEquals("hello", body(push.message));
    }

    @Test
    public void deduplicatesChannelSubscriptions() {
        EmbeddedChannel subscriber = newChannel();
        executeOn(subscriber, "SUBSCRIBE", "news");
        executeOn(subscriber, "SUBSCRIBE", "news");

        assertEquals("1", executeOn(publisher, "PUBLISH", "news", "hello"));
        assertEquals(1, pushes.size());
    }

    @Test
    public void sendsBothMessageAndPmessageWhenBothMatch() {
        EmbeddedChannel subscriber = newChannel();
        executeOn(subscriber, "SUBSCRIBE", "news.tech");
        executeOn(subscriber, "PSUBSCRIBE", "news.*");

        assertEquals("2", executeOn(publisher, "PUBLISH", "news.tech", "hello"));
        assertEquals(2, pushes.size());
    }

    @Test
    public void unsubscribeStopsDelivery() {
        EmbeddedChannel subscriber = newChannel();
        executeOn(subscriber, "SUBSCRIBE", "news", "sports");
        assertEquals("unsubscribed news",
                executeOn(subscriber, "UNSUBSCRIBE", "news"));

        assertEquals("0", executeOn(publisher, "PUBLISH", "news", "hello"));
        assertEquals("1", executeOn(publisher, "PUBLISH", "sports", "hello"));
        assertEquals(1, pushes.size());

        executeOn(subscriber, "UNSUBSCRIBE");
        assertEquals("0", executeOn(publisher, "PUBLISH", "sports", "hello"));
        assertEquals("", executeOn(publisher, "PUBSUB", "CHANNELS"));
    }

    @Test
    public void closeRemovesSubscriptions() {
        EmbeddedChannel subscriber = newChannel();
        executeOn(subscriber, "SUBSCRIBE", "news");
        subscriber.close();
        subscriber.runPendingTasks();
        channels.remove(subscriber);

        assertEquals("0", executeOn(publisher, "PUBLISH", "news", "hello"));
        assertEquals(0, pushes.size());
    }

    @Test
    public void reportsPubSubState() {
        EmbeddedChannel first = newChannel();
        EmbeddedChannel second = newChannel();
        EmbeddedChannel pattern = newChannel();
        executeOn(first, "SUBSCRIBE", "news", "sports");
        executeOn(second, "SUBSCRIBE", "news");
        executeOn(pattern, "PSUBSCRIBE", "news.*");

        assertEquals("news\nsports", executeOn(publisher, "PUBSUB", "CHANNELS"));
        assertEquals("news 2\nsports 1",
                executeOn(publisher, "PUBSUB", "NUMSUB", "news", "sports"));
        assertEquals("1", executeOn(publisher, "PUBSUB", "NUMPAT"));
    }

    @Test
    public void rejectsInvalidCommands() {
        assertEquals("ERR wrong number of arguments for SUBSCRIBE",
                executeOn(publisher, "SUBSCRIBE"));
        assertEquals("ERR wrong number of arguments for PUBLISH",
                executeOn(publisher, "PUBLISH", "news"));
        assertEquals("ERR unknown PUBSUB subcommand FOO",
                executeOn(publisher, "PUBSUB", "FOO"));
    }

    private EmbeddedChannel newChannel() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channels.add(channel);
        return channel;
    }

    private String executeOn(Channel channel, String command, String... args) {
        return service.execute(command, Arrays.asList(args), channel);
    }

    private String body(RemotingCommand message) {
        byte[] body = message.getBody();
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }

    /**
     * 捕获的一次推送。
     */
    private static final class Push {

        /**
         * 目标连接。
         */
        private final Channel channel;

        /**
         * 推送消息。
         */
        private final RemotingCommand message;

        private Push(Channel channel, RemotingCommand message) {
            this.channel = channel;
            this.message = message;
        }
    }
}
