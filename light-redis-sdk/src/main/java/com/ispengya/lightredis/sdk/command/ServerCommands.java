package com.ispengya.lightredis.sdk.command;

import java.util.Map;

/**
 * 服务端管理命令。
 */
public interface ServerCommands {

    /**
     * 检查服务端连通性。
     *
     * @return 服务端回复，通常为 PONG
     */
    String ping();

    /**
     * 获取服务端运行指标。
     *
     * @return INFO 回复解析后的键值映射
     */
    Map<String, String> info();

    /**
     * 触发后台 AOF 重写。
     *
     * @return 服务端回复文本
     */
    String bgRewriteAof();
}
