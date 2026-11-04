package com.framework.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Server {

    private final int port;

    // private RouteRegistry registry;

    public Server(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(this.port), 0);

        server.createContext("/", this::handleExchange);

        System.out.println("Server started on http://localhost:" + this.port);
        server.start();
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        // base info
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        // extracting headers
        Map<String, String> headers = parseHeaders(exchange.getRequestHeaders());

        // extracting cookies
        Map<String, String> cookies = parseInput(headers.get("Cookie"), ";");

        // query params
        String rawQuery = exchange.getRequestURI().getQuery();
        Map<String, String> queryParams = parseInput(rawQuery, "&");

        // extracting body params
        InputStream inputStream = exchange.getRequestBody();
        String rawBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> bodyParams = parseInput(rawBody, "&");

        Request req = new Request(path, method, headers, cookies, null, queryParams, bodyParams);

        // Response object
    }

    private Map<String, String> parseInput(String rawString, String pairDelimiter) {
        return parseInput(rawString, pairDelimiter, "=");
    }

    private Map<String, String> parseInput(
        String rawString,
        String pairDelimiter,
        String keyValueSeparator
    ) {
        Map<String, String> output = new HashMap<>();

        if (rawString == null || rawString.isBlank()) {
            return output;
        }

        String[] brokenUpRawString = rawString.split(pairDelimiter);

        for (String pair : brokenUpRawString) {
            String[] brokenUpPair = pair.split(keyValueSeparator, 2);

            String key = URLDecoder.decode(brokenUpPair[0].trim(), StandardCharsets.UTF_8);
            String value =
                brokenUpPair.length > 1
                    ? URLDecoder.decode(brokenUpPair[1].trim(), StandardCharsets.UTF_8)
                    : "";

            if (!key.isBlank() && !value.isBlank()) {
                output.put(key, value);
            }
        }

        return output;
    }

    private Map<String, String> parseHeaders(Map<String, List<String>> headers) {
        Map<String, String> output = new HashMap<>();
        for (String headerKey : headers.keySet()) {
            List<String> values = headers.get(headerKey);
            output.put(headerKey, String.join(", ", values));
        }
        return output;
    }
}
