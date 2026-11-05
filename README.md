# SHAva

**SHAva** is a lightweight, zero-dependency web framework for Java, built entirely on the JDK's built-in `com.sun.net.httpserver` library. It brings an annotation-driven, Express.js-style developer experience to plain Java — controllers, dynamic routing, middleware ("filters"), static file serving, and query/body parsing — with **no Maven, no Gradle, and no third-party JARs**.

If you can compile Java, you can run a SHAva app.

---

## Table of Contents

- [Features](#features)
- [Quick Start](#quick-start)
- [How It Works](#how-it-works)
- [Usage Guide](#usage-guide)
  - [Defining a Controller](#defining-a-controller)
  - [Routing](#routing)
  - [Request](#request)
  - [Response](#response)
  - [Filters (Middleware)](#filters-middleware)
  - [Static Files](#static-files)
- [Demo Application](#demo-application)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Design Principles](#design-principles)
- [Roadmap](#roadmap)
- [Requirements](#requirements)

---

## Features

- **Annotation-based routing** — `@Controller`, `@Get`, `@Post`, `@Put`, `@Delete`, `@Patch`, `@Options`
- **Dynamic path parameters** — define routes like `/:id` and read them via `req.getPathParam("id")`
- **Wildcard routes** — use `*` in a path to match anything after it
- **Filter / middleware pipeline** — class-level and method-level `@Use` filters, executed as an "onion" chain that can short-circuit requests
- **Rich `Request` object** — headers, cookies, query params, and form-encoded body params, all null-safe with convenience lookups
- **Fluent `Response` object** — `Response.html()`, `.json()`, `.text()`, `.redirect()`, `.error()` static factories plus chainable setters
- **Static file serving** — automatic fallback to the `public/` directory with correct MIME types, cache headers, and directory-traversal protection
- **Pluggable thread pool** — the underlying `HttpServer` can be tuned with any `Executor` (fixed pool, virtual threads, etc.)
- **Zero dependencies** — only the standard JDK is required

---

## Quick Start

### 1. Clone and run the demo

```bash
git clone <your-repo-url> shava && cd shava
./run_demo.sh
```

The script compiles everything into `bin/` and boots the demo Notes app:

```bash
mkdir -p bin
javac -d bin $(find src -name "*.java") && java -cp bin com.demo.Main
```

### 2. Open the app

```
http://localhost:3000/notes
```

Try these endpoints from the demo:

| URL | What it demonstrates |
|---|---|
| `GET /notes` | HTML form + static CSS/JS assets |
| `POST /notes` | Form body parsing (`title=...`) and 302 redirect |
| `GET /notes/42` | Dynamic `:id` path parameter → JSON response |
| `GET /notes/protected` | Filter short-circuits with **401** |
| `GET /notes/protected?secret=admin` | Filter passes, controller runs |
| `GET /css/style.css` | Static file served from `public/` |

### 3. Build your own app

```java
import com.framework.http.Server;

public class Main {
    public static void main(String[] args) throws Exception {
        Server server = new Server(3000);
        server.registerControllers(MyController.class);
        server.start(); // blocks nothing; runs on the JVM's HttpServer
    }
}
```

> **Note:** The server binds during `start()` and keeps running as long as the JVM is alive. The demo's `main` simply returns and the process stays up because the underlying dispatcher thread runs as long as the server does.

---

## How It Works

A request flows through SHAva in a fixed pipeline:

```
HTTP request
   │
   ▼
Server (com.sun.net.httpserver HttpExchange wrapper)
   │  parses path, method, headers, cookies, query params,
   │  and application/x-www-form-urlencoded body into Request
   ▼
RouteRegistry.dispatch(req)
   │  1. regex-match route by method + path,
   │     extract path params into the Request
   │  2. build a fresh FilterChain for the matched route
   ▼
FilterChain (filters in registered order, then controller)
   │  LoggingFilter → AuthFilter → ... → controller method
   ▼
Controller method receives Request, returns Response
   ▼
Server serialises Response (status, headers, body) back to the client
   │
   ▼  (no route matched?)
StaticFileHandler (GET only) — serves from public/ or 403/404
```

Key behaviors:

- **Reflection-based registration.** `RouteRegistry.register()` only accepts classes annotated with `@Controller`. It instantiates the controller once with its default constructor, reads the base path, combines class-level + method-level `@Use` filters, and compiles each route path into a cached regex `Pattern`.
- **Path parameters as capture groups.** `:param` tokens become `([^/]+)` groups; the parameter names are stored alongside the pattern so they can be extracted per-request (e.g., `/notes/42` → `{"id": "42"}`).
- **Ordered matching.** Routes are matched in registration order, so define specific routes before wildcard ones.
- **Filter short-circuiting.** A filter may call `chain.next(req)` to continue the pipeline or return its own `Response` to stop immediately (see `AuthFilter`'s 401).
- **Static fallback.** Only when no annotated route matches a `GET` does `RouteRegistry` fall back to `StaticFileHandler`, and only then does it produce a 404 if nothing is found.

---

## Usage Guide

### Defining a Controller

Controllers are plain classes annotated with `@Controller`. The annotation value is the base path prefix (defaults to `""`). Every route handler takes a single `Request` parameter and returns either `Response` (preferred) or `String` (automatically wrapped as an HTML response).

```java
@Controller("/notes")
@Use(LoggingFilter.class)          // applies to every route in this controller
public class NoteController {

    @Get("/")
    public Response index(Request req) {
        return Response.html("<h1>All Notes</h1>");
    }

    @Get("/:id")
    public Response show(Request req) {
        String id = req.getPathParam("id");
        return Response.json("{\"noteId\": \"" + id + "\"}");
    }

    @Post("/")
    public Response create(Request req) {
        String title = req.getBodyParam("title");
        // ... persist the note ...
        return Response.redirect("/notes");
    }

    @Get("/protected")
    @Use(AuthFilter.class)         // applies only to this route
    public Response protectedEndpoint(Request req) {
        return Response.html("<h1>Secret Vault</h1>");
    }
}
```

Registration happens on the server:

```java
server.registerControllers(NoteController.class, UserController.class);
```

### Routing

SHAva supports all primary HTTP verbs (each annotation is a zero-argument value annotation):

| Annotation | HTTP method |
|---|---|
| `@Get` | `GET` |
| `@Post` | `POST` |
| `@Put` | `PUT` |
| `@Delete` | `DELETE` |
| `@Patch` | `PATCH` |
| `@Options` | `OPTIONS` |

Path syntax (a `@Controller("/notes")` prefix is joined and normalized with each method path):

| Route | Matches | Notes |
|---|---|---|
| `@Get("/")` | `/notes` | trailing slash is trimmed |
| `@Get("/:id")` | `/notes/42` | `req.getPathParam("id")` → `"42"` |
| `@Get("/*")` | `/notes/anything/at/all` | `*` becomes `.*` in the regex |

Order matters: routes are evaluated in the order they were registered, so put `@Get("/protected")` above `@Get("/:id")` if their textual parts could collide.

### Request

`com.framework.http.Request` aggregates everything the server parsed, with null-defense so maps are never `null`:

```java
req.getPath()            // "/notes/42"
req.getMethod()          // "GET" (uppercased)

// Full maps
req.getHeaders()         // Map<String, String>
req.getCookies()         // Map<String, String> (parsed from the Cookie header)
req.getPathParams()      // populated by RouteRegistry after matching
req.getQueryParams()     // parsed from ?k=v&k2=v2
req.getBodyParams()      // parsed from urlencoded form bodies

// Single-key convenience lookups
req.getHeader("User-Agent")
req.getCookie("session")
req.getPathParam("id")
req.getQueryParam("q")
req.getBodyParam("title")

// Chainable mutators for filters to enrich the request
req.setHeader("X-Custom", "value").setCookie("k", "v");
```

Body handling assumes `application/x-www-form-urlencoded` (`key=value&key2=value2`); see the [Roadmap](#roadmap) for JSON/multipart plans.

### Response

`com.framework.http.Response` uses a fluent style plus static factories:

```java
// Static factories
Response.html("<h1>Hi</h1>")     // text/html; charset=UTF-8
Response.json("{\"a\": 1}")      // application/json
Response.text("OK")              // text/plain; charset=UTF-8
Response.redirect("/notes")      // 302 + Location header
Response.error(404, "Not Found"); // minimal HTML error page

// Fluent building
Response res = new Response()
    .setStatusCode(201)
    .setContentType("application/json")
    .setHeader("X-Model", "shava")
    .setBody("{\"created\": true}");   // String or byte[] overload

// Getters used by the server when serialising
res.getStatusCode();  // 200 default
res.getContentType();
res.getHeaders();
res.getBody();        // byte[]
```

Returning a `String` from a controller also works — the framework wraps it with `Response.html(...)`. Any other return type results in a 500 (`Unsupported Controller Return Type`), and a controller exception is caught and converted to a 500 error response — it will not crash the server.

### Filters (Middleware)

Filters implement `com.framework.routing.Filter` (`Response doFilter(Request, FilterChain)`) and are attached with the `@Use` annotation, which accepts an array of filter classes:

- `@Use` on the **class**: runs before every route of that controller.
- `@Use` on a **method**: runs after class filters, only for that route.

Within a filter you have two options:

```java
public class AuthFilter implements Filter {
    @Override
    public Response doFilter(Request req, FilterChain chain) {
        if ("admin".equals(req.getQueryParam("secret"))) {
            return chain.next(req);            // continue down the pipeline
        }
        return Response.error(401, "Unauthorized"); // short-circuit: controller never runs
    }
}
```

A "cross-cutting" filter like `LoggingFilter` wraps the rest of the chain and can observe (or modify) both the incoming request and the outgoing response:

```java
public class LoggingFilter implements Filter {
    @Override
    public Response doFilter(Request req, FilterChain chain) {
        long start = System.currentTimeMillis();
        Response res = chain.next(req);
        System.out.println("<-- [" + res.getStatusCode() + "] in "
            + (System.currentTimeMillis() - start) + "ms");
        return res;
    }
}
```

Filters are instantiated per-controller-registration via their default constructor and shared across requests — keep them stateless or synchronize accordingly.

### Static Files

Any `GET` that matches no route is resolved against the `public/` directory relative to the working directory:

```
public/
├── css/style.css   →  GET /css/style.css   (text/css, Cache-Control: max-age=3600)
└── js/app.js       →  GET /js/app.js       (text/javascript)
```

- MIME types are set from the file extension (css, js, html, png, jpg, gif, svg, ico, json, woff2; unknown → `application/octet-stream`).
- **Directory-traversal protection**: paths are normalized and must remain inside `public/`; escape attempts return **403 Access Denied**.
- If the file doesn't exist, the handler returns `null` and the registry issues the standard **404**.

---

## Demo Application

`src/com/demo` is a complete self-contained example:

- `Main.java` — boots a `Server` on port **3000** and registers `NoteController`.
- `controllers/NoteController.java` — shows `@Controller` base paths, class vs. method filters, an HTML-form `POST` with redirect, a JSON endpoint with `:id`, and a protected route.
- `filters/LoggingFilter.java` — request/response timing logger.
- `filters/AuthFilter.java` — query-token guard with 401 short-circuit.
- `public/` — stylesheet and script served statically.

Console output looks like:

```
Server started on http://localhost:3000
--> [GET] /notes
<-- [200] Completed in 3ms
--> [GET] /notes/protected
<-- [401] Completed in 0ms
```

---

## Project Structure

```
java-project/
├── run_demo.sh                     # one-liner build + run script
├── plan.md                          # planned features (see Roadmap)
├── uml-diagram.md / .pdf            # class diagrams
├── public/                          # static assets (served automatically)
│   ├── css/style.css
│   └── js/app.js
└── src/
    ├── com/framework/               # ← the SHAva framework
    │   ├── annotations/             # @Controller, @Get, @Post, @Put,
    │   │                            # @Delete, @Patch, @Options, @Use
    │   ├── http/
    │   │   ├── Server.java          # HttpServer wrapper: parse → dispatch → write
    │   │   ├── Request.java         # null-safe request data + accessors
    │   │   └── Response.java        # fluent response builder + factories
    │   └── routing/
    │       ├── RouteRegistry.java   # reflection scan, path regex, dispatch
    │       ├── Route.java           # method + pattern + handler + filters
    │       ├── Filter.java          # middleware interface
    │       ├── FilterChain.java     # sequential filter execution → controller
    │       └── StaticFileHandler.java
    └── com/demo/                    # ← example application
        ├── Main.java
        ├── controllers/NoteController.java
        └── filters/{LoggingFilter,AuthFilter}.java
```

---

## Architecture

```
┌─────────────────────────┐
│        Server           │  com.framework.http
│  (HttpExchange façade)  │  parses Request / writes Response
└───────────┬─────────────┘
            │ dispatch(Request)
┌───────────▼─────────────┐
│     RouteRegistry       │  com.framework.routing
│  route list + matcher   │
└───────────┬─────────────┘
            │ FilterChain
   ┌────────┴────────┐
   │ Filter(0..n)    │
   └────────┬────────┘
            │ next(Request) — reflection invoke
   ┌────────▼────────┐
   │  Controller     │  user code returning Response
   └─────────────────┘
```

- **Server** is intentionally the only class touching `com.sun.net.httpserver`; everything else is plain Java, which keeps the core testable and portable.
- **RouteRegistry** owns registration and dispatch; **Route** is an immutable record of a single endpoint; **FilterChain** is created fresh per request so filter state (its position index) request-specific.
- **StaticFileHandler** is deliberately a fallback — explicit annotated routes always win over files.

## Design Principles

1. **Zero-dependency**: the JDK's built-in HTTP server is "good enough" to power a real framework without a project graph of exports.
2. **Convention over configuration**: `public/` for assets, base-path prefixing, automatic 404s, `String` → HTML.
3. **Fail loud at registration, fail soft at runtime**: a bad controller or filter class throws immediately during `registerControllers(...)`, while a controller crash is converted into a 500 response so the server survives.
4. **Null-defense and immutability** at the HTTP boundary: `Request` and `Response` never expose null maps or null bodies.
5. **Middleware as first-class objects**: everything (auth, logging, CORS) goes through the same `Filter` SPI — there are no special hooks.

---

## Roadmap

Tracked in [`plan.md`](plan.md):

- [x] Static asset serving from `public/`
- [x] `@Put`, `@Delete`, `@Patch`, `@Options` routing
- [ ] `multipart/form-data` + file upload parsing (binary bodies)
- [ ] Configurable custom 404 / 500 handlers (`setNotFoundHandler`, `setErrorHandler`)
- [ ] Session manager + `Response.setCookie(...)` helpers (`JSESSIONID`-style in-memory store)
- [ ] Thread-pool / virtual-thread configuration on the underlying `HttpServer`
- [ ] Built-in CORS + preflight (OPTIONS) handling
- [ ] JSON body parsing (currently only urlencoded)

## Requirements

- **Java 16 or newer** — the framework uses pattern matching for `instanceof` (`instanceof Response res`) and modern text blocks, both finalized in recent JDKs.
- No build tool required; `javac` + `java` are enough (see `run_demo.sh`).
