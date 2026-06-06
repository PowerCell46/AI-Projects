# SpotyStats — Execution Plan

A web app that accumulates a user's Spotify listening activity over time and visualizes how their music fits into their life across days, seasons, and life phases.

See [CONTEXT.md](./CONTEXT.md) for the domain glossary and [docs/adr/](./docs/adr/) for the recorded architectural decisions.

## Architecture at a glance

**Backend-for-Frontend (BFF)** — the Spring backend is a *confidential* OAuth client that runs the entire Spotify Authorization Code + PKCE flow. The browser never sees a Spotify token; it holds only an opaque, HTTP-only session cookie. All Spotify calls are proxied through the backend. ([ADR-0001](./docs/adr/0001-bff-confidential-client.md))

**Persisted, on-visit-synced history** — we store plays in our own Postgres DB and accumulate them over time, syncing only while the user is actively using the app. ([ADR-0002](./docs/adr/0002-persisted-history-on-visit-sync.md))

**Liked status is computed live**, never snapshotted per play. ([ADR-0003](./docs/adr/0003-liked-status-computed-live.md))

```
Browser (React) ──opaque HTTP-only cookie──► Spring BFF ──tokens──► Spotify API
                  one origin (Vite proxy / same domain)   │
                                                           ▼
                                                       Postgres
```

## Tech stack

| Layer | Choice |
|---|---|
| Frontend | React + TypeScript (latest), **Vite**, React Router, **TanStack Query**, **Recharts** |
| Styling | **CSS Modules** per component + shared `theme.css` (CSS custom properties) + `global.css` reset |
| Backend | **Java 25**, Spring Boot (latest), **Maven** |
| Backend libs | Spring Web, Spring Security + OAuth2 Client, Spring Session JDBC, Spring Data JPA, Flyway |
| DB | **PostgreSQL** (source of truth; Redis optional cache later) |
| Dev | Postgres via Docker; `mvn spring-boot:run` + `npm run dev` natively; Vite proxies `/api` & `/auth` → backend (one origin) |
| Deploy | Dockerfiles (frontend, backend) + `docker-compose.yml` (frontend, backend, postgres) |

## Frontend conventions

Each component in its own directory:
```
src/components/TrackCard/
  TrackCard.tsx
  TrackCard.module.css
  TrackCard.ts        (optional logic)
  index.ts            (optional barrel)
```
Shared styling lives only in `src/styles/theme.css` (colors, spacing, fonts, breakpoints as CSS vars) and `src/styles/global.css` (reset). Components reference vars via `var(--…)`.

## Data model (normalized)

```
app_user(id, spotify_user_id, display_name, email, image_url)
artist(spotify_id, name)
album(spotify_id, name, cover_url)
track(spotify_id, name, duration_ms, popularity, album_id, primary_artist_id)
track_artist(track_id, artist_id, position)     -- all credited artists, for display
play(id, user_id, track_id, played_at)           -- UNIQUE(user_id, played_at)
-- session/token storage: Spring Session JDBC tables + a token table
--   (access token + refresh token encrypted at rest, keyed to user/session)
```
- **De-dup / incremental sync:** unique `(user_id, played_at)`; pass Spotify the `after` cursor = last stored `played_at` so we only pull new plays.
- **Display vs grouping:** history cards show *all* artists (`track_artist`); charts group by `primary_artist_id` only.
- **popularity:** latest value seen at sync (drifts over time; display-only).

## Spotify OAuth — scopes & config

Scopes requested at login: `user-read-email`, `user-read-private`, `user-read-recently-played`, `user-library-read`, `user-library-modify` (save/unsave).

Authorize request includes PKCE params **and** `show_dialog=true` (always show the consent/account-chooser screen).

Secrets via env vars / `.env` (never committed): `SPOTIFY_CLIENT_ID`, `SPOTIFY_CLIENT_SECRET`, `SPOTIFY_REDIRECT_URI`, `TOKEN_ENCRYPTION_KEY`, DB credentials.

Callback error handling: on Spotify error params or failed exchange, redirect to a friendly frontend error page with a message (no stack traces leaked).

## API surface (frontend ↔ backend; all Spotify access is server-side)

| Method | Path | Purpose |
|---|---|---|
| GET | `/auth/login` | Redirect to Spotify authorize (PKCE + `show_dialog=true`) |
| GET | `/auth/callback` | Exchange code, create session, redirect to `/profile` (handles errors) |
| POST | `/auth/logout` | Invalidate session |
| GET | `/api/me` | Current auth state + basic profile (drives header button) |
| GET | `/api/profile` | Name, email, picture |
| GET | `/api/history?from&to&grouping&cursor` | Synced + grouped plays, paged; includes liked status |
| PUT/DELETE | `/api/tracks/{id}/saved` | Save / unsave a track |
| GET | `/api/stats/liked-percentage?from&to` | Distinct liked ÷ distinct played |
| GET | `/api/stats/artists?from&to&metric` | Top-N artist aggregates + "Other" (pie) |
| GET | `/api/stats/trends?from&to&metric&grouping` | Per-bucket trend (area/line) |

`metric` = `count` | `time`. History fetch triggers a sync first (on-visit).

## Feature scope (v1)

1. "Sign in with Spotify" header button (only when logged out) + always-consent + callback error handling
2. Profile page (name, email, picture); redirect here immediately after login
3. Listening history page — persisted/accumulating; day/week/month grouping toggle (default day); default last 30 days + date-range navigation + paged loading; cards: name, duration, popularity, artist(s), album name + cover, played-at timestamp
4. Liked indicator + **save/unsave**
5. Liked-percentage stat (distinct liked ÷ distinct played, live; refreshes after save/unsave)
6. Two artist charts: donut share + area/line trend; track-count/listening-time toggle; Top-N + "Other"
7. Loading indicators everywhere data loads; responsive layouts

## Execution phases

- **Phase 0 — Scaffolding:** repo dirs (`frontend/`, `backend/`); Spring Initializr (Maven, Java 25, deps above); Vite React-TS; `docker-compose.yml` with Postgres; Flyway baseline; `theme.css` + `global.css`; Vite proxy config.
- **Phase 1 — Auth/BFF:** register Spotify app; OAuth login/callback with PKCE; Spring Session (Postgres) + token table with refresh token encrypted at rest; `/api/me`, logout; FE login button, auth state, post-login redirect to profile.
- **Phase 2 — Profile page.**
- **Phase 3 — Sync + history:** recently-played fetch, dedup, normalized upsert, incremental `after` cursor; history endpoint (grouping/range/paging); FE history page (cards, grouping toggle, date range, loaders).
- **Phase 4 — Liked + save/unsave:** batched saved-check (50/req), indicators, save/unsave, liked-% stat with cache invalidation.
- **Phase 5 — Charts:** artist-aggregate + trend endpoints; Recharts donut + area; metric toggle; Top-N/Other.
- **Phase 6 — Polish:** error handling, responsiveness, code docs, **light tests** (sync/dedup, aggregations, token refresh, auth endpoints; a couple FE logic tests), Dockerfiles + compose for deploy.
