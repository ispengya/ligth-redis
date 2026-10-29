package com.ispengya.lightredis.sdk.pubsub;

import java.util.Set;

/**
 * 一个发布订阅句柄。
 *
 * <p>关闭句柄时发送对应的 UNSUBSCRIBE 或 PUNSUBSCRIBE。
 * 同一客户端上相同频道或模式的多个句柄会共享服务端订阅，
 * 只有最后一个句柄关闭时才会真正取消服务端订阅。</p>
 */
public interface Subscription extends AutoCloseable {

    /**
     * 返回本句柄订阅的频道或模式。
     *
     * @return 不可修改的频道或模式集合
     */
    Set<String> getTargets();

    /**
     * 判断是否为模式订阅。
     *
     * @return 模式订阅返回 true
     */
    boolean isPattern();

    /**
     * 判断句柄是否已关闭。
     *
     * @return 已关闭返回 true
     */
    boolean isClosed();

    /**
     * 关闭句柄并取消对应的服务端订阅。
     */
    @Override
    void close();
}
