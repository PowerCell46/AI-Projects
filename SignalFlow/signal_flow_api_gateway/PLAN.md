# SignalFlow API Gateway — auth phase plan

Outcome of the `/grill-me` interview (2026-09-21). Design decided up front lives here; calls made
*during* implementation go to `DECISIONS.md`.

Each step below is self-contained: what it builds, what it decides, and the gate that says it's done.
**No step starts on a red or missing test. Each step ends green.**

---

## Context

**What this service is.** The single front door for SignalFlow. It owns users and **all** access rules —
downstream services know nothing about authorization and are reachable only through this gateway.

**Not in this phase.** Request forwarding. When it lands it will be `spring-cloud-gateway-server-webmvc`
(the blocking variant), so nothing built here has to change. Reactive/WebFlux was considered and
rejected: it rules out JPA and buys throughput this project doesn't need.

**Stack**, unchanged from the pom: blocking WebMVC + JPA, Java 25, Boot 4.2.0-M1, Postgres.

**Endpoints, total:** `POST /api/v1/auth/register`, `POST /api/v1/auth/login`,
`POST /api/v1/auth/logout`, `GET /api/v1/auth/me`.

**Origin topology** (copied from UrlShortener, which is already single-origin — so there is **no CORS
config anywhere**): prod Caddy terminates TLS → frontend nginx serves the SPA and proxies `/api/` to the
gateway; `Caddyfile.local` is plain HTTP on `:80`; Vite's `server.proxy` sends `/api` → `localhost:8080`
in dev. Frontend is a React + TS SPA, built in a later phase.

**Conventions** — layering `controller → service → repository`, service interface + `...Impl`,
constructor injection via `private final` + `@RequiredArgsConstructor`, `/api/v1`, snake_case test names,
package layout under `com.peter_gerdzhikov.signal_flow_api_gateway` (`/configurations`, `/controllers`,
`/DTOs/request`, `/DTOs/response`, `/entities`, `/exceptions`, `/repositories`, `/services/interfaces`,
`/services/implementations`, `/utilities`). Carried from UrlShortener; written to `CLAUDE.md` in step 1.

---

## Status

All 9 steps (baseline, entities, e2e catalog, security skeleton, register, login, logout, `/me`, audit)
are complete — full auth phase shipped and green. Per-step build notes and decisions now live in
`DECISIONS.md`; `TESTING.md` holds the current e2e catalog.

---

## Known gaps (accepted, not oversights)

- **No brute-force protection on login.** Bcrypt-12 caps guessing at roughly 4/sec/core, and doing rate
  limiting properly wants shared state (Redis) once there is more than one instance.
- **No server-side logout revocation** — a stolen token lives until it expires (≤1h).
- **Claims can be stale for up to an hour** — disabling an account or changing a role does not take effect
  until the current token expires.
- **Register leaks whether an email is registered** (409).
- **`ddl-auto=update`** — no versioned migrations; schema drift between environments is possible.
- **No email confirmation** — anyone can register with an address they do not own.
- **Downstream trust model** (identity headers over a private network) is unimplemented and unverified;
  it becomes a live risk the moment the first downstream service exists.

All three `exploit-report-2026-09-22.md` findings (login timing oracle, missing `LoginRequestDTO` size
cap, duplicate-email registration race) were patched post-step-9 — see `DECISIONS.md`.

---

## Interview record

Every question asked, and what was decided. ✎ marks a decision taken **against** the recommendation.

| # | Question | Decision |
| --- | --- | --- |
| 1 | What is this service — gateway, auth service, or monolith? | Gateway **plus** authentication and authorization |
| 2 | Blocking or reactive; is forwarding in this phase? | Blocking WebMVC + JPA; auth only, no forwarding |
| 3 | Schema management — migrations or Hibernate? | ✎ `ddl-auto=update`, no Flyway |
| 4 | `CommonEntity` shape — mapping, id generation, timestamps? | `@MappedSuperclass`, time-ordered UUID, `createdAt` + `updatedAt` |
| 5 | Does `username` stay; what is the login identifier? | Drop `username`; email is the identity |
| 6 | One role or a set; how does the first ADMIN appear? | Single `Role` enum, STRING-mapped; ADMIN never via API |
| 7 | Is email confirmation in this phase; how is mail sent? | Deferred — register activates immediately |
| 8 | Carry an `enabled` flag now? | Yes, defaults true, login already checks it |
| 9 | How are passwords hashed? | ✎ Plain `BCryptPasswordEncoder(12)` |
| 10 | Who verifies the JWT — gateway only, or downstream too? | Gateway only; downstream services know nothing about access rules → HS256 shared secret |
| 11 | What should logout actually guarantee? | Best-effort: one access token, logout clears the cookie |
| 12 | Where does the frontend sit? | React + TS SPA, docker compose behind Caddy for HTTPS |
| 13 | Single origin or split `app.`/`api.` subdomains? | Single origin — resolved by reading UrlShortener's Caddy/nginx/Vite config rather than asking |
| 14 | `SameSite` alone, or Spring's CSRF tokens too? | `SameSite=Strict` only; CSRF filter disabled deliberately |
| 15 | Is there a "who am I" endpoint? | Yes — `GET /api/v1/auth/me` |
| 16 | Does register also log the user in? | ✎ Auto-login — `201` + body + `Set-Cookie` |
| 17 | What do failed register / login return? | Register `409` explicit; login `401` generic |
| 18 | What validation does register enforce? | `@Email` + max 254; password 8–72; no composition rules |
| 19 | Hand-roll the JWT filter, or use Spring's? | Use Spring's built-in, pointed at the cookie |
| 20 | Load the user per request, or trust the claims? | Trust claims, no DB read; shorten the token to ~1h |
| 21 | Sliding renewal, or hourly re-login? | ✎ No renewal — hard 1h expiry |
| 22 | How do the e2e tests sequence against implementation? | Full catalog up front, implement in slices, `@Disabled` on unbuilt ones |
| 23 | What infrastructure lands this phase? | Postgres-only compose + env-driven config |
| 24 | Brute-force protection on login? | No — documented as a known gap |
| 25 | Which project docs does this repo get? | `CLAUDE.md` + `PLAN.md` + `DECISIONS.md` + `TESTING.md` |

**Decided without asking** (routine calls): entity `User` with `@Table(name = "users")`; cookie named
`access_token`; claims `sub`/`email`/`role`/`iat`/`exp`; logout returns `204` even unauthenticated; `/me`
reads claims rather than the database; package layout and layering copied from UrlShortener.

**Questions that needed re-asking** — #2 and #19 were first put in jargon ("Spring Cloud Gateway
flavours", "resource-server support") and had to be re-framed from first principles. Worth remembering
when writing the next set.
