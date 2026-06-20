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
| imageBase64 | text | required; `data:image/...;base64,...` |
| imageName | String | required, max 255; original upload filename, shown back on edit |

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
| GET | `/assets` | auth | carousel list (no blob; includes `imageName`) |
| GET | `/assets/{id}` | auth | asset detail (no blob; includes `imageName`) |
| GET | `/assets/{id}/image` | auth | base64 image (lazy-loaded by `<img>`) |
| POST | `/assets` | MOD | create (409 on dup name) |
| PUT | `/assets/{id}` | MOD | edit |
| DELETE | `/assets/{id}` | MOD | delete |
| GET | `/assets/{id}/holdings?page=&size=&name=&from=&to=` | auth | **paginated + filtered** (name contains, inclusive date range), current user's, date desc |
| GET | `/assets/{id}/holdings/summary` | auth | aggregation over ALL the user's holdings (backend only; the FE aggregation panel was removed) |
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
| `/assets/:name` | auth | detail: holdings table (paginated) + aggregation panel + holding CRUD |
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
- [x] **Asset CRUD** — `Asset` entity/repo, list+detail DTO (no blob), raw-bytes image endpoint
      (`DataUriImage` decode), MOD-only writes via `@PreAuthorize`, 409 on case-insensitive dup name,
      404 on missing; wired into `GlobalExceptionHandler`. (Image column is `text`, not `@Lob`, to
      avoid the Postgres large-object footgun.) `imageName` persisted alongside the blob and returned
      in list/detail DTOs so the admin edit form can display the original upload filename.
- [x] **Holding CRUD** — `AssetHolding` entity (`date` = `LocalDate` purchase day **+** separate
      `createdAt` `@CreationTimestamp` audit stamp), `HoldingRepository`, paginated list forced to
      newest-first (date, id desc), create/edit/delete with ownership check (403 via
      `AccessDeniedException`), `/summary` aggregation (unweighted mean unit price, sums, date span).
      Derived unit price via shared `Money` util; `PageResponse<T>` envelope instead of raw `Page`.
      List query takes an optional `HoldingFilter` (name contains, inclusive from/to date range) via
      a `search` repository query — filtering happens in SQL so it composes with pagination.
- [x] **Dashboard** — `/dashboard/distribution` (grouped query → `AssetInvestment` projection);
      computed **balance** wired into `/auth/me` (`sum(boughtForAmount)` over all holdings).
- [x] **Validation/error polish** — `HoldingRequest` bean validation (positive, `@PastOrPresent`,
      `@Digits` matching column precision); 404 for missing asset/holding; ownership 403.

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
- [~] **Home dashboard** — asset **carousel** done (scroll-snap, per-tile images fetched via
      the API client + object URLs since `<img>` can't carry the bearer token, links to detail).
      **Donut not built yet** — backend (`/dashboard/distribution`) is now ready.
- [x] **Asset detail** — route is now **`/assets/:slug`** (slugified name: lowercase, non-alnum
      runs → `-`, e.g. `/assets/precious-metals`; resolves to the asset by re-slugifying catalog
      names, so matching is case/spacing-insensitive and the API stays id-based). Header is a
      card (image + title/desc, vertically centred). Per-user **holdings table** (server-paginated
      via `PageResponse<T>`, larger font, always-visible pager, inline two-step delete) with a
      **filter bar** (name contains + inclusive from/to date range, filtered server-side across all
      pages, resets to page 1) and **holding create/edit/delete** via a modal `HoldingForm`.
      The on-top aggregation panel was **removed** per feedback. `holdingService` (builds the
      filter query) + `holding`/`page` types + `useHoldings` hook + `slugify` +
      `formatMoney/Quantity/Price` (locale pinned to en-US).
- [x] **Moderator asset admin** (`/admin/assets`) — `ModeratorRoute` guard + shared `AppHeader`
      (moderator-only catalog link), list with inline edit/delete-confirm, `AssetForm` with a
      **custom file picker** (hidden native input driven by a styled button + filename label, no
      `<img>` preview); edit refetches the current image to avoid forced re-upload and prefills the
      stored `imageName` so the picker shows the real filename.
- [ ] **Donut** — Recharts `PieChart` from `/dashboard/distribution` on the home screen.
      (Recharts is not yet a dependency — needs adding, or hand-roll an SVG donut.)
- [x] **Holdings on asset detail** — `HoldingsTable`, `AggregationPanel`, `HoldingForm` modal,
      wired through `useHoldings` + `holdingService` (see Asset detail above).
- [x] **Vitest** — Vitest 4 + React Testing Library + jsdom (`test`/`test:watch` scripts).
      16 tests green (formatters, `AggregationPanel`, `HoldingsTable`, `HoldingForm` validation).
- [ ] Responsive pass + empty/error states (basic empty/error states in place).

---

## 7. Status & what's next

### Done
- **Backend: complete.** Auth (JWT register/login/me + moderator seeder), Asset CRUD (+ image
  endpoint, + persisted `imageName`), Holding CRUD (+ pagination + **name/date-range filtering** +
  summary), Dashboard distribution, computed balance, RFC-7807 error handling. **163 tests green**
  (unit + validation + `@DataJpaTest` + `@WebMvcTest`).
- **Frontend: auth + catalog + holdings.** Auth screen, AuthContext/apiClient/guards, theming,
  asset carousel, full moderator asset admin (custom file picker showing the persisted filename
  on edit), and the **asset-detail holdings UI** (slug route, card header, filterable + paginated
  table, modal CRUD). Vitest suite (17 tests). Lint clean, build passes.

### Left (frontend only)
1. **Home donut** — Recharts pie from `/dashboard/distribution`.
2. **Responsive pass + empty/error states** — final polish.

### Next up
**Home donut** — Recharts pie from `/dashboard/distribution` (add Recharts, or hand-roll an SVG
donut to avoid the dep), then a final responsive/empty-state pass. The asset-detail holdings UI is
now done.

#### Locked decision — asset-detail route: **FE name, API by id**
Frontend route is `/assets/:name` for readable URLs; it resolves to the asset by name
(case-insensitive) from the already-loaded catalog list, then calls the **unchanged** id-based
endpoints (`/assets/{id}/holdings`, `/summary`, …). No backend change, no new lookup endpoint, and
no rename/encoding fragility on the wire. (Rejected: name-keying the API — mutable key, encoding,
ripple across every nested path.)

#### Locked decision — frontend tests: **add Vitest**
Set up Vitest + React Testing Library (`test` script in `package.json`) and cover the holdings
table/form/aggregation logic as it is built.
