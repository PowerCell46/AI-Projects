# Security audits

Last updated: 2026-10-10

The `exploit-hunter` audits of the tweet service, one section per audit, condensed from the original per-audit
reports (dates are the audit dates). Each "accepted" item is listed in `PLAN.md` under its accepted gaps, with a trigger.

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-09-30 | The five `/api/v1/tweets` routes, outbox publisher, MinIO, Mongo (end of step 9) | 4 accepted (1 High if reachable, 2 Low, 1 Info) |
| 2026-10-04 | `GET /internal/v1/tweets/by-author/{authorId}` (frontend phase 3) | 1 Medium and 1 Low fixed |
| 2026-10-07 | Phase 2: the four reply routes, cursor, conflict retrier, gateway user lookup (step 22) | 1 Low and 1 Info fixed |

## Tweet routes (2026-09-30)

- **Any caller who reaches port 8081 can act as any user (Low while only the gateway reaches it, High if anything else
  can), accepted.** Compose publishes no port for this service (the prod overlay publishes only the frontend). Identity is the
  `X-User-Id` header, trusted as sent, and `getHeader` returns the first value, so a proxy that appends instead of
  replacing would let a client value win. Safe only while the gateway strips `X-User-*` and sets one header (verified
  in gateway phase 4).
- **A large `content` text part is buffered in memory (Low), accepted.** The create cap is 21 MB and a non-file part
  has none of its own; Spring reads it into a `String` before `strip()` and the 280 code-point check reject it.
- **Up to 10 file parts are parsed before the 4-image limit applies (Low), accepted.** Tomcat's default part cap is
  10; the count check runs in the service. Bounded write amplification, nothing persisted.
- **Text keeps control and bidirectional characters (Low, informational), accepted.** JSON output encodes them, so
  only a consumer that renders `content` unescaped is exposed.
- **Clean:** Mongo queries use typed ids and fixed field names, and a log-forging probe stayed one line; no outbound
  calls from input; update and delete match `_id` and `authorId` in the write itself; image type comes from the
  bytes, images are never decoded, storage keys are random UUIDs; error bodies are fixed strings, `/actuator/env`
  is 404 and health shows only status; `.env` is git-ignored, MinIO keys have no default, the container is non-root.
  Dependencies were not scanned: run an advisory scan before any deployment.

## By-author read (2026-10-04)

- **The `_id` tie-break made the query read and sort every tweet of the author since `since` (Medium), fixed
  2026-10-04 (frontend step 24).** The index `{authorId: 1, createdAt: -1}` can't satisfy the `createdAt, _id`
  sort, so with 300,000 tweets by one author and `since=2000-01-01` Mongo examined 300,000 keys and documents
  (247 ms) for 100 rows; the same query sorted by `createdAt` alone examined 100. Anyone who can reach the port can
  send a wide `since`, and each follow triggers the read. The index is now `{authorId: 1, createdAt: -1, _id: -1}`
  and a repository test asserts at most `limit` keys and documents examined (`DECISIONS.md`).
- **A `since` outside `java.util.Date`'s range answered 500 and logged a stack trace (Low), fixed 2026-10-09.** Spring
  parses any ISO instant; the Mongo mapper fails converting years beyond the `long` millisecond range (for example
  `since=%2B292278994-08-17T07:12:56Z`), which was not a 400 like the other bad values. About 90 log lines per
  request; nothing leaked into the body. The real caller sends a validated event time minus 7 days. `since` outside
  1970-01-01 to 9999-12-31 now answers 400 in the `limit` shape; parameterized e2e test (`DECISIONS.md`).
- **Clean:** the response is exactly `{id, createdAt}` and at most 100 items; every bad `limit` and `since` is a 400
  (epoch millis refused by design); no string reaches the Mongo query; the gateway does not forward `/internal/**`
  (404 with or without its secret); only `GET` is allowed; error text carries no internals. No auth on `/internal/**`
  is by design (the `X-User-Id` gap), protected by network isolation only.

## Replies (2026-10-07)

Run against the built jar with throwaway Mongo, MinIO and a stub gateway. Not covered: the gateway's own limits and the
frontend beyond one grep (no `innerHTML` or `dangerouslySetInnerHTML` in `frontend/src`).

- **The 8 KB body cap did not apply to chunked multipart on any route (Low), fixed 2026-10-07 (phase 2, step 22).**
  The container parses multipart without going through the counting wrapper, so a chunked 20 MB upload was read in
  full on the reply routes, unmapped paths and `/internal/**`, before routing and the identity check. The body-size
  filter now refuses multipart with `415` on every route but the tweet create (`DECISIONS.md`); a unit test and a
  real-port test cover it.
- **Reply edit and delete answered `403` or `404`, confirming a reply id exists (Info), fixed 2026-10-07.** Both now
  answer `404 "Reply not found."` for someone else's reply, the same as a missing one. Reply ids are random UUIDs and
  the list shows them to everyone, so little was exposed; the tweet routes keep their `403`.
- **Not reproduced:** a reply flood starving the author's tweet delete (64 parallel clients, 1,524 replies in 6 s: the
  delete answered `204` in 60 ms, no `503 BUSY`).
- **Clean:** every Mongo value is typed and field names are fixed; the cursor is re-encoded and compared (bad sizes and
  cursors are `400`); with 300,000 replies on one tweet a deep page examined 21 keys; edit matches `_id`, `tweetId`
  and `authorId` in the write; the only outbound call is the gateway at a configured URL with UUID ids; error bodies
  are fixed strings and `/actuator` shows `health` only; `INTERNAL_API_SECRET` has no default and a 32-byte floor.
  Dependencies were not scanned.
