# DewIt — Project Overview

DewIt is a personal task-management web application. Users organise tasks into categories, assign priorities and due dates, and track completion over time through a statistics dashboard.

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21 · Spring Boot 3 · Spring Data JPA · Spring Validation · Hibernate · H2 (dev) |
| Frontend | React 18 · TypeScript · Vite · React Router v6 · TanStack Query v5 · date-fns |
| Styling | Plain CSS with custom properties (no CSS framework) |
| Build | Maven (backend) · npm / Vite (frontend) |

---

## Repository layout

```
DewIt/
├── backend/                          Spring Boot REST API
│   ├── API.md                        REST API reference
│   └── src/main/java/com/dewit/backend/
│       ├── controllers/              HTTP layer — maps routes to service calls
│       ├── services/
│       │   ├── interfaces/           Public service contracts (TaskService, CategoryService)
│       │   └── implementations/     Spring @Service beans (TaskServiceImpl, CategoryServiceImpl)
│       ├── repositories/             Spring Data JPA repositories
│       ├── entities/                 JPA entities (Task, Category, CommonEntity)
│       │   └── enumerations/         TaskStatus, TaskPriority enums
│       ├── DTOs/
│       │   ├── task/                 TaskCreateRequest, TaskUpdateRequest, TaskResponse,
│       │   │                         TaskCountFilter, TaskCountResponse
│       │   └── category/             CategoryCreateRequest, CategoryUpdateRequest, CategoryResponse
│       ├── mappers/                  Static mapper classes (TaskMapper, CategoryMapper)
│       └── exceptions/               Custom exception types + GlobalExceptionHandler
│
├── frontend/                         React SPA
│   └── src/
│       ├── pages/                    Full-page views
│       │   ├── Dashboard/            Home — category carousel + task buckets
│       │   ├── NewTaskView/          Create-task form
│       │   ├── CategoryView/         Per-category task grid with filters
│       │   └── Statistics/           Completion metrics over a chosen timeframe
│       ├── components/               Reusable UI components
│       ├── hooks/                    React Query hooks (data fetching + mutations)
│       ├── api/                      Typed API client functions (tasks.ts, categories.ts)
│       ├── utils/                    Pure utility functions and custom hooks
│       └── types/                    Shared TypeScript interfaces (TaskResponse, Page, …)
│
├── PROJECT.md                        ← this file
├── KNOWN_ISSUES.md                   Documented limitations awaiting architectural decisions
└── problems.md                       Issue tracker (resolved items removed as they are fixed)
```

---

## Domain model

### Task

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Auto-generated |
| `title` | String (max 200) | Required, non-blank |
| `description` | String (max 2000) | Optional, nullable |
| `dueDate` | LocalDateTime | Optional; must be present-or-future on create |
| `priority` | `LOW` \| `MEDIUM` \| `HIGH` | Required |
| `status` | `ACTIVE` \| `COMPLETED` | Required; defaults to `ACTIVE` on create |
| `completedAt` | LocalDateTime | Set automatically when status → `COMPLETED`; cleared on reactivation |
| `categoryId` | UUID (FK) | Required; references an existing Category |
| `createdAt` | LocalDateTime | Set by JPA auditing, immutable |
| `lastModifiedAt` | LocalDateTime | Updated by JPA auditing on every save |

### Category

| Field | Type | Notes |
| --- | --- | --- |
| `id` | UUID | Auto-generated |
| `name` | String (max 100) | Required, non-blank, **unique** (database constraint) |
| `tasks` | Task[] | Eagerly fetched, ordered `createdAt DESC` |
| `createdAt` | LocalDateTime | Set by JPA auditing |
| `lastModifiedAt` | LocalDateTime | Updated by JPA auditing |

---

## Backend architecture

### Request lifecycle

```
HTTP request
  → Controller          validates input (@Valid), maps to service call
  → Service interface   defines the contract
  → Service impl        transaction boundary (@Transactional), business logic
  → Repository          Spring Data JPA query
  → Mapper              entity → DTO (static methods, no framework)
  → Controller          returns ResponseEntity / plain DTO
```

### Key design decisions

- **Interface + implementation split** — each service has a `services/interfaces/` contract and a `services/implementations/` `@Service` bean. Controllers inject the interface; Spring wires the implementation.
- **Partial-update semantics** — `PATCH` treats `null` as "field not provided". Explicit clearing of `dueDate` / `description` is a known limitation; see `KNOWN_ISSUES.md §1`.
- **`completedAt` tracking** — managed manually in `TaskServiceImpl`: set on first `COMPLETED` transition, cleared on reactivation. Not touched by JPA auditing.
- **Global exception handling** — `GlobalExceptionHandler` maps known exception types to precise HTTP status codes; `handleGeneric` logs the full exception server-side and returns a generic message to the client (no stack-trace leakage).
- **Category uniqueness** — enforced by a database `UNIQUE` constraint on `categories.name`. A `DataIntegrityViolationException` is caught and mapped to `409`.
- **Task ordering inside Category** — `@OrderBy("createdAt DESC")` on the `tasks` collection.

---

## Frontend architecture

### Pages

| Page | Route | Purpose |
| --- | --- | --- |
| `Dashboard` | `/` | Overview: category carousel, tasks bucketed into Overdue / Today / Upcoming |
| `NewTaskView` | `/tasks/new` | Form to create a task |
| `CategoryView` | `/categories/:id` | Task grid for one category with sort + filter controls and inline category rename |
| `Statistics` | `/statistics` | Completion metrics (timeframe selector, rate card, category breakdown) |

### Data fetching

All server state goes through TanStack Query:

- `useTasksQuery` — fetches up to 200 tasks; shared by Dashboard, CategoryView, Statistics. See note in the hook file about the single cache key.
- `useCategoriesQuery` — fetches up to 100 categories.
- Mutation hooks — `useCreateTaskMutation`, `useUpdateTaskMutation`, `useDeleteTaskMutation`, `useToggleTaskStatusMutation`, `useCreateCategoryMutation`, `useUpdateCategoryMutation`.
- Optimistic updates — `useToggleTaskStatusMutation` applies a local status + `lastModifiedAt` flip immediately; rolled back on error.

### Component conventions

- **`Field`** — layout wrapper. Renders a `<div aria-labelledby>` + either a `<label htmlFor>` (for real inputs) or a `<span>` (for custom controls). Never wraps a button in a `<label>`.
- **`Select`** — fully keyboard-navigable (arrow keys, Enter, Escape, auto-focus on open). Accepts a `disabled` prop.
- **`FilterSelect`** — variant used for sort/filter controls; same keyboard model as `Select`.
- **`useOutsideClick`** — uses `pointerdown` to cover mouse and touch.
- **`useModalEffects`** — manages focus trap, Escape key, and `body.modal-open`. Uses a module-level stack so nested modals each respond only when topmost; `body.modal-open` is removed only when all modals are closed.
- **`Carousel`** — auto-advances when content overflows the viewport. Duplicate clone items are wrapped in `aria-hidden="true"` so screen readers skip them.

### Utility functions

| File | Purpose |
| --- | --- |
| `bucketTasks.ts` | Splits tasks into `overdue` / `today` / `upcoming`; excludes `COMPLETED` tasks |
| `computeStats.ts` | `countCreated`, `countCompleted` (uses `completedAt`), `completionRate`, `countOverdue`, `completedByCategory` |
| `formatDueDate.ts` | Human-readable due-date strings (`Today, 9:00 AM`, `Tomorrow, …`, etc.) |
| `useOutsideClick.ts` | Closes dropdowns/popovers on outside pointer events |
| `useModalEffects.ts` | Focus trap + keyboard handling for modal dialogs |
| `useVisibleSlots.ts` | Returns visible carousel slot count based on viewport width (SSR-safe) |

---

## Known limitations

See **`KNOWN_ISSUES.md`** for detailed write-ups and proposed resolutions:

1. **PATCH clearing** — `null` in a PATCH body is ignored; `dueDate` / `description` cannot be cleared once set.
2. **Timezone handling** — dates stored as `LocalDateTime`; server and client may disagree on "today" boundaries.
3. **Completion-rate denominator** — rate mixes tasks completed in the window vs. tasks created in the window.
4. **Pagination caps** — frontend fetches a fixed max of 200 tasks / 100 categories; no infinite scroll.
5. **Sort-field whitelist** — paginated endpoints accept arbitrary `sort=` parameters.
