package com.framework;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws IOException {
        // 1. Create native HTTP server on port 3000
        HttpServer server = HttpServer.create(new InetSocketAddress(3000), 0);

        // 2. Attach a basic handler to the root path
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String response = "<h1>My Framework is Alive!</h1>";
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(200, response.length());

                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
        });

        // 3. Start the server
        System.out.println("Server started on http://localhost:3000");
        server.start();
    }
}