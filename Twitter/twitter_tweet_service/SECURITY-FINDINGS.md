# Security findings

Raw `exploit-hunter` reports of the tweet service. Condensed history of earlier audits: `SECURITY-AUDITS.md`.

## Audit — 2026-10-07

**Scope:** phase 2 (replies) since the 2026-10-04 audit: `/api/v1/tweets/{id}/replies` (4 routes), the cursor codec,
`ConflictRetrier`, the gateway user lookup, `replyCount`, plus the request-size filter every route passes through.
Run against the built jar (`mongo:8.2` replica set, MinIO, a stub gateway, all throwaway containers on other ports).
Not covered: the gateway's own limits, the frontend beyond one grep for raw-HTML sinks, a dependency advisory scan.

### 1. The 8 KB body cap does not apply to chunked multipart on any route (Low)

- **Location:** `configurations/RequestBodySizeLimitFilter.java:63-71`, `utilities/BodySizeLimitingRequestWrapper.java:30-37`.
- **Mechanism:** the filter caps a declared `Content-Length`, and for chunked bodies wraps the request so
  `getInputStream()` / `getReader()` count bytes. Multipart is parsed by `getParts()`, which the wrapper delegates to
  the container, so the counting stream is never used. A chunked `multipart/form-data` POST is therefore parsed up to
  Spring's multipart limits (5 MB per file, 21 MB per request) on **every** path: reply routes, unmapped paths and
  `/internal/**`, before routing and before the `X-User-Id` check. File parts go to temp disk (`file-size-threshold`
  is 0). The filter's javadoc ("every other route keeps the small one") is not true for this body type. This is
  distinct from the accepted create-route gaps: those are routes that are meant to take 21 MB for an identified caller.
- **Demonstration** (no identity header; 4 files of 5,000,000 bytes `five.bin`):
  ```
  curl -X POST localhost:8081/api/v1/tweets/<id>/replies -F images=@five.bin -F images=@five.bin -F images=@five.bin -F images=@five.bin
  → 413 "The request body is too large."          (declared length: cap works)
  curl -X POST ... -H 'Transfer-Encoding: chunked' <same four -F>
  → 400 "Missing or invalid caller identity."     (all 20,003,511 bytes uploaded and parsed)
  curl -X POST localhost:8081/api/v1/nothing -H 'Transfer-Encoding: chunked' <same four -F>
  → 404, 20,003,428 bytes uploaded
  ```
  A single 15 MB file part is stopped at 5 MB (`413 The uploaded file is too large`), so the per-file cap holds.
- **Reach:** only whoever can reach port 8081, bounded to 21 MB per request. Low while the gateway fronts it.

### 2. Reply edit and delete answer 403 vs 404, revealing that a reply id exists on this tweet (Low, informational)

- **Location:** `services/implementations/replies/ReplyServiceImpl.java:187-194` (edit), `:123-129` (delete).
- **Mechanism:** when the conditional edit matches nothing, a reply that exists on this tweet answers `403`
  ("You can only edit your own replies."), anything else `404`. Delete of another user's reply on a tweet that is not
  the caller's answers `403` for the same reason. Same pattern as the tweet routes. Reply ids are random UUIDs, so
  there is nothing to guess; it only confirms an id obtained elsewhere.
- **Demonstration** (read from the code, not executed): `PUT /api/v1/tweets/<tweet>/replies/<reply by someone else>`
  with a valid `X-User-Id` and `{"content":"x"}` → `403`; the same call with a random UUID → `404`.

### Still open from earlier audits (not re-added)

- `since` outside `java.util.Date`'s range on `GET /internal/v1/tweets/by-author/{id}` still answers `500`:
  `curl 'localhost:8081/internal/v1/tweets/by-author/<uuid>?since=%2B292278994-08-17T07:12:56Z&limit=1'` → 500
  (`SECURITY-AUDITS.md`, 2026-10-04; accepted in `PLAN.md`).

### Tried and not reproduced

- **A reply flood starving the author's own tweet delete** (the hot-document gap in `PLAN.md`). 64 parallel clients
  posted replies as distinct users (1,524 `201`s in 6 s) while the author deleted the tweet: `204` in 60 ms, and no
  `503 BUSY` appeared. The 658 `502`s came from the stub gateway, not the service.

### Clean

- **Injection:** every Mongo value is a typed `UUID`, `Instant` or the trimmed content as a `$set` value; field names
  are fixed. The cursor is base64 of `micros:uuid`, re-encoded and compared, so no other form gets in. Probes:
  18-digit micros → 200, 21 digits, negative, `5:5`, a 7 KB cursor → 400; sizes `0`, `101`, `-1`, `abc`, `1.5`,
  `99999999999` → 400. A log-forging probe (`%0d%0a` in a path) stayed one log line.
- **Keyset query plan:** with 300,000 replies on one tweet, a page 1,000 rows from the end examined 21 keys and 21
  documents (`ix_replies_tweet_created_id`), the same as the first page. No repeat of the by-author finding.
- **Access control:** reply edit matches `_id`, `tweetId` and `authorId` in the write itself; delete compares the caller
  with the reply and tweet authors, both immutable, so no check-then-act gap; a reply of another tweet is `404`.
- **SSRF / redirect:** the only outbound call is the gateway at a configured URL with UUID-typed ids; the gateway's id
  cap (100) equals the page-size cap, so a full page does not fail.
- **Information disclosure:** error bodies are fixed strings; `/actuator` lists `health` only, `/actuator/env` and
  `/heapdump` are 404, `/v3/api-docs` and `/swagger-ui.html` are 404. `/error` answers a bare 500 with no detail.
- **Secrets / CORS:** `INTERNAL_API_SECRET` has no default and a 32-byte floor; no CORS config; no cookies.
- **Races:** reply create/delete run in a transaction with the `replyCount` `$inc`, retried on write conflict with a
  2 s budget; a tweet deleted mid-create fails the `$inc` and rolls the reply back.
- **N/A:** browser-side attacks (JSON only, no HTML or cookies; one grep of `frontend/src` found no `innerHTML` or
  `dangerouslySetInnerHTML`), file uploads on the reply routes.
- **Not scanned:** dependency advisories (Boot 4.1.1, `minio`, `okhttp-jvm 5.3.2`); run a scan before any deployment.
- **Also open from the accepted list, unchanged:** no rate limit on replies, a large tweet delete is one transaction,
  X-User-Id trusted as sent.
