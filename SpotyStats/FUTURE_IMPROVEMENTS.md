# Future Improvements

## Scheduled background sync for plays (high priority)

**Problem:** `syncRecentlyPlayed` is only triggered by the frontend
(`POST /api/listening/sync`), i.e. when a user actually opens the app.
Spotify's `/me/player/recently-played` buffer holds only the **last 50
plays** (~2.5–3 hours of listening). A user who listens all day and opens
SpotyStats the next morning silently loses everything except the most
recent ~50 plays — unrecoverable, since Spotify has no historical-plays
API. For an app whose core value is complete listening history, this is
the weakest link in the persistence setup.

**Fix:** a `@Scheduled` job (every ~20–30 min) that iterates users with
stored tokens and syncs each. Everything it needs already exists:

- Refresh tokens are persisted server-side in `spotify_authorized_client`
  (via `EncryptedJdbcOAuth2AuthorizedClientService`), so the backend can
  act for users who aren't present.
- The sync is idempotent — plays dedup on `(user_id, played_at)` with a
  unique constraint plus `insertIfAbsent` — so overlap between the
  scheduled job and user-triggered syncs is harmless.

## Minor / only if scale ever demands it

- **Batch the sync upserts.** Each play currently does several
  `findById` + `save` round-trips; a 50-item sync issues a few hundred
  queries. Irrelevant at current scale; if it matters later, switch the
  catalog upserts to batched `INSERT ... ON CONFLICT` statements.

---

# Security

The architecture is sound (BFF: tokens never reach the browser, opaque
session cookie, CSRF wired end-to-end, no CORS by design, default-deny
authorization, refresh tokens encrypted at rest, parameterized SQL, no
open redirects). The items below are production-hardening and hygiene,
not design fixes — in priority order.

## 1. Session cookie flags for production (before deploying)

Nothing sets `Secure` on the session cookie. Fine on `http://127.0.0.1`,
but in production a non-Secure cookie is also sent over plain HTTP, where
it can be read in transit — and the session cookie *is* the user's
identity. Add to a prod profile:

```properties
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.same-site=lax
```

(Lax is already the default; pinning it makes the intent explicit.)

## 2. The same-origin assumption must survive deployment (before deploying)

Dev relies on the Vite proxy to make SPA + API one origin. Production
needs the equivalent: a reverse proxy (nginx/Caddy) serving the SPA and
routing `/api`, `/auth`, `/login`, `/oauth2` to the backend on a single
domain. If SPA and API end up on different origins, cookies and CSRF
break — and the tempting "fix" (wide-open CORS with credentials) would
undo the BFF pattern's main benefit. Do not "solve" a broken deployment
with CORS.

## 3. Disconnect / delete-account flow

Logout invalidates the session, but the encrypted refresh token stays in
`spotify_authorized_client` indefinitely, and the user's plays stay too.
Keeping tokens after logout is *required* for the scheduled background
sync — but there should still be an explicit "disconnect SpotyStats"
action that calls `removeAuthorizedClient` and purges the user's data.
Both a privacy/GDPR-shaped concern and basic hygiene: don't hold
credentials for users who've left.

## 4. Rate-limit `POST /api/listening/sync`

Authenticated users can hammer it, and each call hits Spotify upstream.
Spotify rate limits are **per client ID**, so one abusive user degrades
the app for everyone. Cheap fix: skip the upstream call if that user
already synced within the last N seconds. Becomes more relevant once the
scheduled sync exists.

## 5. Accepted trade-offs (known, no action planned)

- **`spring.session.timeout=30d`** — long, but defensible for a consumer
  dashboard. It means a stolen session cookie is valid for up to a month.
- **No encryption key rotation.** Changing `TOKEN_ENCRYPTION_PASSWORD`
  makes every stored refresh token undecryptable. Blast radius is
  "everyone logs in again," not data loss — acceptable.
- **Access tokens stored unencrypted.** They expire within the hour;
  only the long-lived refresh token warrants encryption at rest.
