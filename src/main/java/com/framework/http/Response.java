package com.framework.http;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Response {

    private int statusCode = 200;
    private String contentType = "text/html; charset=UTF-8";
    private Map<String, String> headers = new HashMap<>();
    private byte[] body = new byte[0];

    public Response() {}

    public Response(int statusCode, String contentType, byte[] body, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.contentType = contentType;
        this.body = body != null ? body : new byte[0];
        this.headers = headers != null ? headers : new HashMap<>();
    }

    // Getters
    public int getStatusCode() {
        return statusCode;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getBody() {
        return body;
    }

    // Fluent Setters
    public Response setStatusCode(int statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Response setContentType(String contentType) {
        this.contentType = contentType;
        return this;
    }

    // For binary data (images, files)
    public Response setBody(byte[] body) {
        this.body = body != null ? body : new byte[0];
        return this;
    }

    // For text/string data (HTML, JSON, plain text)
    public Response setBody(String body) {
        if (body != null) {
            this.body = body.getBytes(StandardCharsets.UTF_8);
        }
        return this;
    }

    public Response setHeader(String header, String value) {
        this.headers.put(header, value);
        return this;
    }

    // Static Factory Helpers
    public static Response html(String html) {
        return new Response().setContentType("text/html; charset=UTF-8").setBody(html);
    }

    public static Response text(String text) {
        return new Response().setContentType("text/plain; charset=UTF-8").setBody(text);
    }

    public static Response json(String json) {
        return new Response().setContentType("application/json").setBody(json);
    }

    public static Response redirect(String url) {
        return new Response().setStatusCode(302).setHeader("Location", url);
    }

    public static Response error(int code, String message) {
        return new Response()
            .setStatusCode(code)
            .setContentType("text/html; charset=UTF-8")
            .setBody("<h1>" + code + " Error</h1><p>" + message + "</p>");
    }
}
