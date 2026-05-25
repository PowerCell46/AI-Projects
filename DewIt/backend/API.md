# DewIt REST API

Base path: `/api`

All request and response bodies are JSON (`Content-Type: application/json`). Timestamps are ISO-8601 `LocalDateTime` (no timezone offset). IDs are UUIDs.

## Conventions

### Pagination
List endpoints return a stable `PagedModel` envelope (enabled via `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`):

```json
{
  "content": [ /* items */ ],
  "page": {
    "size": 50,
    "number": 0,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

Query params: `page` (0-indexed), `size` (default 50), `sort` (e.g. `sort=createdAt,desc`). Default sort is `createdAt,desc`.

### Error responses
Returned by the global exception handler. Shape:

```json
{
  "timestamp": "2026-05-22T10:15:30",
  "status": 404,
  "error": "Not Found",
  "message": "Task 7f3a... not found",
  "path": "/api/tasks/7f3a..."
}
```

Validation failures (`400`) additionally include an `errors` array:

```json
{
  "timestamp": "2026-05-22T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/tasks",
  "errors": [
    { "field": "title", "message": "must not be blank" }
  ]
}
```

Status code mapping:

| Status | When |
| --- | --- |
| `400 Bad Request` | Validation failure, malformed JSON, invalid path/query parameter type, or missing required query parameter |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Duplicate category name, or deleting a category that still has tasks |
| `500 Internal Server Error` | Unhandled exception (message is intentionally generic; details are logged server-side) |

---

## Tasks

Resource path: `/api/tasks`

### Task JSON shape

`TaskResponse`:

```json
{
  "id": "7f3a8c2e-0c1a-4b8e-9c4a-1a2b3c4d5e6f",
  "title": "Write report",
  "description": "Q2 financial summary",
  "dueDate": "2026-05-30T17:00:00",
  "priority": "HIGH",
  "status": "ACTIVE",
  "categoryId": "11111111-2222-3333-4444-555555555555",
  "categoryName": "Work",
  "createdAt": "2026-05-22T09:00:00",
  "lastModifiedAt": "2026-05-22T09:00:00",
  "completedAt": null
}
```

Field notes:
- `description` may be `null` if not provided on creation.
- `dueDate` may be `null`.
- `completedAt` is set to the server timestamp the moment `status` first transitions to `COMPLETED`; cleared back to `null` if the task is re-activated. Never updated by subsequent edits.

Enums:
- `priority`: `LOW`, `MEDIUM`, `HIGH`
- `status`: `ACTIVE`, `COMPLETED`

### `POST /api/tasks` — Create task

Request body:

```json
{
  "title": "Write report",
  "description": "Q2 financial summary",
  "dueDate": "2026-05-30T17:00:00",
  "priority": "HIGH",
  "status": "ACTIVE",
  "categoryId": "11111111-2222-3333-4444-555555555555"
}
```

Validation:
- `title`: required, non-blank, max 200 chars
- `description`: optional, max 2000 chars
- `dueDate`: optional; if provided, must be present or future (`@FutureOrPresent`)
- `priority`: required (`LOW`, `MEDIUM`, or `HIGH`)
- `status`: required (`ACTIVE` or `COMPLETED`)
- `categoryId`: required, must reference an existing category

Response: `201 Created`, `Location: /api/tasks/{id}`, body is `TaskResponse`.

### `GET /api/tasks` — List tasks (paginated)

Query params: standard pagination (`page`, `size`, `sort`).

Response: `200 OK`, `Page<TaskResponse>`.

### `GET /api/tasks/{id}` — Get task by ID

Response: `200 OK`, `TaskResponse`. `404` if not found. `400` if `{id}` is not a valid UUID.

### `PATCH /api/tasks/{id}` — Partial update

Request body (all fields optional; only present, non-null fields are applied):

```json
{
  "title": "Write quarterly report",
  "dueDate": "2026-06-01T17:00:00",
  "priority": "MEDIUM",
  "status": "COMPLETED",
  "categoryId": "11111111-2222-3333-4444-555555555555"
}
```

> **Note:** A `null` value is treated as "field not provided" and leaves the entity unchanged.
> Clearing `dueDate` or `description` is not currently supported via this endpoint.
> See `KNOWN_ISSUES.md §1` for the planned resolution.

When `status` is set to `COMPLETED`, `completedAt` is automatically recorded.
When `status` is set back to `ACTIVE`, `completedAt` is cleared.

Validation:
- `title`: max 200 chars (if provided)
- `description`: max 2000 chars (if provided)

Response: `200 OK`, `TaskResponse`. `404` if not found.

### `DELETE /api/tasks/{id}` — Delete task

Response: `204 No Content`. `404` if not found.

### `GET /api/tasks/count` — Count tasks by date filter

> **Note:** This endpoint is not currently consumed by the frontend, which derives counts
> client-side from the paginated task list. See `KNOWN_ISSUES.md §11` for context.

Query params:
- `filter` (required): one of `TODAY`, `OVERDUE`, `UPCOMING`

Bucketing (calendar-day boundaries in the server's JVM timezone; tasks without a due date are excluded):
- `TODAY`: `dueDate >= startOfToday AND dueDate < startOfTomorrow`
- `OVERDUE`: `dueDate < startOfToday`
- `UPCOMING`: `dueDate >= startOfTomorrow`

Response: `200 OK`

```json
{ "count": 5 }
```

`400` if `filter` is missing or not one of the allowed values.

---

## Categories

Resource path: `/api/categories`

### Category JSON shape

`CategoryResponse` (eager-fetches its tasks, ordered by `createdAt DESC`):

```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "name": "Work",
  "createdAt": "2026-05-22T09:00:00",
  "lastModifiedAt": "2026-05-22T09:00:00",
  "tasks": [ /* TaskResponse[], ordered by createdAt DESC */ ]
}
```

### `POST /api/categories` — Create category

Request body:

```json
{ "name": "Work" }
```

Validation:
- `name`: required, non-blank, max 100 chars, **must be unique** (case-sensitive, enforced by a database unique constraint → `409` on duplicate)

Response: `201 Created`, `Location: /api/categories/{id}`, body is `CategoryResponse`.

### `GET /api/categories` — List categories (paginated)

Query params: standard pagination.

Response: `200 OK`, `Page<CategoryResponse>`.

### `GET /api/categories/{id}` — Get category by ID

Response: `200 OK`, `CategoryResponse`. `404` if not found. `400` if `{id}` is not a valid UUID.

### `PATCH /api/categories/{id}` — Rename category

Request body:

```json
{ "name": "Personal" }
```

Validation:
- `name`: max 100 chars (if provided), must be unique → `409` on duplicate

Response: `200 OK`, `CategoryResponse`. `404` if not found.

### `DELETE /api/categories/{id}` — Delete category

Response: `204 No Content`. `404` if not found. `409 Conflict` if the category still has tasks (delete or reassign them first).

---

## Endpoint summary

| Method | Endpoint | Description |
| --- | --- | --- |
| `POST` | `/api/tasks` | Create task |
| `GET` | `/api/tasks` | List tasks (paginated) |
| `GET` | `/api/tasks/{id}` | Get task |
| `PATCH` | `/api/tasks/{id}` | Update task (partial) |
| `DELETE` | `/api/tasks/{id}` | Delete task |
| `GET` | `/api/tasks/count?filter=...` | Count tasks by `TODAY` / `OVERDUE` / `UPCOMING` |
| `POST` | `/api/categories` | Create category |
| `GET` | `/api/categories` | List categories (paginated) |
| `GET` | `/api/categories/{id}` | Get category |
| `PATCH` | `/api/categories/{id}` | Rename category |
| `DELETE` | `/api/categories/{id}` | Delete category |
