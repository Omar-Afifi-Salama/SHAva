package com.demo.controllers;

import com.demo.auth.Session;
import com.demo.filters.LoggingFilter;
import com.framework.annotations.http.Controller;
import com.framework.annotations.http.Get;
import com.framework.annotations.http.Use;
import com.framework.http.Request;
import com.framework.http.Response;

@Controller("/")
@Use(LoggingFilter.class)
public class IndexController {

    @Get("/")
    public Response index(Request req) {
        // Already logged in? Go straight to the notes.
        if (Session.userId(req) != null) {
            return Response.redirect("/notes");
        }

        String html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>SHAva Notes</title>
            <link rel="stylesheet" href="/css/style.css">
        </head>
        <body class="auth-body">
            <main class="auth-card">
                <h1 class="app-logo">SHAva<span>Notes</span></h1>
                <p class="tagline">A tiny notes app running on the SHAva framework.</p>
                <a class="btn btn-primary" href="/login">Log in to your notes</a>
                <p class="hint">Demo user: shady / password123</p>
            </main>
            <script src="/js/app.js"></script>
        </body>
        </html>
        """;

        return Response.html(html);
    }
}
