# Frontend ↔ Backend Integration Guide

Everything the React SPA needs to know to talk to the SpotyStats backend.

---

## 1. The big picture (read this first)

The backend uses a **Backend-for-Frontend (BFF)** pattern (see [ADR 0001](adr/0001-bff-confidential-client.md)):

- The browser **never sees a Spotify token**. All Spotify API calls go through the backend.
- After login, the browser holds exactly one thing: an opaque, HttpOnly **`SESSION` cookie**.
  Think of it as a hotel keycard — it proves who you are to the backend, but contains no
  useful data itself, and JavaScript cannot read it.
- The frontend therefore does **no OAuth logic at all**. No PKCE, no token refresh, no
  `Authorization` headers. It just navigates to a login URL and calls JSON endpoints;
  the cookie rides along automatically.

**Session lifetime:** 30 days (server-side, stored in Postgres). Survives backend restarts.

---

## 2. Dev environment setup — the Vite proxy is mandatory

The backend has **no CORS configuration**, on purpose. The browser must believe the SPA and
the API live on the **same origin**. In development that origin is `http://127.0.0.1:5173`
(the Vite dev server), and Vite must proxy backend paths to `http://127.0.0.1:8080`.

This is not optional: the Spotify OAuth callback is registered as
`http://127.0.0.1:5173/login/oauth2/code/spotify` — i.e. Spotify redirects the browser back
to the **frontend** port, and the proxy must hand that request to the backend.

```ts
// vite.config.ts
export default defineConfig({
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api':     'http://127.0.0.1:8080',
      '/auth':    'http://127.0.0.1:8080',
      '/login':   'http://127.0.0.1:8080',  // includes the OAuth callback
      '/oauth2':  'http://127.0.0.1:8080',
    },
  },
})
```

> ⚠️ **Always use `http://127.0.0.1:5173` in the browser, never `localhost:5173`.**
> Cookies are scoped per host, and the Spotify redirect URI is registered for `127.0.0.1`.
> If you log in via `127.0.0.1` but browse via `localhost`, the session cookie won't be sent
> and every request will look anonymous.

Reserved path prefixes (the SPA router must **not** own these): `/api`, `/auth`, `/login`,
`/oauth2`, `/error`, `/actuator`.

Because everything is same-origin, plain `fetch` works with no special config —
cookies are included on same-origin requests by default. No `credentials: 'include'`,
no CORS headers, no preflight.

---

## 3. Authentication flows

### 3.1 Check who's logged in (app startup)

`GET /api/me` — public, never fails with 401.

```json
// logged in
{ "authenticated": true,  "spotifyUserId": "abc123", "displayName": "Peter" }

// anonymous
{ "authenticated": false, "spotifyUserId": null,     "displayName": null }
```

Call it once on app load to decide between "Sign in with Spotify" and the logged-in UI.

### 3.2 Log in

A **full-page navigation** (not `fetch`!) to:

```
/oauth2/authorization/spotify
```

e.g. `<a href="/oauth2/authorization/spotify">Sign in with Spotify</a>` or
`window.location.href = '/oauth2/authorization/spotify'`.

It must be a real navigation because the backend responds with redirects to
`accounts.spotify.com` and back — an XHR/fetch can't follow a cross-site login dance.
Spotify always shows its account-chooser/consent screen (`show_dialog=true`).

When the flow finishes, the backend redirects the browser to a **frontend route**:

| Outcome | Browser lands on | SPA must provide |
|---|---|---|
| Success | `/overview` | overview (dashboard) route |
| Failure (denied consent, callback error, …) | `/login-error?reason=login_failed` | error page route |

After landing on `/overview`, the `SESSION` cookie is already set — just call the API.

### 3.3 Log out

```
POST /auth/logout
```

- Requires the CSRF header (see §5).
- Returns **204 No Content** — no body to parse.
- Invalidates the server session and deletes the `SESSION` cookie.
- Afterwards, reset client state and route to the landing page.

---

## 4. API endpoints

All responses are JSON, camelCase. Timestamps are ISO 8601 in UTC (e.g. `2026-06-05T10:30:45.123Z`);
calendar dates are ISO `yyyy-MM-dd`. Every endpoint except `/api/me` requires a session and
returns **401** without one. Mutating endpoints (`POST`) need the CSRF header (§5).

**Common query params:**

- `zone` — an IANA time zone (e.g. `Europe/Sofia`) used to bucket plays into calendar days.
  Defaults to `UTC`; invalid values silently fall back to UTC. Pass
  `Intl.DateTimeFormat().resolvedOptions().timeZone`.

### `GET /api/me` — public

See §3.1.

```ts
interface CurrentUserResponse {
  authenticated: boolean;
  spotifyUserId: string | null;  // null when anonymous
  displayName: string | null;    // null when anonymous
}
```

### `GET /api/profile?zone` — requires session

Spotify account details (live from Spotify, tokens refreshed server-side) plus what
SpotyStats has recorded about the user's listening.

```ts
interface ProfileResponse {
  spotifyUserId: string;
  displayName: string | null;
  email: string | null;
  imageUrl: string | null;       // largest/first profile image, may be absent
  country: string | null;        // ISO 3166-1 alpha-2, e.g. "BG"
  product: string | null;        // Spotify tier, e.g. "premium"
  followers: number | null;
  totals: ListeningTotals;
}

interface ListeningTotals {       // all-time aggregates over our recorded plays
  totalPlays: number;
  totalListeningTimeMs: number;
  uniqueArtists: number;
  uniqueTracks: number;
  trackingSince: string | null;  // date of the earliest recorded play (in `zone`)
  likedTotal: number | null;     // live from Spotify; null when Spotify is unavailable
}
```

### `POST /api/listening/sync` → 204

Pulls the user's new recently-played tracks from Spotify into our DB (on-visit sync,
[ADR 0002](adr/0002-persisted-history-on-visit-sync.md)). Call it when a listening page
mounts, **before** fetching the read endpoints below. Idempotent — already-stored plays
are deduplicated.

### `GET /api/listening/today?zone`

Today's plays (in the caller's time zone), newest first.

```ts
interface DailyHistoryResponse {
  date: string;                  // calendar date in `zone`
  tracks: PlayedTrackResponse[];
}

interface PlayedTrackResponse {
  id: string;        // the play's DB id — unique per listen, safe as a React key
  trackId: string;   // Spotify track id — use for the liked toggle
  title: string;
  artist: string;
  album: string;
  albumArtUrl: string | null;
  playedAt: string;  // ISO 8601 UTC
  durationMs: number;
  liked: boolean;    // computed live from Spotify (ADR 0003); false if Spotify is unavailable
}
```

### `GET /api/listening/history?zone&before`

The listening diary, paged: up to a week of days (newest first) per page.

- `before` (optional, ISO date) — exclusive upper bound; omit for the first page.

```ts
interface HistoryPageResponse {
  days: DailyHistoryResponse[];
  nextBefore: string | null;     // pass as `before` for the next page; null = nothing older
}
```

`nextBefore` is already positioned past any play-free gap — just feed it back verbatim.

### `GET /api/listening/stats?period&zone`

Stat-card aggregates for the toggled window. `period` = `week` (default, rolling 7 days)
| `today` (since midnight in `zone`).

```ts
interface PeriodStatsResponse {
  tracksPlayed: number;
  tracksPlayedDeltaPercent: number | null;  // vs the equally long window before
                                            // (yesterday same-time / prior week);
                                            // null when no baseline
  listeningTimeMs: number;
  uniqueArtists: number;
  newArtists: number;                       // artists first heard this period
  uniqueTracks: number;
}
```

### `GET /api/listening/artist-breakdown?period&zone`

Artist share of recent listening (the donut), attributed by primary artist. Takes the
same `period` / `zone` as `/stats`.

```ts
type ArtistBreakdown = ArtistShareResponse[];

interface ArtistShareResponse {
  artistName: string;
  trackCount: number;
  listeningTimeMs: number;
}
```

### `GET /api/listening/artists?period`

Artist ranking. `period` = `week` (default) | `month` — ranked from our captured plays —
or `all`, served from Spotify's long-term top artists (which reach back further than our
own history can).

```ts
interface ArtistRankingResponse {
  trackedSince: string | null;   // earliest play we ever captured (ISO 8601 UTC);
                                 // periods reaching further back are necessarily incomplete
  artists: ArtistRankResponse[];
}

interface ArtistRankResponse {
  artistId: string;
  artistName: string;
  imageUrl: string | null;
  playCount: number | null;        // ┐ null when period=all — Spotify only
  listeningTimeMs: number | null;  // ├ shares the order, not the metrics
  uniqueTracks: number | null;     // ┘
  followed: boolean;               // live from Spotify; false if Spotify is unavailable
}
```

### `GET /api/listening/insights?zone`

Aggregated listening patterns for the Insights page.

```ts
interface InsightsResponse {
  hourlyActivity: { hour: number; plays: number }[];          // 0-23, all recorded plays
  weekdayActivity: { isoWeekday: number;                      // 1 = Monday … 7 = Sunday
                     plays: number; listeningTimeMs: number }[];
  weeklyTrend: { weekStart: string;                           // ISO date
                 plays: number; listeningTimeMs: number }[];  // recent weeks
  topTracks: { trackId: string; title: string; artist: string;
               albumArtUrl: string | null;
               playCount: number; listeningTimeMs: number }[];
}
```

### `POST /api/listening/tracks/{trackId}/liked` → 204

Save or remove a track from the user's Spotify library. Body: `{ "liked": boolean }`
(required). Needs the CSRF header.

### `POST /api/listening/artists/{artistId}/followed` → 204

Follow or unfollow an artist on the user's Spotify account. Body: `{ "followed": boolean }`
(required). Needs the CSRF header.

### `GET /api/liked?limit&offset`

The user's Liked Songs library, fetched live from Spotify in Spotify's order (most
recently added first). `limit` defaults to 50 (clamped to 1–50), `offset` defaults to 0.

```ts
interface LikedPageResponse {
  total: number;                 // library-wide count, for pagination
  limit: number;
  offset: number;
  items: LikedTrackResponse[];
}

interface LikedTrackResponse {
  trackId: string;
  title: string;
  artist: string;
  album: string;
  albumArtUrl: string | null;
  addedAt: string;               // ISO 8601 UTC — when the user liked the track
  durationMs: number;
}
```

---

## 5. CSRF protection

Because auth is cookie-based, the backend requires a CSRF token on every
**state-changing** request (`POST`/`PUT`/`PATCH`/`DELETE`). `GET`s are exempt.

How it works:

1. The backend sets a cookie named **`XSRF-TOKEN`** (readable by JS — this one is *meant*
   to be read). It appears after your first request to the backend, e.g. `GET /api/me`.
2. On every mutating request, echo it back as a header named **`X-XSRF-TOKEN`**.

This proves the request came from a page that can read our cookies, i.e. our own origin —
that's the whole trick.

- **axios** does this automatically (its defaults are exactly `XSRF-TOKEN` → `X-XSRF-TOKEN`).
- **fetch** needs a small helper:

```ts
const readCookie = (name: string): string | null =>
  document.cookie
    .split('; ')
    .find((row) => row.startsWith(`${name}=`))
    ?.split('=')[1] ?? null;

export const apiPost = (url: string, body?: unknown): Promise<Response> =>
  fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': readCookie('XSRF-TOKEN') ?? '',
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
```

A missing/wrong token yields **403 Forbidden** — if logout suddenly 403s, this is why.

---

## 6. Error responses

All errors (from any endpoint) share one shape, produced by the global exception handler:

```ts
interface ApiError {
  timestamp: string;  // ISO 8601 UTC
  status: number;     // mirrors the HTTP status
  error: string;      // machine-readable code — switch on this
  message: string;    // human-readable, safe to log; prefer your own copy in the UI
}
```

| HTTP | `error` code | Meaning | Frontend reaction |
|---|---|---|---|
| 401 | `not_authorized` | No active Spotify session | Show sign-in screen |
| 401 | `spotify_unauthorized` | Spotify revoked/expired our authorization | Prompt re-login |
| 502 | `spotify_error` | Spotify API call failed (their side) | "Spotify is unavailable, try again" |
| 500 | `internal_error` | Unexpected backend error | Generic error message |

Note: a bare **401 from Spring Security itself** (e.g. hitting `/api/profile` with no
session) may arrive with an empty body — handle `response.status === 401` first, then try
to parse `ApiError` for the code.

**Graceful degradation:** decorative data fetched live from Spotify does *not* fail the
whole request when Spotify hiccups — instead the affected fields degrade:
`ProfileResponse.totals.likedTotal` becomes `null`, and `liked` / `followed` flags fall
back to `false`. Don't treat `liked: false` as proof the track isn't saved if you need
certainty at that moment.

---

## 7. Quick reference

| What | Value |
|---|---|
| Backend port (dev) | `8080` (proxied behind Vite) |
| Frontend origin (dev) | `http://127.0.0.1:5173` — **not** `localhost` |
| Session cookie | `SESSION`, HttpOnly, 30-day server-side session |
| CSRF cookie → header | `XSRF-TOKEN` → `X-XSRF-TOKEN` |
| Login | full-page navigation to `/oauth2/authorization/spotify` |
| Post-login redirect | `/overview` (frontend route) |
| Failed-login redirect | `/login-error?reason=login_failed` (frontend route) |
| Logout | `POST /auth/logout` → `204`, needs CSRF header |
| Auth probe | `GET /api/me` (public) |
| Profile | `GET /api/profile?zone` (session required) |
| Sync (call before reads) | `POST /api/listening/sync` → `204` |
| Listening reads | `GET /api/listening/{today,history,stats,artist-breakdown,artists,insights}` |
| Liked Songs library | `GET /api/liked?limit&offset` |
| Save/unsave track | `POST /api/listening/tracks/{trackId}/liked` `{"liked": bool}` → `204` |
| Follow/unfollow artist | `POST /api/listening/artists/{artistId}/followed` `{"followed": bool}` → `204` |
| Health check | `GET /actuator/health` (public) |
| Dates | ISO 8601, UTC |
| JSON naming | camelCase |
