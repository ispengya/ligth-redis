package com.ispengya.lightredis.panel;

import com.ispengya.lightredis.client.LightRedisClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 面板 API 处理器。
 *
 * <p>负责命令执行、状态读取、key 浏览以及本地访问安全校验。</p>
 */
public class PanelApiHandler implements HttpHandler {

    private static final Logger log = LoggerFactory.getLogger(PanelApiHandler.class);
    private static final int MAX_COMMAND_BYTES = 32 * 1024;
    private static final int MAX_NAME_LENGTH = 1024;

    private final LightRedisClient client;
    private final String redisAddress;
    private final int httpPort;

    /**
     * 创建 API 处理器。
     *
     * @param client Redis 客户端
     * @param redisAddress Redis 地址，仅用于展示
     * @param httpPort 面板 HTTP 端口
     */
    public PanelApiHandler(LightRedisClient client, String redisAddress, int httpPort) {
        if (client == null) {
            throw new IllegalArgumentException("Client must not be null");
        }
        if (redisAddress == null || redisAddress.trim().isEmpty()) {
            throw new IllegalArgumentException("Redis address must not be empty");
        }
        this.client = client;
        this.redisAddress = redisAddress;
        this.httpPort = httpPort;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!isAllowedHost(exchange)) {
                writeError(exchange, 403, "forbidden host");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod() == null
                    ? ""
                    : exchange.getRequestMethod().toUpperCase(Locale.ROOT);

            switch (path) {
                case "/api/ping":
                    if (!requireMethod(exchange, method, "GET")) {
                        return;
                    }
                    handlePing(exchange);
                    break;
                case "/api/status":
                    if (!requireMethod(exchange, method, "GET")) {
                        return;
                    }
                    handleStatus(exchange);
                    break;
                case "/api/command":
                    if (!requireMethod(exchange, method, "POST")) {
                        return;
                    }
                    if (!checkPanelRequest(exchange)) {
                        return;
                    }
                    handleCommand(exchange);
                    break;
                case "/api/keys":
                    if (!requireMethod(exchange, method, "GET")) {
                        return;
                    }
                    handleKeys(exchange);
                    break;
                case "/api/key":
                    if ("GET".equals(method)) {
                        handleKeyDetail(exchange);
                    } else if ("DELETE".equals(method)) {
                        if (!checkPanelRequest(exchange)) {
                            return;
                        }
                        handleDeleteKey(exchange);
                    } else {
                        notAllowed(exchange, "GET, DELETE");
                    }
                    break;
                default:
                    writeError(exchange, 404, "not found");
                    break;
            }
        } catch (PayloadTooLargeException e) {
            writeError(exchange, 413, "request body too large");
        } catch (Exception e) {
            log.warn("panel api error", e);
            safeWriteError(exchange, 500, "internal error");
        } finally {
            exchange.close();
        }
    }

    private void handlePing(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        try {
            String reply = client.execute("PING");
            if (isErrorReply(reply) || !"PONG".equals(reply.trim())) {
                writeError(exchange, 503, reply);
                return;
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("reply", reply);
            result.put("redisAddress", redisAddress);
            result.put("elapsedMs", elapsedMillis(start));
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeUnavailable(exchange, e);
        }
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        long start = System.nanoTime();
        try {
            String reply = client.execute("INFO");
            if (isErrorReply(reply)) {
                writeError(exchange, 502, reply);
                return;
            }

            Map<String, String> values = parseKeyValueLines(reply);
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("uptimeInSeconds", parseLong(values.get("uptime_in_seconds")));
            status.put("totalCommands", parseLong(values.get("total_commands_processed")));
            status.put("connectedClients", parseLong(values.get("connected_clients")));
            status.put("keys", parseLong(values.get("keys")));
            status.put("jvmUsedMemoryMb", parseDouble(values.get("jvm_used_memory_mb")));
            status.put("jvmThreads", parseLong(values.get("jvm_threads")));

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("redisAddress", redisAddress);
            result.put("status", status);
            result.put("latencyMs", elapsedMillis(start));
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeUnavailable(exchange, e);
        }
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        byte[] body = readBody(exchange, MAX_COMMAND_BYTES);
        String command = new String(body, StandardCharsets.UTF_8).trim();
        if (command.isEmpty()) {
            writeError(exchange, 400, "command must not be empty");
            return;
        }

        long start = System.nanoTime();
        try {
            String reply = client.execute(command);
            boolean ok = !isErrorReply(reply);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", ok);
            if (ok) {
                result.put("reply", reply);
            } else {
                result.put("error", reply);
            }
            result.put("elapsedMs", elapsedMillis(start));
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeUnavailable(exchange, e);
        }
    }

    private void handleKeys(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String pattern = query.getOrDefault("pattern", "*").trim();
        if (pattern.isEmpty()) {
            pattern = "*";
        }
        if (pattern.length() > MAX_NAME_LENGTH) {
            writeError(exchange, 400, "pattern too long");
            return;
        }

        long start = System.nanoTime();
        try {
            String reply = client.execute("KEYS " + quoteArgument(pattern));
            if (isErrorReply(reply)) {
                writeError(exchange, 400, reply);
                return;
            }

            List<String> keys = new ArrayList<>();
            if (!reply.isEmpty()) {
                for (String key : reply.split("\\r?\\n")) {
                    if (!key.isEmpty()) {
                        keys.add(key);
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("keys", keys);
            result.put("elapsedMs", elapsedMillis(start));
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeUnavailable(exchange, e);
        }
    }

    private void handleKeyDetail(HttpExchange exchange) throws IOException {
        String name = requiredKeyName(exchange);
        if (name == null) {
            return;
        }

        long start = System.nanoTime();
        try {
            String exists = client.execute("EXISTS " + quoteArgument(name));
            if (isErrorReply(exists)) {
                writeError(exchange, 400, exists);
                return;
            }
            if (!"1".equals(exists.trim())) {
                writeError(exchange, 404, "key not found");
                return;
            }

            String type = client.execute("TYPE " + quoteArgument(name)).trim();
            if (isErrorReply(type)) {
                writeError(exchange, 400, type);
                return;
            }

            Object value = null;
            if ("string".equals(type)) {
                String stringValue = client.execute("GET " + quoteArgument(name));
                if (isErrorReply(stringValue)) {
                    writeError(exchange, 400, stringValue);
                    return;
                }
                value = stringValue;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("key", name);
            result.put("type", type);
            result.put("value", value);
            result.put("elapsedMs", elapsedMillis(start));
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeUnavailable(exchange, e);
        }
    }

    private void handleDeleteKey(HttpExchange exchange) throws IOException {
        String name = requiredKeyName(exchange);
        if (name == null) {
            return;
        }

        long start = System.nanoTime();
        try {
            String reply = client.execute("DEL " + quoteArgument(name));
            if (isErrorReply(reply)) {
                writeError(exchange, 400, reply);
                return;
            }

            long deleted;
            try {
                deleted = Long.parseLong(reply.trim());
            } catch (NumberFormatException e) {
                deleted = 0L;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("deleted", deleted);
            result.put("elapsedMs", elapsedMillis(start));
            writeJson(exchange, 200, result);
        } catch (Exception e) {
            writeUnavailable(exchange, e);
        }
    }

    /**
     * 将参数包装成服务端可解析的形式。
     *
     * <p>包含空白、引号或反斜杠时使用双引号包裹，并转义双引号和反斜杠。</p>
     *
     * @param value 原始参数
     * @return 可直接拼接到命令中的参数
     */
    private String quoteArgument(String value) {
        boolean needsQuotes = value.isEmpty();
        for (int i = 0; i < value.length() && !needsQuotes; i++) {
            char current = value.charAt(i);
            if (Character.isWhitespace(current)
                    || current == '"'
                    || current == '\''
                    || current == '\\') {
                needsQuotes = true;
            }
        }
        if (!needsQuotes) {
            return value;
        }

        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' || current == '\\') {
                result.append('\\');
            }
            result.append(current);
        }
        result.append('"');
        return result.toString();
    }

    private String requiredKeyName(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String name = query.get("name");
        if (name == null || name.isEmpty()) {
            writeError(exchange, 400, "missing key name");
            return null;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            writeError(exchange, 400, "key name too long");
            return null;
        }
        return name;
    }

    private boolean requireMethod(HttpExchange exchange, String actual, String expected) throws IOException {
        if (expected.equals(actual)) {
            return true;
        }
        exchange.getResponseHeaders().set("Allow", expected);
        writeError(exchange, 405, "method not allowed");
        return false;
    }

    private void notAllowed(HttpExchange exchange, String allow) throws IOException {
        exchange.getResponseHeaders().set("Allow", allow);
        writeError(exchange, 405, "method not allowed");
    }

    private boolean checkPanelRequest(HttpExchange exchange) throws IOException {
        String panelHeader = exchange.getRequestHeaders().getFirst("X-Light-Redis-Panel");
        if (!"1".equals(panelHeader)) {
            writeError(exchange, 403, "missing panel header");
            return false;
        }
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin != null && !isAllowedOrigin(origin)) {
            writeError(exchange, 403, "forbidden origin");
            return false;
        }
        return true;
    }

    private boolean isAllowedHost(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        String lower = host.trim().toLowerCase(Locale.ROOT);
        return lower.equals("127.0.0.1")
                || lower.equals("localhost")
                || lower.equals("[::1]")
                || lower.equals("127.0.0.1:" + httpPort)
                || lower.equals("localhost:" + httpPort)
                || lower.equals("[::1]:" + httpPort);
    }

    private boolean isAllowedOrigin(String origin) {
        String lower = origin.trim().toLowerCase(Locale.ROOT);
        return lower.equals("http://127.0.0.1")
                || lower.equals("http://localhost")
                || lower.equals("http://[::1]")
                || lower.equals("http://127.0.0.1:" + httpPort)
                || lower.equals("http://localhost:" + httpPort)
                || lower.equals("http://[::1]:" + httpPort);
    }

    private byte[] readBody(HttpExchange exchange, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = exchange.getRequestBody()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new PayloadTooLargeException();
                }
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return result;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            String value = index >= 0 ? pair.substring(index + 1) : "";
            try {
                result.put(URLDecoder.decode(key, StandardCharsets.UTF_8.name()),
                        URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
            } catch (UnsupportedEncodingException | IllegalArgumentException e) {
                log.debug("ignore malformed query pair: {}", pair);
            }
        }
        return result;
    }

    private Map<String, String> parseKeyValueLines(String text) {
        Map<String, String> result = new HashMap<>();
        for (String line : text.split("\\r?\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int index = line.indexOf(':');
            if (index > 0) {
                result.put(line.substring(0, index), line.substring(index + 1));
            }
        }
        return result;
    }

    private boolean isErrorReply(String reply) {
        if (reply == null) {
            return true;
        }
        return reply.startsWith("ERR ")
                || reply.startsWith("WRONGTYPE ")
                || reply.startsWith("NOAUTH ")
                || reply.startsWith("READONLY ");
    }

    private double elapsedMillis(long startNanos) {
        double millis = (System.nanoTime() - startNanos) / 1_000_000.0;
        return Math.round(millis * 100.0) / 100.0;
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void writeJson(HttpExchange exchange, int status, Object value) throws IOException {
        byte[] body = PanelJson.write(value).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void writeError(HttpExchange exchange, int status, String message) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", false);
        result.put("error", message);
        writeJson(exchange, status, result);
    }

    private void writeUnavailable(HttpExchange exchange, Exception exception) throws IOException {
        String message = exception.getMessage();
        writeError(exchange, 503, message == null ? exception.getClass().getSimpleName() : message);
    }

    private void safeWriteError(HttpExchange exchange, int status, String message) {
        try {
            writeError(exchange, status, message);
        } catch (IOException e) {
            log.debug("failed to write error response", e);
        }
    }

    /**
     * 请求体超过限制时抛出。
     */
    private static class PayloadTooLargeException extends IOException {

        private static final long serialVersionUID = 1L;
    }
}
