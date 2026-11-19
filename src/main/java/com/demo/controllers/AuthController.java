package com.demo.controllers;

import com.demo.auth.Session;
import com.framework.annotations.http.Controller;
import com.framework.annotations.http.Get;
import com.framework.annotations.http.Post;
import com.framework.http.Request;
import com.framework.http.Response;
import com.framework.data.Model;
import com.demo.models.User;
import java.util.Optional;

/**
 * Login, registration and logout. Demo app only: passwords are
 * stored and compared as plain text, so do not copy this pattern.
 */
@Controller("/")
public class AuthController {

    @Get("/login")
    public Response loginPage(Request req) {
        boolean hasError = "1".equals(req.getQueryParam("error"));

        String html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Login - SHAva Notes</title>
            <link rel="stylesheet" href="/css/style.css">
        </head>
        <body class="auth-body">
            <main class="auth-card">
                <h1 class="app-logo">SHAva<span>Notes</span></h1>

                %s

                <h2>Log in</h2>
                <form method="POST" action="/login" class="stack">
                    <input type="text" name="username" placeholder="Username" required autofocus>
                    <input type="password" name="password" placeholder="Password" required>
                    <button type="submit" class="btn btn-primary">Log in</button>
                </form>

                <h2>Or create an account</h2>
                <form method="POST" action="/register" class="stack">
                    <input type="text" name="username" placeholder="Username" required>
                    <input type="password" name="password" placeholder="Password" required>
                    <button type="submit" class="btn btn-secondary">Sign up</button>
                </form>
            </main>
            <script src="/js/app.js"></script>
        </body>
        </html>
        """.formatted(hasError ? "<p class='banner banner-error'>Wrong username or password.</p>" : "");

        return Response.html(html);
    }

    @Post("/login")
    public Response login(Request req) {
        String username = req.getBodyParam("username");
        String password = req.getBodyParam("password");

        Optional<User> user = Model.query(User.class)
            .where("username", username)
            .first();

        if (user.isPresent() && user.get().getPassword().equals(password)) {
            String token = Session.start(user.get().getId());
            return Response.redirect("/notes")
                .setHeader("Set-Cookie", Session.cookie(token));
        }

        return Response.redirect("/login?error=1");
    }

    @Post("/register")
    public Response register(Request req) {
        String username = req.getBodyParam("username");
        String password = req.getBodyParam("password");

        boolean valid = username != null && !username.isBlank()
            && password != null && !password.isBlank();

        boolean taken = Model.query(User.class)
            .where("username", username)
            .exists();

        if (!valid || taken) {
            return Response.redirect("/login?error=1");
        }

        User user = new User(username, password);
        user.save();

        String token = Session.start(user.getId());
        return Response.redirect("/notes")
            .setHeader("Set-Cookie", Session.cookie(token));
    }

    @Post("/logout")
    public Response logout(Request req) {
        String token = req.getCookie(Session.COOKIE_NAME);
        if (token != null) {
            Session.destroy(token);
        }
        return Response.redirect("/login")
            .setHeader("Set-Cookie", Session.expiredCookie());
    }
}
