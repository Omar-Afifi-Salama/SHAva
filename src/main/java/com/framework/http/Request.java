package com.framework.http;

import java.util.HashMap;
import java.util.Map;

public class Request {

    private final String path;
    private final String method;
    private final Map<String, String> headers;
    private final Map<String, String> cookies;
    private Map<String, String> pathParams;
    private Map<String, String> queryParams;
    private Map<String, String> bodyParams;

    // Primary Constructor with Null Defense
    public Request(
        String path,
        String method,
        Map<String, String> headers,
        Map<String, String> cookies,
        Map<String, String> pathParams,
        Map<String, String> queryParams,
        Map<String, String> bodyParams
    ) {
        this.path = path != null ? path : "/";
        this.method = method != null ? method.toUpperCase() : "GET";

        // Ensure internal maps are never null
        this.headers = headers != null ? headers : new HashMap<>();
        this.cookies = cookies != null ? cookies : new HashMap<>();
        this.pathParams = pathParams != null ? pathParams : new HashMap<>();
        this.queryParams = queryParams != null ? queryParams : new HashMap<>();
        this.bodyParams = bodyParams != null ? bodyParams : new HashMap<>();
    }

    // Convenience Constructor
    public Request(String path, String method) {
        this(path, method, null, null, null, null, null);
    }

    // --- Core Getters ---
    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getCookies() {
        return cookies;
    }

    public Map<String, String> getPathParams() {
        return pathParams;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public Map<String, String> getBodyParams() {
        return bodyParams;
    }

    // --- Individual Key Lookups (Crucial for clean controllers!) ---
    public String getHeader(String key) {
        return headers.get(key);
    }

    public String getCookie(String key) {
        return cookies.get(key);
    }

    public String getPathParam(String key) {
        return pathParams.get(key);
    }

    public String getQueryParam(String key) {
        return queryParams.get(key);
    }

    public String getBodyParam(String key) {
        return bodyParams.get(key);
    }

    // --- Setters / Mutations ---
    public Request setHeader(String header, String value) {
        this.headers.put(header, value);
        return this;
    }

    public Request setCookie(String key, String value) {
        this.cookies.put(key, value);
        return this;
    }

    public Request setPathParams(Map<String, String> pathParams) {
        if (pathParams != null) this.pathParams = pathParams;
        return this;
    }

    public Request setQueryParams(Map<String, String> queryParams) {
        if (queryParams != null) this.queryParams = queryParams;
        return this;
    }

    public Request setBodyParams(Map<String, String> bodyParams) {
        if (bodyParams != null) this.bodyParams = bodyParams;
        return this;
    }
}
