package com.ispengya.lightredis.panel;

import com.ispengya.lightredis.client.LightRedisClient;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 面板 HTTP 服务。
 *
 * <p>只绑定回环地址，提供 API 和静态页面。</p>
 */
public class PanelServer {

    private static final Logger log = LoggerFactory.getLogger(PanelServer.class);
    private static final AtomicInteger THREAD_INDEX = new AtomicInteger(0);

    /**
     * 面板 HTTP 端口。
     */
    private final int httpPort;

    /**
     * 面板连接的 Redis 地址，仅用于展示。
     */
    private final String redisAddress;

    /**
     * JDK 内置 HTTP 服务。
     */
    private final HttpServer httpServer;

    /**
     * 处理请求的线程池，线程为守护线程。
     */
    private final ExecutorService executor;

    /**
     * 是否已经停止。
     */
    private volatile boolean stopped;

    /**
     * 创建面板服务。
     *
     * @param client Redis 客户端
     * @param redisAddress Redis 地址，仅用于展示
     * @param httpPort 面板 HTTP 端口
     * @param clientFactory 订阅专用客户端工厂
     * @throws IOException 端口绑定失败
     */
    public PanelServer(LightRedisClient client, String redisAddress, int httpPort,
                       Supplier<LightRedisClient> clientFactory) throws IOException {
        if (client == null) {
            throw new IllegalArgumentException("Client must not be null");
        }
        if (redisAddress == null || redisAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Redis address must not be empty");
        }
        if (httpPort <= 0 || httpPort > 65535) {
            throw new IllegalArgumentException("HTTP port must be between 1 and 65535");
        }
        if (clientFactory == null) {
            throw new IllegalArgumentException("Client factory must not be null");
        }
        this.httpPort = httpPort;
        this.redisAddress = redisAddress;
        this.httpServer = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), httpPort), 0);
        this.executor = Executors.newFixedThreadPool(16, runnable -> {
            Thread thread = new Thread(runnable,
                    "light-redis-panel-http-" + THREAD_INDEX.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.httpServer.createContext("/api/",
                new PanelApiHandler(client, redisAddress, httpPort, clientFactory));
        this.httpServer.createContext("/", new StaticFileHandler());
        this.httpServer.setExecutor(executor);
    }

    /**
     * 启动面板服务。
     */
    public void start() {
        httpServer.start();
        log.info("Panel started at http://127.0.0.1:{} (redis: {})", httpPort, redisAddress);
    }

    /**
     * 停止面板服务并释放线程池。
     */
    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        httpServer.stop(0);
        executor.shutdownNow();
    }

    /**
     * 获取面板 HTTP 端口。
     *
     * @return HTTP 端口
     */
    public int getHttpPort() {
        return httpPort;
    }
}
