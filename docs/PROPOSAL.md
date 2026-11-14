# Project Proposal — SHAva

**Course:** Object-Oriented Programming (Java)
**Team:** [Your Name], [Partner's Name]
**Date:** November 2026

---

## Abstract

SHAva is a full-stack web framework for Java with zero dependencies. It is built entirely on the JDK's built-in `com.sun.net.httpserver` library, so if you can compile Java, you can run a SHAva app. No Maven, no Gradle, no third-party JARs.

The project follows the MVC pattern. The Controller layer routes HTTP requests to plain Java methods using annotations and reflection. The Model layer stores data through a small driver interface that users can implement however they want, backed by an abstract `Entity` base class. The View layer is the interesting part: a custom `.shava` template language that we parse into an AST and transpile to HTML. On top of that come sessions, authentication, and middleware.

Since this is an OOP course, the framework doubles as a catalogue of OOP ideas in practice: annotations and reflection, interfaces, generics, and patterns like Facade, Builder, Chain of Responsibility, Strategy, and Repository.

## Objectives

1. Build a complete MVC framework while keeping the zero-dependency promise.
2. Show how a few OOP techniques (annotations, reflection, interfaces, generics) power every layer.
3. Prove the framework actually works by building a real app on top of it.

## Architecture Overview

```
HTTP request
   │
   ▼
Server ──────────────── parses request, writes response (Facade)
   │
   ▼
FilterChain ─────────── sessions · auth · CORS · logging (Chain of Responsibility)
   │
   ▼
Controller ──────────── @Controller/@Get routing via reflection
   │                    @Required/@MinLength validation via reflection
   ▼
Model ───────────────── abstract Entity + DatabaseDriver<T extends Entity> SPI
   │
   ▼
View ─────────────────── .shava templates → tokenizer → AST → HTML
   │
   ▼
HTTP response ───────── HTML page · JSON object (reflection serializer) · redirect
```

Each stage is its own component with a small interface. User code plugs into the framework without ever touching the HTTP machinery.

## Feature Set

### Controller layer

Controllers are plain classes. You annotate them, the framework finds them with reflection and wires up the routing for you:

```java
@Controller("/tasks")
public class TaskController {

    @Get("/:id")
    public Response show(Request req) {
        return Response.json(driver.findById(req.getPathParam("id")));
    }
}
```

- **Routing** — `@Controller("/base")` marks a class; `@Get`, `@Post`, `@Put`, `@Delete`, `@Patch` mark its methods. A whole REST resource is one readable file.
- **Path parameters** — `:id` segments become regex capture groups at startup and are extracted per request, so handlers get parsed values instead of raw URLs.
- **Filters (middleware)** — a `Filter` wraps the rest of the pipeline. It can pass the request along with `chain.next(req)` or stop it early, like `AuthFilter` returning a 401. Filters attach to a whole controller or a single method with `@Use`.
- **Request and Response** — `Request` carries headers, cookies, query params, and form bodies behind a null-safe API. `Response` has static factories like `Response.html()`, `.json()`, `.redirect()`, plus chainable setters.
- **Error handling** — bad controllers fail at registration; a handler that throws gets converted into an error response so the server never crashes. Custom 404/500 pages plug in through an `ErrorHandler` interface.
- **Static files** — unmatched GET requests fall back to a `public/` folder with correct MIME types and path-traversal protection.

OOP concepts: annotations + reflection, Chain of Responsibility, Facade, Builder.

### View layer

Instead of writing HTML with tags pasted inside it, SHAva pages are written in **`.shava`**, a small template language we designed. Indentation defines the structure, like Python:

```shava
---
title: "My website"
css:
    "/styles/index.css"
    "/styles/homepage.css"
js:
    "/scripts/homepage.js"
---

h1(class: "title") "Hello my name is "
    span(class: "highlight") b ${name}

for task in tasks
    li(class: "task") ${task.title}

if user
    a(href: "/logout") "Logout"
else
    a(href: "/login") "Login"
```

- The YAML block at the top declares the page title and assets; the engine generates the `<head>` from it.
- `each` and `if` are just nodes in the tree, same as elements.
- `${var}` escapes by default (XSS protection). `${!var}` prints raw HTML when you really mean it.
- At startup each template is tokenized and parsed into an AST. Every node type (`ElementNode`, `TextNode`, `VariableNode`, `EachNode`, `IfNode`) knows how to render itself; output is just a tree walk.

OOP concepts: Interpreter + Composite patterns, and building a real tokenizer and recursive-descent parser from scratch.

### Model layer

- **`Entity`** — an abstract base class every stored document inherits from. It carries a shared `id` and convenience methods like `task.save()` and `task.delete()`.
- **`DatabaseDriver<T extends Entity>`** — the heart of the layer. It's a small interface of ~8 CRUD signatures:

    ```java
    public interface DatabaseDriver<T extends Entity> {
        T insert(T entity);
        T findById(String id);
        List<T> findAll();
        T update(T entity);
        boolean delete(String id);
        long count(); // default method
        boolean exists(String id); // default method
        void clear();
    }
    ```

    The framework only knows the signatures, never the implementations. Want a SQL driver, a JSON file, an in-memory map, even a remote REST API? Implement those 8 methods and the framework works with it unchanged. `exists` and `count` are `default` methods built on the others, so a new driver only really needs ~5.

- **Bundled drivers** — `InMemoryDriver` and a JSON-backed `FileDriver` ship with the framework, so it works out of the box with no database.
- **Validation** — annotations like `@Required`, `@MinLength`, and `@Range` sit on entity fields. Reflection checks them before the controller runs, and field-level errors go straight back to the template.

OOP concepts: abstract classes, bounded generics, default methods, Strategy, dependency inversion. The same annotation/reflection trick that powers routing powers the data layer.

### Cross-cutting concerns

- **Sessions & auth** — an in-memory `SessionStore` behind a session cookie, reachable as `req.getSession()`, plus a ready-made `AuthFilter`. Password hashing uses PBKDF2, which is already in the JDK, so the zero-dependency rule survives.
- **Flash messages** — `res.flash("Saved!")` shows a one-time message on the page after a redirect.
- **JSON mapping** — `Response.json(anyObject)` serializes objects to JSON using reflection, so SHAva doubles as a REST API framework.

OOP concepts: encapsulation, pluggable interfaces, Open/Closed principle.

## Task Board: Every Layer in One Application

To prove the framework rather than just describe it, we'll build a multi-user task board entirely on SHAva:

| Feature                                    | Layers exercised                           |
| ------------------------------------------ | ------------------------------------------ |
| Register / login / logout                  | Sessions, AuthFilter, password hashing     |
| Create tasks with inline validation errors | Validation annotations, `.shava` templates |
| Task list per user, survives restarts      | `FileDriver`, file persistence             |
| "Task saved!" banner after redirect        | Flash messages                             |
| JSON endpoint (`GET /api/tasks`)           | Reflection JSON serializer                 |
| Custom 404 page, static CSS/JS             | Error handlers, static serving             |

And because the task board stores data through the `DatabaseDriver` interface, swapping the file driver for something else — say, a custom SQL driver — is a one-line change that touches no application code.
