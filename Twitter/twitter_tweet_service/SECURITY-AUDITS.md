# Security audits

The `exploit-hunter` audits of the tweet service, one section per audit, condensed from the original per-audit
reports (dates are the audit dates). Each "accepted" item is listed in `PLAN.md` under its accepted gaps, with a trigger.

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-09-30 | The five `/api/v1/tweets` routes, outbox publisher, MinIO, Mongo (end of step 9) | 4 accepted (1 High if reachable, 2 Low, 1 Info) |
| 2026-10-04 | `GET /internal/v1/tweets/by-author/{authorId}` (frontend phase 3) | 1 Medium fixed, 1 Low open |

## Tweet routes (2026-09-30)

- **Any caller who reaches port 8081 can act as any user (High if reachable), accepted.** Identity is the
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
- **A `since` outside `java.util.Date`'s range answers 500 and logs a stack trace (Low), open.** Spring parses any
  ISO instant; the Mongo mapper fails converting years beyond the `long` millisecond range (for example
  `since=%2B292278994-08-17T07:12:56Z`), which is not a 400 like the other bad values. About 90 log lines per
  request; nothing leaks into the body. The real caller sends a validated event time minus 7 days. Fix: reject a
  `since` outside a sane window with the `limit` 400 shape, or map `ConversionFailedException` to 400.
- **Clean:** the response is exactly `{id, createdAt}` and at most 100 items; every bad `limit` and `since` is a 400
  (epoch millis refused by design); no string reaches the Mongo query; the gateway does not forward `/internal/**`
  (404 with or without its secret); only `GET` is allowed; error text carries no internals. No auth on `/internal/**`
  is by design (the `X-User-Id` gap), protected by network isolation only.
