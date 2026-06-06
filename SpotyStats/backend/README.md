# SpotyStats Backend

Spring Boot 4 (Java 25) Backend-for-Frontend for the SpotyStats app. See the repo-root
[`PLAN.md`](../PLAN.md), [`CONTEXT.md`](../CONTEXT.md), and [`docs/adr/`](../docs/adr/) for
the design and decisions.

## What's implemented so far

A working vertical slice: **log in with Spotify → server-side session → fetch your profile
through the backend**.

- BFF security with Spring Security OAuth2 Client (`oauth2Login`), confidential client + PKCE,
  `show_dialog=true` always shown.
- Server-side session (Spring Session JDBC, opaque `SESSION` cookie); tokens never reach the browser.
- Refresh tokens **encrypted at rest** (`EncryptedJdbcOAuth2AuthorizedClientService`).
- Flyway schema for the full domain model + Spring Session tables.
- Endpoints: `GET /api/me` (auth state), `GET /api/profile` (live `/me` via the token proxy),
  `POST /auth/logout`, plus the Spring login endpoints (`/oauth2/authorization/spotify`, `/login/oauth2/code/spotify`).

Still to come (next turns): recently-played sync + history, liked/save-unsave, stats, charts data.

## Prerequisites

- **JDK 25** (this repo expects `C:\Users\HP ZBook 17 G5\.jdks\openjdk-25`).
- **Docker** (for Postgres) and **Maven**.
- A **Spotify app** from the [developer dashboard](https://developer.spotify.com/dashboard) with
  this Redirect URI registered (dev): `http://127.0.0.1:5173/login/oauth2/code/spotify`

## Configuration

Copy the root [`.env.example`](../.env.example) to `.env` and fill it in (Spotify client id/secret,
encryption password/salt). Export those variables into your shell before running, or set them in
your IDE run config. **Never commit `.env`.**

## Run (local dev)

```powershell
# 1. Postgres
docker compose up -d            # from repo root

# 2. Backend (JAVA_HOME must point at JDK 25)
$env:JAVA_HOME = "C:\Users\HP ZBook 17 G5\.jdks\openjdk-25"
cd backend
mvn spring-boot:run
```

> The frontend (Vite) will proxy `/api`, `/auth`, `/oauth2`, and `/login` to `http://localhost:8080`
> so the browser sees a single origin. Until the frontend exists, you can hit the login flow directly
> by registering `http://127.0.0.1:8080/login/oauth2/code/spotify` instead and browsing to
> `http://127.0.0.1:8080/oauth2/authorization/spotify`.

## Verify the slice

1. `mvn -q clean verify` — compiles and packages (no integration tests yet).
2. Browse to the login endpoint; approve on Spotify; you should be redirected to the (not-yet-built)
   frontend `/profile`.
3. `GET http://localhost:8080/api/me` → `{"authenticated":true,...}` once logged in.
4. `GET http://localhost:8080/api/profile` → your Spotify name/email/picture (proves the token proxy).

## Notes / follow-ups

- **CSRF**: configured with `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`).
  The SPA must echo the cookie value back as the header on state-changing requests. A small filter to
  force token issuance on first load will be added with the frontend.
- **Tests**: the default context-load test was removed; Testcontainers-based tests arrive in the
  testing phase (Postgres-specific Flyway migrations need a real Postgres).
