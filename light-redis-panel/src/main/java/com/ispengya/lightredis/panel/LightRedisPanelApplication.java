package com.ispengya.lightredis.panel;

import com.ispengya.lightredis.client.RemotingLightRedisClient;
import picocli.CommandLine;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.CountDownLatch;

/**
 * light-redis 本地 Web 控制台启动入口。
 *
 * <p>面板作为独立进程运行，通过客户端核心连接 light-redis 服务端。</p>
 */
@CommandLine.Command(name = "light-redis-panel", mixinStandardHelpOptions = true, version = "0.0.1")
public class LightRedisPanelApplication implements Runnable {

    @CommandLine.Option(names = {"--redis-host"}, description = "Redis server host", defaultValue = "127.0.0.1")
    private String redisHost;

    @CommandLine.Option(names = {"--redis-port"}, description = "Redis server port", defaultValue = "6379")
    private int redisPort;

    @CommandLine.Option(names = {"--http-port"}, description = "Panel HTTP port", defaultValue = "8080")
    private int httpPort;

    @CommandLine.Option(names = {"-t", "--timeout"}, description = "Redis request timeout millis", defaultValue = "5000")
    private long timeoutMillis;

    @CommandLine.Option(names = {"--open"}, description = "Open the panel in the default browser")
    private boolean open;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new LightRedisPanelApplication()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        RemotingLightRedisClient client = new RemotingLightRedisClient(redisHost, redisPort, timeoutMillis);
        PanelServer server;
        try {
            client.start();
            server = new PanelServer(client, redisHost + ":" + redisPort, httpPort,
                    () -> new RemotingLightRedisClient(redisHost, redisPort, timeoutMillis));
            server.start();
        } catch (Exception e) {
            System.err.println("Failed to start light-redis panel: " + e.getMessage());
            client.close();
            System.exit(1);
            return;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            client.close();
        }, "light-redis-panel-shutdown"));

        String url = "http://127.0.0.1:" + httpPort;
        System.out.println("light-redis panel: " + url);
        System.out.println("redis server:     " + redisHost + ":" + redisPort);
        System.out.println("Press Ctrl+C to stop.");

        if (open) {
            openBrowser(url);
        }

        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (Exception e) {
            System.err.println("Could not open browser automatically: " + e.getMessage());
        }
    }
}
