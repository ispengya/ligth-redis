package com.ispengya.lightredis.panel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 静态资源处理器。
 *
 * <p>从 classpath 的 web 目录读取 index.html、app.js 和 style.css。</p>
 */
public class StaticFileHandler implements HttpHandler {

    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static {
        CONTENT_TYPES.put("html", "text/html; charset=utf-8");
        CONTENT_TYPES.put("css", "text/css; charset=utf-8");
        CONTENT_TYPES.put("js", "application/javascript; charset=utf-8");
        CONTENT_TYPES.put("svg", "image/svg+xml");
        CONTENT_TYPES.put("ico", "image/x-icon");
        CONTENT_TYPES.put("png", "image/png");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod() == null
                    ? ""
                    : exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            if (!"GET".equals(method) && !"HEAD".equals(method)) {
                exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                writeText(exchange, 405, "method not allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                path = "/index.html";
            }
            if (path.contains("..") || path.contains("\\") || path.indexOf('\0') >= 0) {
                writeText(exchange, 400, "bad path");
                return;
            }

            String contentType = CONTENT_TYPES.get(extension(path));
            if (contentType == null) {
                writeText(exchange, 404, "not found");
                return;
            }

            InputStream resource = StaticFileHandler.class.getResourceAsStream("/web" + path);
            if (resource == null) {
                writeText(exchange, 404, "not found");
                return;
            }

            byte[] body;
            try (InputStream input = resource) {
                body = readAll(input);
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            if ("html".equals(extension(path))) {
                exchange.getResponseHeaders().set("Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self'; "
                                + "img-src 'self' data:; connect-src 'self'; object-src 'none'; "
                                + "base-uri 'none'; frame-ancestors 'none'");
            }

            if ("HEAD".equals(method)) {
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    private String extension(String path) {
        int index = path.lastIndexOf('.');
        if (index < 0 || index == path.length() - 1) {
            return "";
        }
        return path.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void writeText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
