package com.framework.routing;

import com.framework.http.Response;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class StaticFileHandler {

    private final String publicDir;

    public StaticFileHandler(String publicDir) {
        this.publicDir = publicDir;
    }

    /**
     * Attempts to resolve a URL path to a file inside publicDir.
     * Returns a Response object if found, or null if no matching file exists.
     */
    public Response serve(String requestPath) {
        try {
            // 1. Normalize and resolve target path
            Path baseDir = Paths.get(publicDir).toAbsolutePath().normalize();
            String relativePath = requestPath.startsWith("/")
                ? requestPath.substring(1)
                : requestPath;
            Path targetPath = baseDir.resolve(relativePath).normalize();

            // 2. SECURITY CHECK: Prevent Directory Traversal Attack
            // Ensures requestPath didn't escape outside the public directory using ../
            if (!targetPath.startsWith(baseDir)) {
                return Response.error(403, "Access Denied");
            }

            File file = targetPath.toFile();

            // 3. Check if file exists and is a readable file (not a folder)
            if (!file.exists() || file.isDirectory()) {
                return null; // File not found; let RouteRegistry issue a 404
            }

            // 4. Read bytes and construct Response
            byte[] fileBytes = Files.readAllBytes(targetPath);
            String mimeType = getMimeType(file.getName());

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", mimeType);
            headers.put("Cache-Control", "max-age=3600"); // Cache for 1 hour

            return new Response(200, mimeType, fileBytes, headers);
        } catch (Exception e) {
            return Response.error(500, "Static File Error: " + e.getMessage());
        }
    }

    /**
     * Determines the correct Content-Type header based on file extension.
     */
    private String getMimeType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "text/javascript";
        if (lower.endsWith(".html")) return "text/html; charset=UTF-8";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }
}
