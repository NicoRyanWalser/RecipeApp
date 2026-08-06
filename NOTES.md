# RecipeApp — Setup Notes & Learning Roadmap

A high-level record of how the backend was set up and wired to the frontend, kept
as a study reference. The most useful column is **"What you'd search"** — knowing
the *name* of a thing is most of the battle when you're starting out.

## The mental model first

A web app is really **three separate programs** that talk over HTTP:

1. **Frontend** — runs in the browser (the React app)
2. **Backend** — a server program that receives requests and holds the logic (Spring Boot)
3. **Database** — stores data permanently (PostgreSQL)

"Setting up the backend" means creating #2 and #3, then connecting all three.
Almost every step below is a consequence of that.

The request flow:

```
Browser form → POST /api/recipes → Vite proxy → Controller → Service → Repository → PostgreSQL
```

---

## The steps

### Phase 1 — Prerequisites (get the tools on your machine)
| Step | Why | What you'd search |
|---|---|---|
| 1. Check/install a modern **JDK** (Java 21) | Spring Boot 4 needs Java 17+; the machine had Java 8 | *"install JDK 21 Windows"*, *"what Java version does Spring Boot need"* |
| 2. Confirm **Docker Desktop** is installed | Easiest way to run PostgreSQL without installing it directly | *"install Docker Desktop Windows"* |

> Maven is not installed separately — Spring projects ship a "wrapper" (`mvnw`)
> that downloads the right Maven version for you. Search: *"maven wrapper"*.

### Phase 2 — Create the backend skeleton
| Step | Why | What you'd search |
|---|---|---|
| 3. Generate a Spring Boot project from **Spring Initializr** (start.spring.io) | Gives a correctly-configured project instead of assembling it by hand | *"spring initializr"* — the starting point every Spring dev uses |
| 4. Pick dependencies: **Web, JPA, PostgreSQL, Validation** | Web = REST endpoints; JPA = database mapping; PostgreSQL = the driver; Validation = input checking | *"spring boot starter web jpa"* |

start.spring.io is the answer to "where do I even begin." Tick boxes, download a
zip, and you have a runnable server.

### Phase 3 — Run the database
| Step | Why | What you'd search |
|---|---|---|
| 5. Write a **`compose.yaml`** describing a Postgres container | Declares the DB name, user, password, and port in one file | *"docker compose postgres"* |
| 6. `docker compose up -d` | Starts Postgres in the background | *"docker compose up detached"* |

### Phase 4 — Write the backend code (the layered pattern)
The convention is **four layers**, each with one job:

| Step | Layer | Its one job | What you'd search |
|---|---|---|---|
| 7. `Recipe.java` | **Entity** | "This class = a database table" | *"spring boot jpa entity example"* |
| 8. `RecipeRepository.java` | **Repository** | Database queries (you just declare an interface!) | *"spring data jpa repository"* |
| 9. `RecipeService.java` | **Service** | Business logic | *"spring boot service layer"* |
| 10. `RecipeController.java` | **Controller** | Maps URLs (`/api/recipes`) to methods | *"spring boot rest controller example"* |
| 11. `RecipeRequest/Response` | **DTOs** | The shape of the JSON in/out | *"spring boot dto vs entity"* |

If you learn *one* thing about Spring, learn this
**Controller → Service → Repository → Entity** chain. It's the backbone of nearly
every Spring app.

### Phase 5 — Connect backend to database
| Step | Why | What you'd search |
|---|---|---|
| 12. Fill in **`application.properties`** with the DB url/user/password | Tells Spring how to reach Postgres | *"spring boot postgresql application.properties"* |
| 13. Set `ddl-auto=update` | Makes Hibernate auto-create tables from the entity classes | *"spring jpa hibernate ddl-auto"* |

### Phase 6 — Connect frontend to backend
| Step | Why | What you'd search |
|---|---|---|
| 14. Add a **proxy** in `vite.config.ts` | Lets the frontend call `/api/...` without CORS errors | *"vite proxy backend api"*, *"what is CORS"* |
| 15. Wire the form's `onSubmit` to **`fetch('/api/recipes', ...)`** | Sends the form data to the backend | *"react form fetch POST example"*, *"react controlled form"* |

### Phase 7 — Run and verify
| Step | Why | What you'd search |
|---|---|---|
| 16. Start DB, then backend (`./mvnw spring-boot:run`), then frontend (`npm run dev`) | The three programs, each in its place | *"run spring boot maven wrapper"* |
| 17. Test the API directly with **curl** or **Postman** | Confirm the backend works *before* blaming the frontend | *"test REST API curl"*, *"Postman tutorial"* |

---

## Phase 8 — From "a recipe is three text boxes" to a real data model

Everything in Phases 1–7 built an app where a recipe was three strings: a name, a
blob of ingredients, a blob of instructions. That's the right place to start and
it hits a wall the moment you want to **combine two recipes into one shopping
list** — because you can't add up prose.

The fix was to stop storing ingredients as text and start storing them as
**relationships**. That one change is what the rest of this phase is about.

### The key insight

> Normalizing ingredients isn't about *validation* — it's that combining becomes a
> **database JOIN on `ingredient_id`** instead of string matching. Once a recipe
> line points at ingredient #47, "carot" is *unrepresentable* — not because you
> rejected the typo, but because there's no text field left to type it into.

Making a bad state **impossible to represent** beats checking for it. That idea
generalizes way past this app.

### The schema
| Step | Why | What you'd search |
|---|---|---|
| 18. `Unit` as a Java **enum**, not a table | A conversion factor is a law of physics, not user data. Rule of thumb: fixed by the outside world + your code names it → enum. Users create it → table | *"java enum with fields constructor"*, *"jpa enumerated string vs ordinal"* |
| 19. `Ingredient` catalog table with a UNIQUE `slug` | One row per real food. The slug ("Olive Oil" → "olive oil") is what stops case/spacing duplicates. **Only the DB can truly enforce uniqueness** — a check-then-insert in Java is a race condition | *"database normalization"*, *"unique constraint vs application check"* |
| 20. `RecipeIngredient` as an **association entity** | A join table that carries extra data (quantity, unit, note). A plain many-to-many can say "this recipe uses tomatoes" but has nowhere to put "800 g of them" | *"jpa manytoone joincolumn owning side"*, *"association entity vs many-to-many"* |
| 21. Steps as `@ElementCollection`, lines as `@Entity` | **The entity-vs-value-object distinction.** A line has identity and points at something outside itself → entity. A step is text owned entirely by its recipe → value | *"jpa elementcollection vs entity"*, *"value object vs entity ddd"* |
| 22. `cascade = ALL` + `orphanRemoval = true` | Cascade propagates *your* operations to children. Orphan removal deletes a child that quietly *fell out of the collection* — which is what makes "edit a recipe and remove an ingredient" actually delete the row | *"jpa cascade vs orphanremoval"* |

### Traps that will bite (all of these did, or nearly did)
| Trap | What happens | What you'd search |
|---|---|---|
| 23. `this.children = newList` on a cascaded collection | Throws *"A collection with cascade='all-delete-orphan' was no longer referenced"* — at flush time, far from the line that caused it. Hibernate tracks the collection **instance** it gave you. Mutate it: `clear()` then `addAll()` | *"a collection with cascade all-delete-orphan was no longer referenced"* |
| 24. `LEFT JOIN FETCH` on two collections at once | `MultipleBagFetchException` **at startup**. Two Lists make an ambiguous cartesian product. Fix: `@BatchSize` instead | *"hibernate MultipleBagFetchException"* |
| 25. The **N+1 select problem** | 1 query for N recipes, then N more as you touch each one's children. Three different fixes, and the cheapest is to **not fetch what you won't display** (a summary DTO + projection query) | *"spring data jpa n+1 problem"*, *"jpa projection dto query"* |
| 26. `LazyInitializationException` | Map entities → DTOs **inside** the `@Transactional` service method. Never return an entity from a controller: Jackson touches a lazy field mid-serialization and you get a 500 with half-written JSON | *"LazyInitializationException spring boot"* |
| 27. `@Valid` missing on a nested list | Nested constraints are **silently never checked**. Validation does not descend into nested objects on its own | *"jakarta validation nested @Valid list"* |
| 28. Spring Boot **4** unbundled Jackson | `spring-boot-starter-webmvc` no longer brings JSON. Add `spring-boot-starter-jackson` — and note it's Jackson **3**, package `tools.jackson.*`, not `com.fasterxml.jackson.*`. Every tutorial online shows the old package | *"spring boot 4 jackson 3 migration"* |
| 29. `ddl-auto=update` is **additive only** | It adds tables/columns; it never drops, renames, or backfills. Restructuring the schema needed `docker compose down -v`. This is the concrete reason migration tools exist | *"hibernate ddl-auto update limitations"*, *"flyway baseline existing schema"* |

### The frontend half
| Step | Why | What you'd search |
|---|---|---|
| 30. One `api/client.ts` — the only file that calls `fetch` | One place to add auth headers, a base URL, or error handling. Also: **`fetch` does not reject on 404 or 500**, only on a failed round trip. Code that only uses `.catch()` treats every 500 as success | *"fetch does not throw on 404"* |
| 31. Draft state ≠ API payload | Quantity is a **string** in the form because that's what an `<input>` holds — and `Number('')` is **0**, not NaN, which would turn an empty box into "0 g salt" instead of "to taste" | *"react controlled input number empty string"* |
| 32. `crypto.randomUUID()` as the React `key`, never the array index | With index keys, deleting row 2 makes React think rows 2 and 3 *changed content* rather than that row 2 was removed — text visibly jumps up a row | *"react key index anti-pattern"* |
| 33. Debounce + `AbortController` on search-as-you-type | Debounce stops 6 requests for 6 keystrokes. AbortController stops the **out-of-order response** race, where a slow "car" reply lands after "carrot" and shows the wrong results. Intermittent, invisible locally, real for users | *"react debounce hook"*, *"abortcontroller race condition fetch"* |
| 34. The **WAI-ARIA combobox pattern** | Building a filterable dropdown by hand means taking on the job `<select>` did for free. The core idea is **virtual focus**: real DOM focus never leaves the input, and `aria-activedescendant` names the highlighted option — otherwise the user couldn't type | *"wai-aria authoring practices combobox"*, *"aria-activedescendant"* |
| 35. `onMouseDown`, not `onClick`, on dropdown options | `mousedown` fires *before* the input blurs. By the time `click` arrives the list has already closed and the click lands on nothing. **The single most common reason a hand-built dropdown "ignores clicks"** | *"dropdown onclick not firing blur mousedown"* |

### The payoff: the aggregation algorithm
`ShoppingListService` is the most interesting file in the app. Four stages —
**bucket** by (ingredient, dimension) → **sum** in base units → **merge**
dimensions using that ingredient's own factors → **format** for humans.

Its governing rule is worth stealing:

> **Never guess, never throw.** When there's no factor to convert 500 ml of milk
> into grams, don't invent a density and don't refuse to produce a list — print
> **both amounts** and let the human resolve it.

*When a computation can't be done correctly, degrade to showing the inputs rather
than inventing an output.* Search: *"graceful degradation"*, *"dimensional analysis unit conversion"*.

Two more habits that showed up repeatedly and are worth naming:
- **Push validation to the boundary to keep the core simple.** Rejecting
  "quantity without a unit" at the DTO means the aggregation code can rely on
  "no unit ⇒ no quantity" and never handle a data-loss case.
- **Derive, don't accept.** The client never sends a `position`; the server uses
  the array index. That deletes an entire category of bug (gaps, duplicates,
  negatives) rather than validating against it.

---

## The two "aha" concepts that make this searchable

- **REST API** — the convention of "URLs + HTTP methods (GET/POST) that return
  JSON." Every backend tutorial assumes this vocabulary. Search: *"what is a REST API"*.
- **ORM / JPA** — the idea that a Java class can *be* a database table so you
  rarely write SQL. Search: *"what is an ORM"*, *"JPA tutorial"*.

Best single search if starting from scratch:
**"spring boot postgres react crud tutorial"** — "CRUD"
(Create/Read/Update/Delete) is the magic keyword that pulls up exactly this kind
of full-stack walkthrough.

---

## How to run it day-to-day

Three things, in order. Use two terminals for the last two.

```bash
# 1. Database (once; stays running in background)
cd server
docker compose up -d

# 2. Backend  (terminal A)  ->  http://localhost:8080
cd server
./mvnw spring-boot:run

# 3. Frontend (terminal B)  ->  http://localhost:5173
cd my-homepage
npm run dev
```

> Gotcha: the terminal's default `java` may still be Java 8. If `./mvnw`
> complains about the Java version, set `JAVA_HOME` to
> `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.

> Gotcha: `Port 8080 was already in use` means a previous backend is still
> running. Find and stop it:
> `Get-NetTCPConnection -LocalPort 8080 -State Listen | %{ Stop-Process -Id $_.OwningProcess -Force }`

**Wiping the database.** `docker compose down -v` deletes the volume and therefore
every recipe. You need it whenever the schema changes in a way `ddl-auto=update`
can't handle — dropped columns, renames, or a new `NOT NULL` column on a table that
already has rows (see step 29). The ~180-ingredient catalog re-seeds itself
automatically on the next boot; your recipes do not come back.

```bash
cd server
docker compose down -v && docker compose up -d
```

> On first boot after a wipe you'll see a wall of `INSERT` statements — that's
> `show-sql=true` logging the catalog seed. The line to look for is
> `Ingredient catalog seeded: 183 new, 183 total`. On every later boot it says
> `0 new`, because the seeder checks per row rather than "is the table empty".

---

## The universal pattern (this stack is just one dialect)

React/Spring/Postgres is one set of vocabulary for a pattern that is identical
across almost every web stack. Learn the pattern once and every stack looks like
the same movie dubbed in a different language.

### The universal round-trip ("user types a string, it gets stored")

```
1. USER types into an input and clicks a button
       |
2. FRONTEND packages the value and sends an HTTP request
   (POST /api/things, body: {"value": "hello"})
       |   <- internet boundary. HTTP + JSON. Universal.
3. BACKEND has a ROUTE that matches that URL+method
       |
4. ROUTE runs a HANDLER function (your logic)
       |
5. HANDLER asks the DATABASE LAYER to save it
       |   <- database boundary. Usually SQL. Universal.
6. DATABASE writes a row and confirms
       |
7. BACKEND sends an HTTP response back -> frontend updates the screen
```

Steps 2 and 5 are the two boundaries that never change: frontend<->backend is
always HTTP; backend<->database is almost always SQL.

### The same jobs, four stacks (read across the rows)

| Universal job | Spring (this app) | Node/Express | Python/Django | Ruby on Rails |
|---|---|---|---|---|
| Receive the HTTP request | `@RestController` | `app.post()` | `urls.py` + view | `routes.rb` + controller |
| Your logic ("handler") | Controller method | route callback | view function | controller action |
| "Class = table" (ORM) | JPA `@Entity` | Prisma model | Django Model | ActiveRecord model |
| Talk to the DB | `Repository` | `prisma.thing.create()` | `Thing.objects.create()` | `Thing.create()` |
| The database | PostgreSQL | PostgreSQL | PostgreSQL | PostgreSQL |
| Config | `application.properties` | `.env` | `settings.py` | `database.yml` |

Postgres doesn't care what language talks to it — it just receives SQL. You could
swap the whole backend for a different language and the database wouldn't notice.

### What changes between stacks
- **Syntax & vocabulary** — ~90% of the difference. Same six concepts, new names.
- **How much is automatic** — Rails/Django = lots of magic; Express = minimal; Spring = in between.
- **Typed vs untyped** — Java/TypeScript check at compile time; Python/JS/Ruby at runtime.

### What almost never changes (transferable knowledge worth investing in)
- **HTTP** — methods (GET/POST/PUT/DELETE), status codes (200/201/400/404/500), JSON bodies
- **REST** — the convention for designing those URLs
- **SQL & relational databases** — tables, rows, keys, queries
- **The ORM idea** — "a class maps to a table"
- **The client/server split** — the frontend must go through the backend to reach the DB (that's where auth, validation, and security live)
