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
