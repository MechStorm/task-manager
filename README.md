# Task Manager

A Spring Boot backend for managing tasks and users: a REST API with no frontend, JWT authentication and role separation (`USER` / `ADMIN`), request validation, schema migrations via Liquibase and consistent error handling.

## Domain model

**User** — `id` (UUID), `name`, `email` (unique), `password` (hashed with BCrypt before it is stored), `role` (`USER` / `ADMIN`), `createdAt`/`updatedAt`, `version`.

**Task** — `id` (UUID), `title`, `status` (`TO_DO` / `IN_PROGRESS` / `DONE`), `deadline` (optional), `owner` (a reference to a `User`, may be `null`), `createdAt`/`updatedAt`, `version`.

## API

Base URL: `http://localhost:8080`. Bodies are JSON. Every `PUT` is partial: a field that is not sent stays unchanged.

### Authentication

Only `/api/auth/**`, the API docs (`/swagger-ui/**`, `/v3/api-docs/**`) plus `/actuator/health`, `/actuator/info` and `/actuator/prometheus` are public. Everything else requires a header:

```
Authorization: Bearer <token>
```

| Method | Path | Body | Success | Description |
|---|---|---|---|---|
| POST | `/api/auth/register` | `{ name, email, password }` | `201` + `Location` | Self-registration, always the `USER` role |
| POST | `/api/auth/login` | `{ email, password }` | `200` | Returns `{ "token": "<JWT>" }` |

A token lives 30 minutes (configurable through `JWT_ACCESS_TOKEN_MINUTES`) and no session is created — authentication is fully stateless. The `Bearer` scheme is case-sensitive.

The first administrator is created on startup from `app.security.users` — by default `admin@example.com` / `adminqwerty`. Restarting the application does not create duplicates.

### Tasks

| Method | Path | Body | Success | Who may | Description |
|---|---|---|---|---|---|
| POST | `/api/tasks` | `{ title, userId?, deadline? }` | `201` + `Location` | any authenticated | Create a task (`userId` pointing at someone else — admin only) |
| GET | `/api/tasks` | — | `200` | any authenticated | List all tasks |
| GET | `/api/tasks/{id}` | — | `200` | any authenticated | A single task |
| PUT | `/api/tasks/{id}` | `{ title?, userId?, deadline?, status? }` | `200` | the owner, an admin, or anyone for a free task | Partial update |
| POST | `/api/tasks/{id}/release` | — | `200` | the owner or an admin | Release the task back to the shared pool |
| DELETE | `/api/tasks/{id}` | — | `204` | `ADMIN` only | Delete a task |

Response (`TaskResponse`): `id`, `title`, `status`, `deadline`, `ownerId`, `ownerName`, `createdAt`, `updatedAt`. Fields whose value is `null` (for example `ownerId` on an unassigned task) are omitted from the response rather than sent as `null`.

### Users

| Method | Path | Body | Success | Who may | Description |
|---|---|---|---|---|---|
| POST | `/api/users` | `{ name, email, password, role? }` | `201` + `Location` | `ADMIN` only | Create a user, including an admin (`role: "ADMIN"`) |
| GET | `/api/users` | — | `200` | any authenticated | List users |
| GET | `/api/users/{id}` | — | `200` | any authenticated | A single user |
| PUT | `/api/users/{id}` | `{ name?, password? }` | `200` | the user themselves or an admin | Partial update |
| DELETE | `/api/users/{id}` | — | `204` | `ADMIN` only | Delete (clears the owner on their active tasks) |

Response (`UserResponse`): `id`, `name`, `email`, `createdAt`, `updatedAt`. The password, the role and the task list are never returned.

### Request validation (in short)

- Task `title`: required on creation, 1–255 characters.
- `deadline`: if given, it cannot be in the past.
- User `name`: required on creation, 1–255 characters.
- `email`: required, must be a valid email address, unique.
- `password`: 8–16 characters.

## Error handling

Every error comes back as a `ProblemDetail`:

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Task with id 3f2a1c4e-0000-0000-0000-000000000000 not found",
  "instance": "/api/tasks/3f2a1c4e-0000-0000-0000-000000000000"
}
```

Body validation errors additionally carry a per-field `errors` map:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/auth/register",
  "errors": {
    "email": "Email must be a valid email address",
    "password": "Password must be between 8 and 16 characters"
  }
}
```

| Code | When |
|---|---|
| `400` | Invalid body, a deadline in the past, a forbidden status transition, moving to `IN_PROGRESS`/`DONE` without an owner, releasing a finished or already free task, broken JSON, an `id` that is not a UUID |
| `401` | No `Authorization` header; the token is expired, forged, has no expiry, or belongs to a deleted user; a wrong email/password pair on `/api/auth/login` |
| `403` | Not enough rights: someone else's task or profile, assigning a task to another user, deleting without the `ADMIN` role |
| `404` | No task or user with that `id` |
| `409` | The `email` is already taken; the task was changed concurrently by someone else (optimistic locking on `version`) |
| `500` | An unexpected failure. Only `"Internal Server Error"` goes out — no exception message, no stack trace |

## Running locally

### Requirements

- JDK 21
- Maven
- Docker + Docker Compose

### 1. Start the database

```bash
docker compose up -d
```

Starts Postgres 16 in the `todo_list_app_db` container, database `todo_list_db` (user/password: `postgres`/`postgres`), with port `5432` published to the host.

### 2. Run the application

```bash
mvn spring-boot:run
```

On startup Liquibase applies every migration from `src/main/resources/db/changelog/` — there is no need to create tables by hand. The application comes up on `http://localhost:8080`.

### 3. Explore the API in Swagger UI

Open `http://localhost:8080/swagger-ui.html`; the raw OpenAPI spec is at `/v3/api-docs`. Both are public.

To call protected endpoints:

1. Run `POST /api/auth/register`, then `POST /api/auth/login` — their example bodies match, so both work as is. For admin-only endpoints log in as the administrator instead.
2. Copy `token` from the response, press **Authorize** and paste it **without** the `Bearer` prefix.

The token survives page reloads until it expires.

### Environment variables (optional)

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | Application port |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/todo_list_db` | Database connection string |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Database password |
| `JWT_SECRET` | a demo value | Token signing key, at least 32 characters |
| `JWT_ISSUER` | `task-manager` | Token issuer, verified while parsing |
| `JWT_ACCESS_TOKEN_MINUTES` | `30` | Token lifetime |
| `ADMIN_EMAIL` | `admin@example.com` | Email of the administrator created on startup |
| `ADMIN_PASSWORD` | `adminqwerty` | Password of that administrator |
| `SWAGGER_ENABLED` | `true` | Serve Swagger UI and `/v3/api-docs` |

`JWT_SECRET` and `ADMIN_PASSWORD` must be overridden anywhere outside local development: the defaults sit in plain text in `application.yaml`.

### Monitoring

| Path | Access |
|---|---|
| `/actuator/health` | no authentication |
| `/actuator/info` | no authentication |
| `/actuator/prometheus` | no authentication, `text/plain` |
| all other `/actuator/**` | `ADMIN` only (and not exposed — they answer `404`) |

### Tests

```bash
mvn test
```

### Quick smoke check

```bash
# 1. Log in as the administrator (created on startup from app.security.users)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@example.com","password":"adminqwerty"}' | jq -r .token)

# 2. Create a task
curl -X POST http://localhost:8080/api/tasks \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"Buy groceries"}'

# 3. List tasks
curl http://localhost:8080/api/tasks -H "Authorization: Bearer $TOKEN"
```

## Checking the endpoints in Postman

### Environment setup

### Step 1. Log in as the administrator

```
POST {{baseUrl}}/api/auth/login

{
  "email": "admin@example.com",
  "password": "adminqwerty"
}
```
→ `200 OK`, `{ "token": "eyJhbGciOiJIUzI1NiJ9..." }`

```javascript
pm.test("200 OK", () => pm.response.to.have.status(200));
pm.test("a token is returned", () => pm.expect(pm.response.json().token).to.be.a("string"));
pm.environment.set("adminToken", pm.response.json().token);
```

### Step 2. Register and log in a regular user

```
POST {{baseUrl}}/api/auth/register

{
  "name": "Alice",
  "email": "alice@example.com",
  "password": "password123"
}
```
→ `201 Created`, `Location: /api/users/<id>`
```json
{
  "id": "4b3ba2e1-...",
  "name": "Alice",
  "email": "alice@example.com",
  "createdAt": "2026-09-10T12:00:00Z",
  "updatedAt": "2026-09-10T12:00:00Z"
}
```

### Step 3. Create a task in the shared pool

```
POST {{baseUrl}}/api/tasks
Authorization: Bearer {{userToken}}

{
  "title": "Buy groceries"
}
```
→ `201 Created`, `Location: /api/tasks/<id>`
```json
{
  "id": "3f2a1c4e-...",
  "title": "Buy groceries",
  "status": "TO_DO",
  "createdAt": "2026-09-10T12:00:00Z",
  "updatedAt": "2026-09-10T12:00:00Z"
}
```

### Step 4. Claim the task and start working on it

```
PUT {{baseUrl}}/api/tasks/{{taskId}}
Authorization: Bearer {{userToken}}

{
  "userId": "{{userId}}",
  "status": "IN_PROGRESS"
}
```
→ `200 OK`, `status: "IN_PROGRESS"`, with `ownerId`/`ownerName` filled in.

### Step 5. Finish the task

```
PUT {{baseUrl}}/api/tasks/{{taskId}}
Authorization: Bearer {{userToken}}

{
  "status": "DONE"
}
```
→ `200 OK`, `status: "DONE"`

Send this before step 4 and you get `400 Bad Request` with `detail: "Can't move task from TO_DO to DONE"` — there is no direct `TO_DO → DONE` transition.

### Step 6. Release the task back to the pool

Move the task back to `IN_PROGRESS` first (`{"status": "IN_PROGRESS"}`), otherwise release runs into the rule about finished tasks.

```
POST {{baseUrl}}/api/tasks/{{taskId}}/release
Authorization: Bearer {{userToken}}
```
→ `200 OK`, `status: "TO_DO"`, and `ownerId` is absent from the response.

### Step 7. Delete the task (admin only)

```
DELETE {{baseUrl}}/api/tasks/{{taskId}}
Authorization: Bearer {{adminToken}}
```
→ `204 No Content` with an empty body. A follow-up `GET {{baseUrl}}/api/tasks/{{taskId}}` → `404 Not Found`.

### Authorization checks: expected `401` and `403`

Every request below must **fail** — that is the check.

| Request | Token | Expected |
|---|---|---|
| `GET {{baseUrl}}/api/tasks` | No Auth | `401`, `detail: "Authentication required: provide a valid Bearer token"` |
| `GET {{baseUrl}}/api/tasks` | `Bearer garbage` | `401` |
| `GET {{baseUrl}}/api/tasks` | `{{userToken}}` without the word `Bearer` | `401` |
| `DELETE {{baseUrl}}/api/tasks/{{taskId}}` | `{{userToken}}` | `403` — only an admin may delete |
| `DELETE {{baseUrl}}/api/users/{{userId}}` | `{{userToken}}` | `403` |
| `POST {{baseUrl}}/api/users` | `{{userToken}}` | `403` — only an admin may create users |
| `POST {{baseUrl}}/api/tasks` with another user's `userId` | `{{userToken}}` | `403`, `detail: "Only admin can assign tasks to other users"` |
| `PUT {{baseUrl}}/api/tasks/{{taskId}}` with `{"title": "..."}`, task owned by someone else | `{{userToken}}` | `403`, `detail: "You can only modify your own or unassigned tasks"` |
| `PUT {{baseUrl}}/api/tasks/{{taskId}}` with `{"title": "..."}`, task is free | `{{userToken}}` | `403`, `detail: "You can only edit tasks you own"` — claim the task first |
| `PUT {{baseUrl}}/api/users/<someone else's id>` with `{"name": "..."}` | `{{userToken}}` | `403`, `detail: "You can only update your own profile"` |

### Validation and error checks

| Request | Body | Expected |
|---|---|---|
| `POST {{baseUrl}}/api/tasks` | `{"title": "   "}` | `400`, `errors.title: "Title can't be blank"` |
| `POST {{baseUrl}}/api/tasks` | `{"title": "x", "deadline": "2020-01-01T00:00:00Z"}` | `400`, `errors.deadline: "Deadline can't be in the past"` |
| `POST {{baseUrl}}/api/tasks` | `{"title": "x", "userId": "<random UUID>"}` as the admin | `404`, `detail: "User not found: <id>"` |
| `PUT {{baseUrl}}/api/tasks/{{taskId}}` | `{"status": "IN_PROGRESS"}` on a free task | `400`, `detail: "Can't move task to IN_PROGRESS because owner is null"` |
| `PUT {{baseUrl}}/api/tasks/{{taskId}}` | `{"status": "CANCELLED"}` | `400`, `detail: "Malformed JSON request"` — no such status exists |
| `POST {{baseUrl}}/api/tasks/{{taskId}}/release` | — on a `DONE` task | `400`, `detail: "Completed task can't be released back to the pool"` |
| `POST {{baseUrl}}/api/tasks/{{taskId}}/release` | — on a free task, as the admin | `400`, `detail: "Task has no owner and is already in the pool"` |
| `GET {{baseUrl}}/api/tasks/not-a-uuid` | — | `400` |
| `GET {{baseUrl}}/api/tasks/<random UUID>` | — | `404`, `detail: "Task with id <id> not found"` |
| `POST {{baseUrl}}/api/auth/register` | `{"name": "", "email": "broken", "password": "short"}` | `400`, `detail: "Request validation failed"` plus `errors` for all three fields |
| `POST {{baseUrl}}/api/auth/register` | the email of an existing user | `409`, `detail: "Email already in use"` |
| `POST {{baseUrl}}/api/auth/login` | correct email, wrong password | `401`, `detail: "Invalid email or password"` |
| `POST {{baseUrl}}/api/auth/login` | an email that does not exist | `401`, `detail: "Invalid email or password"` — the same message, so the existence of an account is not leaked |

### Monitoring

| Request | Token | Expected |
|---|---|---|
| `GET {{baseUrl}}/actuator/health` | No Auth | `200`, `{"status":"UP"}` |
| `GET {{baseUrl}}/actuator/info` | No Auth | `200` |
| `GET {{baseUrl}}/actuator/prometheus` | No Auth | `200`, a text payload with metrics such as `jvm_memory_used_bytes` |
| `GET {{baseUrl}}/actuator/metrics` | No Auth | `401` |
| `GET {{baseUrl}}/actuator/metrics` | `{{userToken}}` | `403` |
