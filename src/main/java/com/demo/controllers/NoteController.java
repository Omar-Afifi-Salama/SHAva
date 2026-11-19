package com.demo.controllers;

import com.demo.auth.Session;
import com.demo.models.Note;
import com.demo.models.User;
import com.demo.util.Html;
import com.framework.annotations.http.Controller;
import com.framework.annotations.http.Delete;
import com.framework.annotations.http.Get;
import com.framework.annotations.http.Post;
import com.framework.annotations.http.Use;
import com.demo.filters.AuthFilter;
import com.demo.filters.LoggingFilter;
import com.framework.data.Model;
import com.framework.data.Query;
import com.framework.http.Request;
import com.framework.http.Response;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * The notes app. Every route requires a logged-in user (class-level AuthFilter),
 * and every note is scoped to the logged-in user's id.
 */
@Controller("/notes")
@Use({ LoggingFilter.class, AuthFilter.class })
public class NoteController {

    @Get("/")
    public Response index(Request req) {
        int userId = Session.userId(req);
        List<Note> notes = Model.query(Note.class)
            .where("userId", userId)
            .orderByDesc("id")
            .get();

        return Response.html(renderNotesPage(req, notes));
    }

    @Post("/")
    public Response create(Request req) {
        String title = req.getBodyParam("title");
        String body = req.getBodyParam("body");
        if (title == null || title.isBlank()) {
            return Response.redirect("/notes");
        }

        Note note = new Note(
            Session.userId(req),
            title.trim(),
            body != null ? body.trim() : "",
            LocalDateTime.now().toString()
        );
        note.save();

        return Response.redirect("/notes");
    }

    // JSON API: single note, owned by the logged-in user
    @Get("/:id")
    public Response show(Request req) {
        Optional<Note> note = ownedNote(req);
        if (note.isEmpty()) {
            return Response.error(404, "Note not found");
        }

        Note n = note.get();
        return Response.json(
            "{\"status\": \"success\", \"note\": {\"id\": %d, \"title\": \"%s\", \"body\": \"%s\"}}".formatted(
                n.getId(),
                jsonEscape(n.getTitle()),
                jsonEscape(n.getBody())
            )
        );
    }

    // JSON API: delete via fetch() from app.js
    @Delete("/:id")
    public Response destroy(Request req) {
        int deleted = ownedNoteQuery(req).delete();
        if (deleted == 0) {
            return Response.error(404, "Note not found");
        }
        return Response.json("{\"status\": \"deleted\"}");
    }

    // --- Rendering ---

    private String renderNotesPage(Request req, List<Note> notes) {
        StringBuilder cards = new StringBuilder();
        for (Note note : notes) {
            cards.append("""
                <article class="note-card" data-id="%d">
                    <h3>%s</h3>
                    <p>%s</p>
                    <footer>
                        <time>%s</time>
                        <button class="btn btn-danger note-delete" data-id="%d">Delete</button>
                    </footer>
                </article>
                """.formatted(
                note.getId(),
                Html.escape(note.getTitle()),
                Html.escape(note.getBody()),
                Html.escape(Html.formatDate(note.getCreatedAt())),
                note.getId()
            ));
        }

        String username = Model.find(User.class, Session.userId(req))
            .map(User::getUsername)
            .orElse("unknown");

        return """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>My Notes - SHAva Notes</title>
            <link rel="stylesheet" href="/css/style.css">
        </head>
        <body>
            <header class="topbar">
                <span class="app-logo">SHAva<span>Notes</span></span>
                <div class="topbar-user">
                    <span>%s</span>
                    <form method="POST" action="/logout">
                        <button type="submit" class="btn btn-ghost">Log out</button>
                    </form>
                </div>
            </header>

            <main class="container">
                <section class="panel">
                    <h2>New note</h2>
                    <form method="POST" action="/notes" class="stack">
                        <input type="text" name="title" placeholder="Title" required>
                        <textarea name="body" rows="3" placeholder="Write something..."></textarea>
                        <button type="submit" class="btn btn-primary">Save note</button>
                    </form>
                </section>

                <h2 class="section-title">My notes</h2>
                <div class="note-grid">
                    %s
                </div>
                %s
            </main>
            <script src="/js/app.js"></script>
        </body>
        </html>
        """.formatted(
            Html.escape(username),
            cards.toString(),
            notes.isEmpty()
                ? "<p class='empty-state'>No notes yet — create your first one above.</p>"
                : ""
        );
    }

    // --- Helpers ---

    private Query<Note> ownedNoteQuery(Request req) {
        int userId = Session.userId(req);
        int noteId = parseId(req.getPathParam("id"));

        return Model.query(Note.class)
            .where("id", noteId)
            .where("userId", userId);
    }

    private Optional<Note> ownedNote(Request req) {
        return ownedNoteQuery(req).first();
    }

    private int parseId(String rawId) {
        try {
            return Integer.parseInt(rawId);
        } catch (NumberFormatException e) {
            return -1; // never matches a real id
        }
    }

    private String jsonEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
