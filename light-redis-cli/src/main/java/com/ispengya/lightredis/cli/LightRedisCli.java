package com.ispengya.lightredis.cli;

import com.ispengya.lightredis.client.RemotingLightRedisClient;
import picocli.CommandLine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@CommandLine.Command(name = "light-redis-cli", mixinStandardHelpOptions = true, version = "0.0.1")
public class LightRedisCli implements Runnable {

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
        RemotingLightRedisClient client = new RemotingLightRedisClient(host, port, timeoutMillis);
        PrintWriter out = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8), true);
        try {
            client.start();
            Runtime.getRuntime().addShutdownHook(new Thread(client::close));
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            out.println("Connected to " + address + ", type \"quit\" or \"exit\" to close.");
            while (true) {
                out.print(host + ":" + port + "> ");
                out.flush();
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
                    String reply = client.execute(trimmed);
                    if (!reply.isEmpty()) {
                        out.println(reply);
                    } else {
                        out.println("(empty)");
                    }
                } catch (Exception e) {
                    e.printStackTrace(out);
                }
            }
        } catch (IOException e) {
            e.printStackTrace(out);
        } finally {
            out.flush();
            client.close();
        }
    }
}
