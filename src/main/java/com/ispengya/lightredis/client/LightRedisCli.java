package com.ispengya.lightredis.client;

import com.ispengya.remoting.netty.NettyRemotingClient;
import com.ispengya.remoting.protocol.RemotingCommand;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@CommandLine.Command(name = "light-redis-cli", mixinStandardHelpOptions = true, version = "0.0.1")
public class LightRedisCli implements Runnable {

    private static final int REQUEST_CODE = 1;

    @CommandLine.Option(names = {"-h", "--host"}, description = "Server host", defaultValue = "127.0.0.1")
    private String host;

    @CommandLine.Option(names = {"-p", "--port"}, description = "Server port", defaultValue = "6379")
    private int port;

    @CommandLine.Option(names = {"-t", "--timeout"}, description = "Request timeout millis", defaultValue = "5000")
    private int timeoutMillis;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new LightRedisCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        String address = host + ":" + port;
        NettyRemotingClient client = new NettyRemotingClient();
        try {
            client.start();
            Runtime.getRuntime().addShutdownHook(new Thread(client::shutdown));
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Connected to " + address + ", type \"quit\" or \"exit\" to close.");
            while (true) {
                System.out.print(host + ":" + port + "> ");
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if ("quit".equalsIgnoreCase(trimmed) || "exit".equalsIgnoreCase(trimmed)) {
                    break;
                }
                try {
                    RemotingCommand request = RemotingCommand.createRequestCommand(REQUEST_CODE, null);
                    request.setBody(trimmed.getBytes(StandardCharsets.UTF_8));
                    RemotingCommand response = client.invokeSync(address, request, timeoutMillis);
                    byte[] body = response.getBody();
                    if (body != null && body.length > 0) {
                        System.out.println(new String(body, StandardCharsets.UTF_8));
                    } else {
                        System.out.println("(empty)");
                    }
                } catch (Exception e) {
                    e.printStackTrace(System.out);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(System.out);
        } finally {
            client.shutdown();
        }
    }
}
