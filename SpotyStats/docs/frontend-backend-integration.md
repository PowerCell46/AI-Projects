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
| Success | `/profile` | profile page route |
| Failure (denied consent, callback error, …) | `/login-error?reason=login_failed` | error page route |

After landing on `/profile`, the `SESSION` cookie is already set — just call the API.

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

All responses are JSON, camelCase. Timestamps are ISO 8601 in UTC (e.g. `2026-06-05T10:30:45.123Z`).

### `GET /api/me` — public

See §3.1.

```ts
interface CurrentUserResponse {
  authenticated: boolean;
  spotifyUserId: string | null;  // null when anonymous
  displayName: string | null;    // null when anonymous
}
```

### `GET /api/profile` — requires session

Fetches the user's profile **live from Spotify** (proxied through the backend, tokens
refreshed automatically server-side).

```ts
interface ProfileResponse {
  spotifyUserId: string;
  displayName: string | null;
  email: string | null;
  imageUrl: string | null;       // largest/first profile image, may be absent
}
```

Returns **401** with no meaningful body if there is no session → treat as "not logged in"
and show the sign-in screen.

*(That's the entire API surface today. History/stats/library endpoints will follow the same
conventions: `/api/...`, session cookie auth, `ApiError` on failure.)*

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

---

## 7. Quick reference

| What | Value |
|---|---|
| Backend port (dev) | `8080` (proxied behind Vite) |
| Frontend origin (dev) | `http://127.0.0.1:5173` — **not** `localhost` |
| Session cookie | `SESSION`, HttpOnly, 30-day server-side session |
| CSRF cookie → header | `XSRF-TOKEN` → `X-XSRF-TOKEN` |
| Login | full-page navigation to `/oauth2/authorization/spotify` |
| Post-login redirect | `/profile` (frontend route) |
| Failed-login redirect | `/login-error?reason=login_failed` (frontend route) |
| Logout | `POST /auth/logout` → `204`, needs CSRF header |
| Auth probe | `GET /api/me` (public) |
| Profile | `GET /api/profile` (session required) |
| Health check | `GET /actuator/health` (public) |
| Dates | ISO 8601, UTC |
| JSON naming | camelCase |
