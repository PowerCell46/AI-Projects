# Security findings — frontend

Five audits ran between 2026-10-04 and 2026-10-08 (feed and compose; tab row and People; likes and `/liked`; tweet
details and tab panels; profile page). Read from code, plus `npm audit` (0 vulnerabilities on 2026-10-06). The Docker
stack and a browser were not used. Open findings are kept privately (`../security-private/`); full audit text is in git
history (`git log --follow -- frontend/SECURITY-FINDINGS.md`). Backend audits live in each service's
`docs/SECURITY-AUDITS.md` and in `../SECURITY_AUDITS_ROOT.md`.

## Fixed

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | Low | No Content-Security-Policy and no referrer policy; Google Fonts stylesheet could restyle the login form and receive the `/confirm?token=…` URL as `Referer` | 2026-10-10: referrer meta in `index.html`, CSP in nginx, self-hosted fonts |
| 3, 6 | Low / Informational | Ids and the `/tweets/:tweetId` address were spliced into API paths unencoded (a `..` in the address reached other `/api/` routes as an authenticated `GET`) | 2026-10-07: `useOpenTweetId` accepts only a UUID and `TabPanels` redirects anything else to `/feed`; `ENDPOINTS` encodes every path segment (`segment()`); `ProfilePage` calls the API only for names matching `USERNAME_PATTERN` |

## Confirmed not a hole

- **Route guards and client-side upload checks are UX, not enforcement (4).** The server enforces the 280-character,
  four-image, 5 MiB and MIME limits and checks the content type from magic bytes, so a forged `Content-Type` changes
  nothing stored or served.
- No `dangerouslySetInnerHTML`, `innerHTML` or `localStorage` in `frontend/src`; the session cookie is `HttpOnly` and
  `SameSite=Strict`; picture URLs are mapped at the API boundary; the 401 handler signs out and lands on `/login`.

## Still open

| # | Severity | Finding | Notes |
|---|---|---|---|
| 2 | Low | Gateway session cookie `Secure` defaults to off | Default to `true` before any deployment |
| 3 | Informational | `backendPath` (takes any path) and the client-side `tweetPath` stay unencoded | Ids are encoded in `ENDPOINTS` |
| 5 | Low | `usePagedList` follows a cursor with no progress check; a server returning empty pages with a cursor that never advances makes it refetch forever | Not reachable against the real gateway |
| — | Medium | No rate limit on proxied read routes (one `author-tweets?size=100` request fans out to three services) | Decided 2026-10-10: not planned; see `PLAN.md` accepted gaps |
| — | Low | `PATCH /users/me` accepts a `birthdate` outside Postgres's range and `\u0000` in `bio` / `location` (fails as `409` / `500`, not `400`); the birthdate is visible to every logged-in user (the form should say so) | Add a lower bound and a control-character check |
| — | Low | Tweet and timeline services trust the network for `/internal/**` and `X-User-Id` | Network-trust trigger |
| — | Low | Timeline: retry after an unfollow re-inserts rows; no per-pair dampening of follow toggling; `FEED_BACKFILL_SIZE` above 100 dead-letters every follow; a far-past `occurredAt` skips the 7-day window; a forged self-follow event deletes the user's own tweets from their feed | See `twitter_timeline_service/docs/SECURITY-AUDITS.md` |
