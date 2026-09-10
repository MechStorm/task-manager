# Task Manager

Backend-приложение на Spring Boot для управления задачами и пользователями: REST API без фронтенда, с валидацией, миграциями схемы через Liquibase и обработкой ошибок.

## Доменная модель

**User** — `id` (UUID), `name`, `email` (уникален), `password` (хешируется через BCrypt перед сохранением), `createdAt`/`updatedAt`.

**Task** — `id` (UUID), `title`, `status` (`TO_DO` / `IN_PROGRESS` / `DONE`), `deadline` (опционально), `owner` (ссылка на `User`, может быть `null`), `createdAt`/`updatedAt`.

### Ключевые бизнес-правила

- Новая задача всегда создаётся со статусом `TO_DO` — независимо от того, указан ли владелец сразу.
- Перевести задачу в `IN_PROGRESS` или `DONE` можно только если у неё есть владелец — либо уже назначенный, либо переданный тем же запросом (`userId` + `status` вместе — сценарий "взять задачу в работу").
- При удалении пользователя: его задачи в `TO_DO`/`IN_PROGRESS` теряют владельца и остаются/переходят в `TO_DO` (общий пул). Задачи в `DONE` не трогаются — сохраняют статус, владелец у них снимается отдельно на уровне БД (`ON DELETE SET NULL`).
- `email` пользователя проверяется на уникальность на уровне сервиса и подстрахован ограничением в БД (на случай гонки параллельных запросов).
- `email` не меняется после регистрации.

## API

Базовый URL: `http://localhost:8080`. Формат тела — JSON. Все `PUT`-запросы частичные: непереданное поле не изменяется.

### Tasks

| Метод | Путь | Тело | Успех | Описание |
|---|---|---|---|---|
| POST | `/api/tasks` | `{ title, userId?, deadline? }` | `201` + `Location` | Создать задачу |
| GET | `/api/tasks` | — | `200` | Список всех задач |
| GET | `/api/tasks/{id}` | — | `200` | Одна задача |
| PUT | `/api/tasks/{id}` | `{ title?, userId?, deadline?, status? }` | `200` | Частичное обновление |
| DELETE | `/api/tasks/{id}` | — | `204` | Удалить задачу |

Ответ (`TaskResponse`): `id`, `title`, `status`, `deadline`, `ownerId`, `ownerName`, `createdAt`, `updatedAt`. Поля со значением `null` (например, `ownerId` у неназначенной задачи) в ответе отсутствуют, а не приходят как `null`.

### Users

| Метод | Путь | Тело | Успех | Описание |
|---|---|---|---|---|
| POST | `/api/users` | `{ name, email, password }` | `201` + `Location` | Регистрация |
| GET | `/api/users` | — | `200` | Список пользователей |
| GET | `/api/users/{id}` | — | `200` | Один пользователь |
| PUT | `/api/users/{id}` | `{ name?, password? }` | `200` | Частичное обновление |
| DELETE | `/api/users/{id}` | — | `204` | Удалить (снимает владельца с его активных задач) |

Ответ (`UserResponse`): `id`, `name`, `email`, `createdAt`, `updatedAt`. Пароль и список задач в ответе никогда не возвращаются.

### Валидация запросов (кратко)

- `title` задачи: обязателен при создании, 1–255 символов.
- `deadline`: если указан — не может быть в прошлом.
- `name` пользователя: обязателен при создании, 1–255 символов.
- `email`: обязателен, должен быть валидным email, уникален.
- `password`: 8–255 символов.
## Обработка ошибок

Все ошибки возвращаются в формате `ProblemDetail` (RFC 7807):

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Task not found: <id>"
}
```

| Код | Когда |
|---|---|
| `400` | Невалидные данные в теле запроса, дедлайн в прошлом, попытка перевести задачу в `IN_PROGRESS`/`DONE` без владельца |
| `404` | Задача или пользователь не найдены по `id` |
| `409` | `email` уже занят |

## Запуск локально

### Требования

- JDK 17
- Maven
- Docker + Docker Compose
### 1. Поднять базу данных

```bash
docker compose up -d
```

Поднимет Postgres 16 в контейнере `todo_list_app_db`, база `todo_list_db` (пользователь/пароль: `postgres`/`postgres`), порт `5432` проброшен на хост.

### 2. Запустить приложение

```bash
mvn spring-boot:run
```

При старте Liquibase автоматически применит все миграции из `src/main/resources/db/changelog/` — создавать таблицы вручную не нужно. Приложение поднимется на `http://localhost:8080`.

### Переменные окружения (опционально)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `SERVER_PORT` | `8080` | Порт приложения |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/todo_list_db` | Строка подключения к БД |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Пользователь БД |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Пароль БД |

### Быстрая проверка

```bash
# Создать пользователя
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com","password":"password123"}'
 
# Создать задачу
curl -X POST http://localhost:8080/api/tasks \
  -H "Content-Type: application/json" \
  -d '{"title":"Buy groceries"}'
 
# Получить список задач
curl http://localhost:8080/api/tasks
```

## Примеры запросов (Postman)

**Создать пользователя**
```
POST {{baseUrl}}/api/users
 
{
  "name": "Alice",
  "email": "alice@example.com",
  "password": "pass123"
}
```
→ `201 Created`, `Location: /api/users/<id>`
```json
{
  "id": "4b3ba2r1w-...",
  "name": "Alice",
  "email": "alice@example.com",
  "createdAt": "2026-09-10T12:00:00Z",
  "updatedAt": "2026-09-10T12:00:00Z"
}
```

**Создать задачу без владельца**
```
POST {{baseUrl}}/api/tasks
 
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

**"Взять" задачу — назначить владельца и перевести в работу одним запросом**
```
PUT {{baseUrl}}/api/tasks/{{taskId}}
 
{
  "userId": "{{userId}}",
  "status": "IN_PROGRESS"
}
```
→ `200 OK`, `status: "IN_PROGRESS"`, `ownerId`/`ownerName` заполнены.

**Попытка перевести задачу в работу без владельца — ожидаемая ошибка**
```
PUT {{baseUrl}}/api/tasks/{{taskId}}
 
{
  "status": "IN_PROGRESS"
}
```
→ `400 Bad Request`
```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Cannot move task to IN_PROGRESS without an owner — assign a user first or in the same request"
}
```
