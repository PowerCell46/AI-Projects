# WealthBuilder — Implementation Plan

A personal **asset holdings tracker**. Users record their purchases across moderator-defined
asset classes (Stocks, Cryptocurrencies, Precious Metals, …), see their net invested balance,
and review per-asset aggregations. Moderators manage the asset catalog.

> Naming: `Asset` = the moderator-managed class/category (Stocks, Crypto, Metals). `AssetHolding` =
> a user's individual purchase record within an asset. The actual instrument name (Apple, BTC) lives
> on the holding's `name` field.
>
> Scope note: the fleshed-out ideas are the real spec. The original brief's expense/income/budget
> framing is reinterpreted — "category" = asset, there is no income/expense type, and "balance"
> is the user's net invested amount.

---

## 1. Domain model

### User
| field | type | notes |
|---|---|---|
| id | Long | PK |
| username | String | unique, @NotBlank |
| passwordHash | String | BCrypt |
| role | enum `Role` | `USER` (default) or `MODERATOR` |

- **balance is NOT stored** — it is a derived/computed value = `sum(boughtForAmount)` over all the
  user's holdings (net invested). Exposed read-only.

### Asset (moderator-managed catalog)
| field | type | notes |
|---|---|---|
| id | Long | PK |
| name | String | @NotBlank, **unique (case-insensitive)** → 409 on dup |
| description | String | @NotBlank, max len |
| imageBase64 | text/`@Lob` | required; `data:image/...;base64,...` |

### AssetHolding (a user's purchase record)
| field | type | notes |
|---|---|---|
| id | Long | PK |
| asset | Asset FK | @ManyToOne |
| user | User FK | @ManyToOne (owner) |
| name | String | @NotBlank, max len (the instrument, e.g. "Apple") |
| boughtForAmount | BigDecimal | @Positive (total spent) |
| quantity | BigDecimal | @Positive (units) |
| date | LocalDate | @NotNull @PastOrPresent (purchase date) |
| note | String | optional, max len |

> ⚠️ **Open decision — transaction timestamp.** `date` is a `LocalDate` (day only), capturing the
> *purchase date* but **not** a precise timestamp of when the transaction happened, nor when the
> record was created. Decide before building the entity:
> - **Purchase moment matters?** → make it `LocalDateTime` (or add a `time`) instead of `LocalDate`.
> - **Audit trail wanted?** → add a separate `createdAt` (`Instant`, `@CreationTimestamp`),
>   independent of the user-supplied purchase date.

- **price is derived, never stored**: `price = boughtForAmount / quantity`.
- Money/quantity use `BigDecimal` for exact arithmetic (non-functional accuracy requirement).

### Aggregation (per current user, per asset)
Computed server-side over **all** of the user's holdings for that asset:
- **average price** = simple mean of each holding's `boughtForAmount / quantity` (unweighted).
- **quantity sum** = Σ quantity
- **amount sum** = Σ boughtForAmount
- **period** = min(date) → max(date)

---

## 2. Backend (Spring Boot 4.0.6 / Java 25)

Mirrors the SpotyStats stack: JPA, validation, security, Lombok, Postgres, Jackson 3 (`tools.jackson`).

### Auth & security
- **Local register/login** (no OAuth). Passwords hashed with BCrypt.
- **JWT bearer tokens**: login returns one **24h HS256 access token** (secret from env). No refresh.
- Frontend sends `Authorization: Bearer <token>`; a `OncePerRequestFilter` validates it and sets the
  security context. **Stateless** session policy (no spring-session table).
- All endpoints auth-required **except** `POST /api/auth/register` and `POST /api/auth/login`.
- Expired/invalid token → **401**; frontend redirects to `/login`.
- **Moderator seeded at startup**: an `ApplicationRunner` creates one `MODERATOR` from env config
  (`APP_MOD_USERNAME` / `APP_MOD_PASSWORD`) only if none exists. Register always creates `USER`.
- Method-level `@PreAuthorize("hasRole('MODERATOR')")` on asset write endpoints.

### REST API (`/api`)
| method | path | role | purpose |
|---|---|---|---|
| POST | `/auth/register` | public | create USER, returns token |
| POST | `/auth/login` | public | returns `{ token }` |
| GET | `/auth/me` | auth | current user + computed balance |
| GET | `/assets` | auth | carousel list (no blob — see image note) |
| GET | `/assets/{id}` | auth | asset detail (no blob) |
| GET | `/assets/{id}/image` | auth | base64 image (lazy-loaded by `<img>`) |
| POST | `/assets` | MOD | create (409 on dup name) |
| PUT | `/assets/{id}` | MOD | edit |
| DELETE | `/assets/{id}` | MOD | delete |
| GET | `/assets/{id}/holdings?page=&size=` | auth | **paginated**, current user's, date desc |
| GET | `/assets/{id}/holdings/summary` | auth | aggregation over ALL the user's holdings |
| POST | `/assets/{id}/holdings` | auth | create holding (owner = current user) |
| PUT | `/holdings/{id}` | auth | edit own holding |
| DELETE | `/holdings/{id}` | auth | delete own holding |
| GET | `/dashboard/distribution` | auth | invested-per-asset for the home donut |

- **Image strategy** (base64 in DB, mitigated): list/detail DTOs **omit** the blob; the carousel's
  `<img src="/api/assets/{id}/image">` lazy-loads each image so list JSON stays small.
- Holding/summary queries always filtered `WHERE user = currentUser`; ownership enforced on PUT/DELETE
  (403 if not owner).
- Pagination via Spring Data `Pageable`.

### Cross-cutting
- `@RestControllerAdvice` → RFC-7807 `ProblemDetail` for validation (400), 401, 403, 404, 409.
- CORS config allowing the Vite dev origin (`http://localhost:5173`).
- DTOs separate from entities (request/response records); no entity leakage.

### Package layout (`backend/src/main/java/.../wealthbuilder/backend`)
Layered (mirrors SpotyStats): top-level packages are layers, topics nest **inside** them.
```
config/        SecurityConfig, CorsConfig, DataSeeder, AppProperties, JwtAuthenticationFilter
controllers/   AuthController  (+ AssetController, HoldingController, DashboardController)
services/
  interfaces/        AuthService, JwtService  (+ AssetService, HoldingService)
  implementations/   AuthServiceImpl, JwtServiceImpl, AppUserDetailsServiceImpl  (+ …Impl)
repositories/  UserRepository  (+ AssetRepository, HoldingRepository)
entities/      User, Role  (+ Asset, AssetHolding)
dtos/          auth/{Register,Login}Request, {Auth,CurrentUser}Response  (+ asset/, holding/, dashboard/)
exceptions/    GlobalExceptionHandler, InvalidTokenException, UsernameAlreadyTakenException
utils/         (cross-cutting helpers, added as needed)
```

---

## 3. Frontend (React 19 + Vite + TS + react-router 7)

Convention: per-component directory + CSS Modules + shared theme variables (matches SpotyStats FE).
Sizing via rem token scale + auto-fit/clamp, not media queries.

### Auth handling
- `AuthContext`: holds token + user. Token kept in memory, **persisted to localStorage** so a refresh
  stays logged in (rehydrate on load).
- `apiClient` (fetch wrapper): injects `Authorization` header; on **401** clears auth + redirects to
  `/login`.
- `ProtectedRoute` wrapper guards all non-auth routes; `ModeratorRoute` additionally checks role.

### Routes
| path | access | screen |
|---|---|---|
| `/login` | public | login form |
| `/register` | public | register form |
| `/` (home) | auth | balance + **donut (invested per asset)** + asset **carousel** |
| `/assets/:id` | auth | detail: holdings table (paginated) + aggregation panel + holding CRUD |
| `/admin/assets` | MOD | asset catalog CRUD (create/edit/delete, base64 image upload) |

### Key components
- **Carousel**: CSS scroll-snap (no extra lib), responsive, lazy `<img>` per tile, click → detail.
- **Donut**: Recharts `PieChart` of net-invested distribution from `/dashboard/distribution`.
- **HoldingsTable**: server-paginated, sorted date desc, row actions edit/delete.
- **AggregationPanel**: avg price, quantity sum, amount sum, period (from `/summary`).
- **HoldingForm modal**: name, amount, quantity, date, note; client validation mirrors server.
- **AssetForm** (mod): name, description, image picker → base64.

---

## 4. Decisions locked (from grilling)
1. App = personal asset holdings tracker; `Asset` (catalog) + `AssetHolding` (per-user buy).
2. average price = **simple mean** of per-holding unit prices.
3. balance = **computed** net invested (sum of boughtForAmount), read-only.
4. Auth = **JWT bearer**, single 24h token, no refresh; token in React context + localStorage.
5. Moderator **seeded at startup** from env; register → USER only.
6. Asset image = **base64 in DB**, served via dedicated lazy-loaded image endpoint.
7. Holding fields: name, boughtForAmount, quantity, date, optional note; price derived.
8. Detail page = **current user's holdings only**, **no filtering**, sorted date desc.
9. Visualization = **one donut on home** (invested per asset); Recharts.
10. **Postgres**; `backend/` + `frontend/` siblings.
11. Holdings table = **server-side pagination**; aggregation = separate endpoint over all holdings.
12. Validation: holding positive+required+`@PastOrPresent`; asset unique-name + required, 409 on dup.

---

## 5. Suggested build order
1. Backend scaffold (pom mirroring SpotyStats) + Postgres config + entities/repos.
2. Security: JWT service/filter, register/login, mod seeder.
3. Asset CRUD (+ image endpoint) with mod authorization.
4. Holding CRUD + pagination + summary + dashboard distribution.
5. `@RestControllerAdvice` + validation polish.
6. Frontend scaffold + AuthContext + apiClient + routing/guards.
7. Login/register → home (balance + carousel + donut).
8. Asset detail (table + aggregation + holding CRUD).
9. Moderator asset admin screen.
10. Responsive pass + empty/error states.

---

## 6. Build progress

### Backend
- [x] **Scaffold** — `pom.xml` (Boot 4.0.6 / Java 25, JPA, validation, security, postgres,
      lombok, `nimbus-jose-jwt`), `BackendApplication`, `application.properties`, `AppProperties`,
      `.gitignore` (root + backend) + `.env.example`.
- [x] **User domain** — `User` entity (`app_user`), `Role` enum, `UserRepository`.
- [x] **JWT security** — `JwtService` (HS256 issue/verify), `JwtAuthenticationFilter`,
      `AppUserDetailsService`, `SecurityConfig` (stateless, method security), `CorsConfig`.
- [x] **Auth endpoints** — `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`
      (+ request/response DTOs, bean validation).
- [x] **Moderator seeder** — `DataSeeder` (`ApplicationRunner`, idempotent, env-driven).
- [x] **Error handling** — `GlobalExceptionHandler` → RFC-7807 `ProblemDetail` (400/401/403/409/500).
- [ ] **Asset CRUD** — entity/repo, list+detail (no blob), image endpoint, MOD-only writes, 409 on dup.
- [ ] **Holding CRUD** — entity/repo, paginated list, create/edit/delete (ownership), `/summary`.
- [ ] **Dashboard** — `/dashboard/distribution`; wire computed **balance** into `/auth/me`.
- [ ] **Validation/error polish** — 404 for missing asset/holding, ownership 403, edge cases.

> ⏸ **Paused after authentication, as requested.** Note: `/auth/me` returns `balance = 0` for now;
> it gets wired to `sum(boughtForAmount)` once holdings exist (see the Dashboard item).

### Frontend
- [x] **Scaffold** — Vite + React 19 + TS (strict), react-router 7, `@stylistic` ESLint
      (4-space, semicolons, single quotes), CSS Modules + shared theme tokens, URL/route
      constants, `.env(.example)` + `.gitignore`.
- [x] **AuthContext + apiClient + guards** — token in context + localStorage (rehydrate on
      load), fetch wrapper injecting `Bearer` + parsing RFC-7807 into `ApiError`, global
      401 → logout, `ProtectedRoute` / `PublicOnlyRoute`. `ThemeProvider` (light/dark).
- [x] **Login/register** — single CRT auth screen (login + register, VHS sweep mode swap,
      reduced-motion fallback), wired to `/auth/register` + `/auth/login` with field-level
      (400) and form-level (401/409) error handling; placeholder home shows the balance.
- [ ] Home dashboard (carousel + donut).
- [ ] Asset detail (table + aggregation + holding CRUD).
- [ ] Moderator asset admin screen.
- [ ] Responsive pass + empty/error states.
