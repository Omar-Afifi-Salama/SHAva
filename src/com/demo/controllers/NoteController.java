package com.demo.controllers;

import com.demo.filters.AuthFilter;
import com.demo.filters.LoggingFilter;
import com.framework.annotations.Controller;
import com.framework.annotations.Get;
import com.framework.annotations.Post;
import com.framework.annotations.Use;
import com.framework.http.Request;
import com.framework.http.Response;

@Controller("/notes")
@Use(LoggingFilter.class) // Applies to all routes in this controller
public class NoteController {

    // GET /notes - HTML Form & List
    @Get("/")
    public Response index(Request req) {
        String html = """
        <!DOCTYPE html>
        <html>
        <head><title>Framework Test App</title></head>
        <link rel="stylesheet" href="/css/style.css">
        <script src="/js/app.js"></script>
        <body style="font-family: sans-serif; padding: 20px;">
            <h1>Notes App (Custom Java Framework)</h1>

            <h3>Create New Note</h3>
            <form action="/notes" method="POST">
                <input type="text" name="title" placeholder="Note Title" required />
                <button type="submit">Submit Note</button>
            </form>

            <h3>Sample Links to Test:</h3>
            <ul>
                <li><a href="/notes/42">Get Note #42 (JSON)</a></li>
                <li><a href="/notes/protected">Protected Route (Fails without secret)</a></li>
                <li><a href="/notes/protected?secret=admin">Protected Route (Passes with secret)</a></li>
            </ul>
        </body>
        </html>
        """;
        return Response.html(html);
    }

    // GET /notes/:id - Path Parameter Extraction & JSON Response
    @Get("/:id")
    public Response show(Request req) {
        String id = req.getPathParam("id");
        String json = String.format(
            "{\"status\": \"success\", \"noteId\": \"%s\", \"content\": \"Sample content for note %s\"}",
            id,
            id
        );
        return Response.json(json);
    }

    // POST /notes - Form Body Handling & Redirect
    @Post("/")
    public Response create(Request req) {
        String noteTitle = req.getBodyParam("title");
        System.out.println("[DB Simulation] Created new note: " + noteTitle);

        // Redirect back to main page after form submission
        return Response.redirect("/notes");
    }

    // GET /notes/protected - Tested with Method-Level AuthFilter
    @Get("/protected")
    @Use(AuthFilter.class)
    public Response protectedEndpoint(Request req) {
        return Response.html("<h1>Secret Vault: You are authorized!</h1>");
    }
}
