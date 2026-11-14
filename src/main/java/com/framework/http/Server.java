package com.framework.http;

import com.framework.routing.RouteRegistry;
import com.sun.net.httpserver.HttpExchange;
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
    private final RouteRegistry registry = new RouteRegistry();

    public Server(int port) {
        this.port = port;
    }

    public void registerControllers(Class<?>... controllerClasses) {
        this.registry.register(controllerClasses);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(this.port), 0);

        server.createContext("/", this::handleExchange);

        System.out.println("Server started on http://localhost:" + this.port);
        server.start();
    }

    private void handleExchange(HttpExchange exchange) throws IOException {
        try {
            // Base HTTP Request info
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            // Extract headers
            Map<String, String> headers = parseHeaders(exchange.getRequestHeaders());

            // Extract cookies (case-insensitive lookup via getFirst)
            String rawCookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            Map<String, String> cookies = parseInput(rawCookieHeader, ";");

            // Query parameters
            String rawQuery = exchange.getRequestURI().getQuery();
            Map<String, String> queryParams = parseInput(rawQuery, "&");

            // Body parameters (POST/PUT form data)
            InputStream inputStream = exchange.getRequestBody();
            String rawBody = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> bodyParams = parseInput(rawBody, "&");

            // Instantiate Request Object
            Request req = new Request(
                path,
                method,
                headers,
                cookies,
                null,
                queryParams,
                bodyParams
            );

            // Dispatch to RouteRegistry (Executes FilterChain then Controller)
            Response res = registry.dispatch(req);

            // Serialize Response back to HttpExchange
            writeResponse(exchange, res);
        } catch (Exception e) {
            // Emergency fallback error response
            byte[] errorBytes = ("Server Error: " + e.getMessage()).getBytes(
                StandardCharsets.UTF_8
            );
            exchange.sendResponseHeaders(500, errorBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errorBytes);
            }
        }
    }

    private void writeResponse(HttpExchange exchange, Response res) throws IOException {
        // Set HTTP Response Headers
        if (res.getHeaders() != null) {
            for (Map.Entry<String, String> header : res.getHeaders().entrySet()) {
                exchange.getResponseHeaders().set(header.getKey(), header.getValue());
            }
        }

        byte[] bodyBytes = res.getBody() != null ? res.getBody() : new byte[0];

        // Send status code and body length
        exchange.sendResponseHeaders(res.getStatusCode(), bodyBytes.length);

        // Write response payload
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bodyBytes);
        }
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

            if (!key.isBlank()) {
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
