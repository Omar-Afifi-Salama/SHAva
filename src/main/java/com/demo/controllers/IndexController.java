package com.demo.controllers;

import com.demo.filters.LoggingFilter;
import com.framework.annotations.Controller;
import com.framework.annotations.Get;
import com.framework.annotations.Use;
import com.framework.http.Request;
import com.framework.http.Response;

@Controller("/")
@Use(LoggingFilter.class)
public class IndexController {

    @Get("/")
    public Response index(Request req) {
        String html = """
         <!DOCTYPE html>
        <html>
        <head><title>Framework Test App</title></head>
        <link rel="stylesheet" href="/css/style.css">
        <script src="/js/app.js"></script>
        <body style="font-family: sans-serif; padding: 20px;">
            <h1 style="font-size: 32px">Welcome to SHAva</h1>
        </body>
        </html>
        """;

        return Response.html(html);
    }
}
