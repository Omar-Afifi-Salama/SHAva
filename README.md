# SHAva

**SHAva** is a lightweight web framework for Java. The HTTP core is built entirely on the JDK's built-in `com.sun.net.httpserver` library and brings an annotation-driven, Express.js-style developer experience to plain Java — controllers, dynamic routing, middleware ("filters"), static file serving, and query/body parsing. On top of that sits an annotation-driven **model layer (the M in MVC)**: a mini-ORM with ActiveRecord-style models, a fluent query builder, an automatic schema builder, and first-class SQLite support.

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
  - [The Model Layer](#the-model-layer)
    - [Defining a Model](#defining-a-model)
    - [Persisting: save, delete, find](#persisting-save-delete-find)
    - [The Query Builder](#the-query-builder)
    - [The Schema Builder](#the-schema-builder)
    - [Raw SQL with Database](#raw-sql-with-database)
    - [Wiring it up](#wiring-it-up)
- [Demo Application](#demo-application)
- [Project Structure](#project-structure)
- [Architecture](#architecture)
- [Design Principles](#design-principles)
- [Roadmap](#roadmap)
- [Requirements](#requirements)

---

## Features

**HTTP framework**

- **Annotation-based routing** — `@Controller`, `@Get`, `@Post`, `@Put`, `@Delete`, `@Patch`, `@Options`
- **Dynamic path parameters** — define routes like `/:id` and read them via `req.getPathParam("id")`
- **Wildcard routes** — use `*` in a path to match anything after it
- **Filter / middleware pipeline** — class-level and method-level `@Use` filters, executed as an "onion" chain that can short-circuit requests
- **Rich `Request` object** — headers, cookies, query params, and form-encoded body params, all null-safe with convenience lookups
- **Fluent `Response` object** — `Response.html()`, `.json()`, `.text()`, `.redirect()`, `.error()` static factories plus chainable setters
- **Static file serving** — automatic fallback to the `public/` directory with correct MIME types, cache headers, and directory-traversal protection

**Model / data layer**

- **ActiveRecord-style models** — extend `Model<T>` and get `save()`, `delete()`, `find()`, and `query()` for free
- **Convention over configuration** — `createdAt` maps to `created_at`, class `Note` maps to table `note`; override with `@Table` / `@Id` annotations
- **Fluent query builder** — `where`, `whereIn`, `whereNull`, `orderBy`, `limit`/`offset`, with terminal `get()`, `first()`, `count()`, `exists()`, `delete()`
- **Automatic row mapping** — `AutoMapper` hydrates objects from `ResultSet`s, including SQLite's `0/1` → boolean conversion
- **Schema builder** — define tables in Java (`t.string("title").notNull()`), no SQL migration files
- **Injection-safe by design** — values are always bound via `PreparedStatement`; identifiers and operators are whitelisted/validated
- **SQLite out of the box** — any JDBC `DataSource` works; the demo ships with `sqlite-jdbc`

---

## Quick Start

### 1. Clone and run the demo

```bash
git clone <your-repo-url> shava && cd shava
./run_demo.sh
```

`run_demo.sh` runs Maven (`mvn -q compile exec:java`), which compiles the project, resolves the SQLite JDBC driver, creates `shava.db` on first boot, seeds a demo user, and starts the server on port **3000**.

### 2. Open the app

```
http://localhost:3000
```

Log in with the seeded demo user (**alice** / **password123**) or register a new account.

Try these flows from the demo:

| URL | What it demonstrates |
|---|---|
| `GET /` | Welcome page; redirects to `/notes` when logged in |
| `GET /login` | Login page with a registration form |
| `POST /login` | Session cookie auth; wrong credentials redirect back with an error banner |
| `POST /register` | Creates a `User` through the model layer |
| `POST /logout` | Destroys the session and expires the cookie |
| `GET /notes` | `AuthFilter` + query builder → HTML note cards |
| `POST /notes` | `note.save()` inserts with an auto-increment id |
| `GET /notes/:id` | JSON endpoint, scoped to the logged-in user |
| `DELETE /notes/:id` | Bulk delete via `Query.delete()`, triggered by `fetch()` in `app.js` |
| `GET /css/style.css` | Static file served from `public/` |

### 3. Build your own app

```java
import com.framework.data.Database;
import com.framework.data.Model;
import com.framework.http.Server;
import org.sqlite.SQLiteDataSource;

public class Main {
    public static void main(String[] args) throws Exception {
        Database db = new Database(newDataSource());
        Model.use(db);                       // one-time database setup

        Server server = new Server(3000);
        server.registerControllers(MyController.class);
        server.start();
    }
}
```

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
   │  (controllers may talk to the model layer, see below)
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
- **Filter short-circuiting.** A filter may call `chain.next(req)` to continue the pipeline or return its own `Response` to stop immediately (see `AuthFilter`'s redirect to `/login`).
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
    .setHeader("Set-Cookie", "session=abc123; Path=/; HttpOnly")
    .setBody("{\"created\": true}");   // String or byte[] overload
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
        if (Session.userId(req) != null) {     // logged in (cookie-based session)
            return chain.next(req);            // continue down the pipeline
        }
        return Response.redirect("/login");    // short-circuit: controller never runs
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

### The Model Layer

The model layer (`com.framework.data`) is a mini-ORM: you write plain Java classes, annotate them, and the framework generates all SQL. It is built from a few cooperating pieces:

| Class | Role |
|---|---|
| `Model<T>` | Base class; static + instance convenience methods |
| `EntityMeta` | Cached reflection metadata per model class (table name, id, columns) |
| `AutoMapper` | Turns `ResultSet` rows into model objects |
| `Query<T>` | Fluent query builder |
| `Database` | JDBC wrapper: execute, insert, query, plus ORM operations |
| `Schema` / `TableBuilder` / `Column` | Fluent `CREATE TABLE` builder |
| `NamingUtils` | `camelCase` → `snake_case` conversion + identifier validation |
| `@Table`, `@Id` | Optional annotations (`com.framework.annotations.data`) |

Data flow:

```
Controller
   │  Model.query(Note.class)...get()   /   note.save()
   ▼
Query / Model ──► Database ──► JDBC DataSource (SQLite)
   │                                 │  PreparedStatement + ResultSet
   ▼                                 ▼
EntityMeta (cached reflection)   AutoMapper (row → object)
```

#### Defining a Model

```java
import com.framework.annotations.data.Id;
import com.framework.annotations.data.Table;
import com.framework.data.Model;

@Table("notes")                     // optional: defaults to snake_case class name
public class Note extends Model<Note> {

    @Id                             // marks the primary key (auto-increment by default)
    private int id;
    private int userId;
    private String title;
    private String body;
    private String createdAt;

    public Note() {}                // required: used by AutoMapper to hydrate rows
}
```

Rules and conventions:

- Every field maps to a column with the same name in `snake_case`: `userId` → `user_id`, `createdAt` → `created_at`. No per-column annotations needed.
- Exactly one field must be annotated with `@Id`. With `autoIncrement = true` (the default), the database generates the key and it is written back into the object after insert.
- `static` and `transient` fields are ignored.
- Fields may be `private` — the layer reads/writes them via reflection (`setAccessible(true)`).
- The class name maps to a table by snake-casing it (`Note` → `note`); use `@Table("notes")` when the class name doesn't match the table (e.g. singular/plural or reserved words like `User` → `users`).

#### Persisting: save, delete, find

```java
Note note = new Note(userId, "Shopping list", "Milk, eggs", LocalDateTime.now().toString());
note.save();                        // id is null/0 → INSERT; generated id written back
note.setTitle("Shopping list v2");
note.save();                        // id set → UPDATE ... WHERE id = ?

note.delete();                      // DELETE WHERE id = ? (throws if the id is unset)

// Static helpers
Optional<Note> n = Model.find(Note.class, 1);
Query<Note> q   = Model.query(Note.class);
```

#### The Query Builder

Chain methods accumulate conditions; nothing hits the database until a terminal method is called:

```java
List<Note> notes = Model.query(Note.class)
    .where("userId", userId)                        // shorthand for "="
    .where("title", "LIKE", "%draft%")              // explicit operator
    .whereIn("status", List.of("OPEN", "NEW"))
    .whereNotNull("createdAt")
    .orderByDesc("id")
    .limit(10)
    .get();                                         // List<Note>

Optional<Note> first = Model.query(Note.class).where("id", 1).first();
long count           = Model.query(Note.class).where("userId", userId).count();
boolean exists       = Model.query(Note.class).where("username", "alice").exists();
int rowsDeleted      = Model.query(Note.class).where("archived", true).delete();
```

Supported operators: `=`, `!=`, `<>`, `<`, `<=`, `>`, `>=`, `LIKE`, `ILIKE` (anything else throws). Column names are snake-cased and validated against `^[a-zA-Z_][a-zA-Z0-9_]*$`, so identifiers cannot be used for SQL injection; values are always bound as `?` parameters.

#### The Schema Builder

Define tables in Java instead of SQL files. Column definitions are snake-cased exactly like model fields, so `Schema` and the ORM agree on names automatically:

```java
Schema schema = new Schema(db);

schema.createTableIfNotExists("notes", t -> {
    t.id();                                          // id INTEGER PRIMARY KEY AUTOINCREMENT
    t.integer("userId").notNull();                   // user_id INTEGER NOT NULL
    t.string("title").notNull().unique();            // title VARCHAR(255) NOT NULL UNIQUE
    t.text("body");                                  // body TEXT
    t.bool("archived").defaultValue(false);          // archived BOOLEAN DEFAULT 0
    t.timestamp("createdAt");                        // created_at TIMESTAMP
});

schema.dropTableIfExists("temp");
```

Column types: `id`, `integer`, `longInteger`, `string`, `text`, `bool`, `doubleNumber`, `timestamp`, plus `foreignKey(column, refTable, refColumn)`. Column modifiers: `.notNull()`, `.unique()`, `.defaultValue(value)`.

> **Consistency rule:** `Schema` snake-cases table/column names, and the ORM snake-cases class/field names — so `t.string("title")` + field `title` always line up. But a class named `Note` maps to table `note`, while you may create table `notes`; add `@Table("notes")` to the model (as the demo does) so both sides use the same name.

#### Raw SQL with Database

When you need full control, drop below the ORM:

```java
Database db = ...;   // injected or obtained via Model.use()

db.execute("UPDATE notes SET title = ? WHERE id = ?", "Hi", 1);

List<Note> notes = db.query("SELECT * FROM notes WHERE user_id = ?", Note.class, userId);
Optional<Note> one = db.queryOne("SELECT * FROM notes WHERE id = ?", Note.class, id);
long newId = db.insertAndReturnId("INSERT INTO notes (title) VALUES (?)", "Auto id");
```

`RowMapper<T>` is the functional interface (`T map(ResultSet rs)`) used by `query`; `AutoMapper` is the automatic implementation and is used whenever you pass a `Class<T>` instead.

All `SQLException`s are wrapped into an unchecked `DatabaseException` with the offending SQL attached, and connections are opened/closed per call via try-with-resources — `Database` never holds a connection.

#### Wiring it up

```java
SQLiteDataSource dataSource = new SQLiteDataSource();
dataSource.setUrl("jdbc:sqlite:shava.db");     // file created next to pom.xml
Database db = new Database(dataSource);

Model.use(db);                                 // sets the app-wide default database
createTables(db);                              // Schema.createTableIfNotExists(...)
```

Any `javax.sql.DataSource` works (HikariCP, pgJDBC, MySQL...) — the layer is written against plain JDBC. Note that SQLite-specific behavior like `getGeneratedKeys()` requires a driver that supports it, which `sqlite-jdbc` does.

---

## Demo Application

`src/main/java/com/demo` is a complete self-contained example: a notes app with login.

- `Main.java` — creates the SQLite `Database`, calls `Model.use(db)`, creates the `users` and `notes` tables via `Schema`, seeds the demo user, and boots the server on port **3000**.
- `models/User.java`, `models/Note.java` — model-layer classes (the M).
- `auth/Session.java` — tiny in-memory session store: cookie token → user id.
- `controllers/AuthController.java` — login, registration, logout (passwords are plain text **for demo purposes only**).
- `controllers/NoteController.java` — every route is behind `AuthFilter`; notes are scoped to the logged-in user with `.where("userId", ...)`.
- `util/Html.java` — HTML escaping + timestamp formatting helpers.
- `filters/LoggingFilter.java` — request/response timing logger.
- `filters/AuthFilter.java` — session guard; redirects to `/login`.
- `public/` — stylesheet (`style.css`) and script (`app.js`: note deletion via `fetch`).

Console output looks like:

```
Seeded demo user: alice / password123
Server started on http://localhost:3000
--> [GET] /notes
<-- [302] Completed in 1ms
--> [POST] /notes
<-- [302] Completed in 12ms
```

---

## Project Structure

```
SHAva/
├── pom.xml                             # Maven build (compiler + exec + SQLite driver)
├── run_demo.sh                         # mvn -q compile exec:java
├── docs/                               # proposal, UML diagrams, .shava examples
│   ├── PROPOSAL.md
│   ├── UML.md
│   └── example.shava
├── public/                             # static assets (served automatically)
│   ├── css/style.css
│   └── js/app.js
└── src/
    └── main/java/
        ├── com/framework/              # ← the SHAva framework
        │   ├── annotations/
        │   │   ├── http/               # @Controller, @Get, @Post, @Put,
        │   │   │                       #   @Delete, @Patch, @Options, @Use
        │   │   └── data/               # @Table, @Id
        │   ├── data/                   # ← the model layer (the M)
        │   │   ├── Model.java          # ActiveRecord-style base class
        │   │   ├── Query.java          # fluent query builder
        │   │   ├── Database.java       # JDBC wrapper + ORM operations
        │   │   ├── EntityMeta.java     # cached per-class reflection metadata
        │   │   ├── AutoMapper.java     # ResultSet → object hydration
        │   │   ├── RowMapper.java      # row-mapping functional interface
        │   │   ├── Schema.java         # CREATE TABLE builder
        │   │   ├── TableBuilder.java   # per-table column definitions
        │   │   ├── Column.java         # single column definition
        │   │   ├── NamingUtils.java    # camelCase→snake_case + identifier checks
        │   │   └── DatabaseException.java
        │   ├── http/
        │   │   ├── Server.java         # HttpServer wrapper: parse → dispatch → write
        │   │   ├── Request.java        # null-safe request data + accessors
        │   │   └── Response.java       # fluent response builder + factories
        │   └── routing/
        │       ├── RouteRegistry.java  # reflection scan, path regex, dispatch
        │       ├── Route.java          # method + pattern + handler + filters
        │       ├── Filter.java         # middleware interface
        │       ├── FilterChain.java    # sequential filter execution → controller
        │       └── StaticFileHandler.java
        └── com/demo/                   # ← example application: notes app with login
            ├── Main.java               # DB setup, Model.use, Schema, seed, server
            ├── auth/Session.java       # in-memory cookie sessions
            ├── models/User.java        # @Table("users")
            ├── models/Note.java        # @Table("notes")
            ├── controllers/IndexController.java
            ├── controllers/AuthController.java
            ├── controllers/NoteController.java
            ├── filters/LoggingFilter.java
            ├── filters/AuthFilter.java
            └── util/Html.java
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
   └────────┬────────┘
            │ Model.query(...).get() / entity.save()
   ┌────────▼─────────┐
   │ Model layer      │  com.framework.data
   │ Query · Database │  SQL generation + binding
   │ EntityMeta cache │  reflection metadata (once per class)
   │ AutoMapper       │  ResultSet rows → model objects
   └────────┬─────────┘
            │ JDBC
   ┌────────▼─────────┐
   │ DataSource       │  SQLite (or any JDBC driver)
   └──────────────────┘
```

- **Server** is intentionally the only class touching `com.sun.net.httpserver`; everything else is plain Java, which keeps the core testable and portable.
- **RouteRegistry** owns registration and dispatch; **Route** is an immutable record of a single endpoint; **FilterChain** is created fresh per request so filter state (its position index) is request-specific.
- **StaticFileHandler** is deliberately a fallback — explicit annotated routes always win over files.
- **The model layer is metadata-driven**: `EntityMeta` and `AutoMapper` scan each model class once and cache the result in a `ConcurrentHashMap`, so per-query reflection cost is amortized to almost nothing.
- **`Database` borrows a connection per call** and always closes it; the layer holds no connection state, which makes it safe for the server's thread pool.

## Design Principles

1. **Zero-dependency HTTP core**: the JDK's built-in HTTP server powers the web layer with no third-party JARs; the model layer adds only a JDBC driver (SQLite for the demo).
2. **Convention over configuration**: `public/` for assets, base-path prefixing, automatic 404s, `String` → HTML, and `camelCase` fields → `snake_case` columns.
3. **Fail loud at registration/startup, fail soft at runtime**: a bad controller, filter, or model (missing `@Id`, no no-arg constructor, unsafe identifier) throws immediately; a controller crash becomes a 500 response so the server survives.
4. **Injection-safe by construction**: values are always `PreparedStatement` parameters; identifiers and SQL operators are whitelisted.
5. **Null-defense and immutability** at the HTTP boundary: `Request` and `Response` never expose null maps or null bodies.
6. **Middleware as first-class objects**: everything (auth, logging, CORS) goes through the same `Filter` SPI — there are no special hooks.

---

## Roadmap

- [x] Static asset serving from `public/`
- [x] `@Put`, `@Delete`, `@Patch`, `@Options` routing
- [x] Model layer: `Model`, `Query`, `Database`, `AutoMapper`, `EntityMeta`
- [x] Schema builder (`Schema` / `TableBuilder` / `Column`)
- [x] SQLite support (`sqlite-jdbc`) + demo app with login
- [x] Cookie-based sessions (in-memory, demo-level)
- [ ] `multipart/form-data` + file upload parsing (binary bodies)
- [ ] Configurable custom 404 / 500 handlers (`setNotFoundHandler`, `setErrorHandler`)
- [ ] Framework-level session manager + `Response.setCookie(...)` helpers (currently in the demo)
- [ ] Thread-pool / virtual-thread configuration on the underlying `HttpServer`
- [ ] Built-in CORS + preflight (OPTIONS) handling
- [ ] JSON body parsing (currently only urlencoded)
- [ ] Migrations (versioned `ALTER TABLE` support on top of `Schema`)
- [ ] Relationships (`@HasMany` / `@BelongsTo` style helpers)

## Requirements

- **Java 21 or newer** — the framework uses pattern matching for `instanceof`, records, and text blocks.
- **Maven 3.8+** (or any recent Maven) — `run_demo.sh` is just `mvn -q compile exec:java`. The SQLite JDBC driver and SLF4J are pulled automatically from Maven Central; there is nothing to install by hand.
