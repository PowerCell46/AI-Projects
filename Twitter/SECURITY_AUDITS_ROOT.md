# Security audits across services

Consolidated fixed findings and audit records moved from service-level `docs/SECURITY-AUDITS.md` files. (Open and accepted findings are kept privately under `security-private/`.)

---

## Twitter API Gateway

Last updated: 2026-10-10

The `exploit-hunter` audits of the gateway, one section per phase, condensed from the original per-audit reports (dates are the audit dates). Only fixed findings are kept in this public file; the open and accepted ones are kept privately.

| Phase | Audited | Scope | Result |
| --- | --- | --- | --- |
| 1 | 2026-09-29 | Auth, email confirmation, outbox, cleanup job | 4 findings fixed; open ones kept privately |
| 2 | 2026-09-29, re-check 2026-09-30 | Picture upload, file serving, profile routes | 1 finding fixed; open ones kept privately |
| 3 | 2026-09-30 | Follow, unfollow, follower and following lists | No fixed findings; open ones kept privately |
| 4 | 2026-09-30 | Routing of `/api/v1/tweets/**` | 1 finding fixed; open ones kept privately |
| 5 | 2026-10-04 | `GET /api/v1/users` (people to follow) | No fixed findings kept here; open ones kept privately |
| Follow check | 2026-10-04 | `GET /internal/v1/users/{a}/follows/{b}` | No findings |

### Phase 1: auth and email confirmation
- **Multi-byte password crashed register and login with a 500 (Medium), fixed.** `@Size(max = 72)` counts characters but bcrypt takes 72 bytes, so a 63-character password of 2-byte characters passed validation and the encoder threw. New `@MaxUtf8Bytes(72)` on both DTOs; both answer 400.
- **A short `JWT_SECRET` was accepted at startup and failed at login (Low), fixed.** Startup now fails below 32 bytes.
- **`COOKIE_SECURE` defaulted to `false` (Low), fixed 2026-10-10.** It defaults to `true`; plain-HTTP local dev and the e2e stack set `COOKIE_SECURE=false`.
- **The seeder's shared password defaulted to a value in the public repo (Medium), fixed 2026-10-10.** `SEED_PASSWORD` has no default; with seeding on, startup fails unless it is 12-72 bytes with a lowercase letter, an uppercase letter and a digit (`DataSeedServiceImplTest`). The seeder stays on by default, so a deployment must set it.
- **Clean:** all queries bind parameters; no outbound requests or redirects from input; the only resource route is `/me`; HS256 is pinned and `alg=none` or tampered tokens are 401; the token is read from the cookie only; bodies are capped for declared and chunked sizes; error bodies are fixed strings; actuator exposes only `health`; no CORS. Dependencies were scanned on 2026-10-10.

### Phase 2: pictures and files
- **Parallel uploads to one slot leaked rows and objects without bound (Medium), fixed 2026-09-30.** Eight parallel uploads all answered 200 and left 4 orphan `DbFile` rows and objects. `ProfilePictureServiceImpl` now takes `UserRepository.lockById` before loading the user in both transactions, and `should_leave_no_orphan_rows_when_uploads_to_one_slot_run_in_parallel` fails without it (confirmed by removing the lock temporarily).
- **Re-check, verified:** the lock reads fresh state, so the loser sees the winner's picture as the old one; `@DynamicUpdate` keeps a racing profile edit from being overwritten; the lock is only on the caller's own row; a failed transaction still deletes the newly stored object, and old objects are deleted only after commit.
- **Clean:** 20 MB and 6 MB chunked uploads answer 413 and create no row; the client's type and filename are never read (type from magic bytes, key a random UUID); GIF, renamed text, truncated headers and a non-WebP RIFF give 415; the user id comes from the JWT only, so no IDOR; storage failures answer a fixed 502.

### Phase 3: follows
No finding was fixed in this phase; the open ones are kept privately.
- **Clean:** bound parameters throughout; a hand-built cursor can only move the page start (extreme values give 200, malformed ones 400); identity from the JWT only; unknown and unconfirmed targets both 404; self-follow is 400 before any write; `size` is validated before `size + 1` is computed; a 10-item page costs 3 statements; counters stay equal to `COUNT(*)` in all four concurrency tests.

### Phase 4: tweet routing
- **A chunked body over a cap answered 502 instead of 413 (Low), fixed 2026-09-30.** The size-cap exception surfaced wrapped in the proxy's I/O error. `handleUpstreamFailure` now unwraps it and answers 413, covered by a handler unit test and two chunked scenarios in `TweetBodyCapsIntegrationTest`.
- **Clean:** no path trick (`..`, `%2e%2e`, `..;`, double encoding, CR/LF, case changes) reached the tweet service; anonymous requests were 401 with nothing forwarded; the tweet cap applies to the exact `POST /api/v1/tweets` only; `Content-Length` plus chunked was not smuggled; `Cookie`, `Authorization` and client `X-Forwarded-*` never reach the tweet service; `/actuator/env` is 401.

### Phase 5: people to follow
No fixed finding is kept here; the open ones are kept privately.
- **Clean:** bad or missing credentials are 401; the caller is excluded by the JWT `sub`; every malformed cursor is a 400 and nothing is reflected; every bad `size` is a 400; markup in a username or bio comes back as escaped JSON; the response is `no-store` with `nosniff` and `X-Frame-Options: DENY`.

### Follow check (frontend phase 3)
No findings, not even Low. The secret guard runs first: a missing, empty, wrong, query-string or cookie-only secret all answer the unknown-path 404, so the follow graph can't be read without it. The endpoint is not reachable through the tweet or timeline routes (path-traversal variants all 401 or 400), allows only `GET`/`HEAD`/`OPTIONS`, leaks nothing in error text, and costs one unique-index lookup (0.012 ms). Endpoint answers `200 {"following": true|false}`.

---

## Twitter Tweet Service

Last updated: 2026-10-10

The `exploit-hunter` audits of the tweet service, one section per audit, condensed from the original per-audit reports (dates are the audit dates). Findings that are still open or accepted are kept privately.

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-09-30 | The five `/api/v1/tweets` routes, outbox publisher, MinIO, Mongo (end of step 9) | 4 findings accepted; kept privately |
| 2026-10-04 | `GET /internal/v1/tweets/by-author/{authorId}` (frontend phase 3) | 1 Medium and 1 Low fixed |
| 2026-10-07 | Phase 2: the four reply routes, cursor, conflict retrier, gateway user lookup (step 22) | 1 Low and 1 Info fixed |

### Tweet routes (2026-09-30)
- **Clean:** Mongo queries use typed ids and fixed field names, and a log-forging probe stayed one line; no outbound calls from input; update and delete match `_id` and `authorId` in the write itself; image type comes from the bytes, images are never decoded, storage keys are random UUIDs; error bodies are fixed strings, `/actuator/env` is 404 and health shows only status; `.env` is git-ignored, MinIO keys have no default, the container is non-root. Dependencies were scanned on 2026-10-10.

### By-author read (2026-10-04)
- **The `_id` tie-break made the query read and sort every tweet of the author since `since` (Medium), fixed 2026-10-04 (frontend step 24).** The index `{authorId: 1, createdAt: -1}` can't satisfy the `createdAt, _id` sort, so with 300,000 tweets by one author and `since=2000-01-01` Mongo examined 300,000 keys and documents (247 ms) for 100 rows; the same query sorted by `createdAt` alone examined 100. Anyone who can reach the port can send a wide `since`, and each follow triggers the read. The index is now `{authorId: 1, createdAt: -1, _id: -1}` and a repository test asserts at most `limit` keys and documents examined (`DECISIONS.md`).
- **A `since` outside `java.util.Date`'s range answered 500 and logged a stack trace (Low), fixed 2026-10-09.** Spring parses any ISO instant; the Mongo mapper fails converting years beyond the `long` millisecond range (for example `since=%2B292278994-08-17T07:12:56Z`), which was not a 400 like the other bad values. About 90 log lines per request; nothing leaked into the body. The real caller sends a validated event time minus 7 days. `since` outside 1970-01-01 to 9999-12-31 now answers 400 in the `limit` shape; parameterized e2e test (`DECISIONS.md`).
- **Clean:** the response is exactly `{id, createdAt}` and at most 100 items; every bad `limit` and `since` is a 400 (epoch millis refused by design); no string reaches the Mongo query; the gateway does not forward `/internal/**` (404 with or without its secret); only `GET` is allowed; error text carries no internals.

### Replies (2026-10-07)
- **The 8 KB body cap did not apply to chunked multipart on any route (Low), fixed 2026-10-07 (phase 2, step 22).** The container parses multipart without going through the counting wrapper, so a chunked 20 MB upload was read in full on the reply routes, unmapped paths and `/internal/**`, before routing and the identity check. The body-size filter now refuses multipart with `415` on every route but the tweet create (`DECISIONS.md`); a unit test and a real-port test cover it.
- **Reply edit and delete answered `403` or `404`, confirming a reply id exists (Info), fixed 2026-10-07.** Both now answer `404 "Reply not found."` for someone else's reply, the same as a missing one. Reply ids are random UUIDs and the list shows them to everyone, so little was exposed; the tweet routes keep their `403`.
- **Not reproduced:** a reply flood starving the author's tweet delete (64 parallel clients, 1,524 replies in 6 s: the delete answered `204` in 60 ms, no `503 BUSY`).
- **Clean:** every Mongo value is typed and field names are fixed; the cursor is re-encoded and compared (bad sizes and cursors are `400`); with 300,000 replies on one tweet a deep page examined 21 keys; edit matches `_id`, `tweetId` and `authorId` in the write; the only outbound call is the gateway at a configured URL with UUID ids; error bodies are fixed strings and `/actuator` shows `health` only; `INTERNAL_API_SECRET` has no default and a 32-byte floor. Dependencies were scanned on 2026-10-10.

---

## Twitter Mail Service

Last updated: 2026-10-10

The `exploit-hunter` audits of the mail service, one section per phase, condensed from the original per-audit reports (dates are the audit dates). Each "accepted" item is listed in `ACCEPTED_GAPS_ROOT.md` under its accepted gaps, with a trigger.

| Phase | Audited | Scope | Result |
| --- | --- | --- | --- |
| 1 | 2026-10-01 | Confirmation-email path: consumer, validation, Redis inbox, rendering, SMTP, DLT, health, compose, Dockerfile | 4 findings fixed; open ones kept privately. |
| 2 | 2026-10-02 | Follow-email path: consumer, pair-keyed window, renderer, shared dispatch, second DLT | 1 finding fixed; open ones kept privately. |

The only untrusted-input entry point is the Kafka topics; the service has no HTTP surface beyond `GET /actuator/health`.

### Phase 1: confirmation email
- **STARTTLS could be silently downgraded and the server identity was not checked (Medium), fixed.** `mail.smtp.starttls.enable` only makes JavaMail *try* STARTTLS; if the EHLO reply leaves it out, `AUTH` (the Gmail app password) and every body (confirmation links) went in plaintext, and `ssl.checkserveridentity` defaulted to `false`. `starttls.required` now follows `starttls.enable` and `checkserveridentity=true`; plaintext SMTP (Mailpit) works only by turning STARTTLS off on purpose. Verified live: against a server without STARTTLS nothing is delivered, and the record is retried then dead-lettered. Covered by `MailTlsConfigurationIntegrationTest`.
- **The e2e Mailpit API was published on every interface (Low), fixed.** The unauthenticated UI and API exposed live confirmation links to the LAN. Now `127.0.0.1:8125:8025`, and the fixtures call `127.0.0.1` so a resolver preferring `::1` can't miss the loopback-only binding.
- **Event validation was looser than the gateway's (Low), fixed.** Hibernate's `@Email` accepts quoted local parts, address literals, `a@localhost` and `a%b@example.com`, which the gateway's `@Pattern` rejects. Header injection was never possible (CR/LF, commas, display names and angle brackets are rejected here and again in delivery). The gateway's `@Pattern` now sits next to `@Email`, so both services accept the same addresses (`UserConfirmationRequestedEventDTOValidationTest`).
- **Invalid records were dropped without a log line (Low), fixed.** A malformed or invalid record went straight to the DLT and only a permanent SMTP rejection logged. The notification service now logs a WARN with `eventId`, `userId` and the violated field names and messages, never the values (a test checks the address and token stay out). A malformed-JSON record has no id to log, so it shows only in the DLT.
- **Clean:** logs carry only `eventId`, `userId`, the exception class and the SMTP code, and the delivery exceptions have no cause (a test asserts a recipient never reaches their message); the subject is a constant with CR/LF stripped; every HTML value goes through `HtmlUtils.htmlEscape` in one pass; Redis keys are `mail:confirmation:` plus a typed `UUID` and the release script takes `KEYS`/`ARGV`; Redis and Mailpit bind `127.0.0.1`, Redis needs a password, `.env` is git-ignored, the container is non-root; `email` ≤ 254, `confirmationUrl` ≤ 2048, no regex over free text. Dependencies were scanned on 2026-10-10.

### Phase 2: follow email
- **A zero or negative `FOLLOW_MAIL_WINDOW` silently turned the 24h dedupe into a 5-minute one (Low), fixed.** Redis refuses a zero expiry, the dispatch service swallowed the failure, and only the claim key's 5m TTL remained. Both notification services now throw `IllegalStateException` at startup through `Durations.requirePositive`, for this window and for `CONFIRMATION_MAIL_SENT_TTL`, which had the same hole (a `Constructor` test in each).
- **Clean:** the subject holds a username matching `^[A-Za-z0-9_]{3,15}$` over the whole value (25 validation cases, CR/LF stripped again in delivery); both usernames and the feed URL are escaped in the HTML part and the only link is `APP_BASE_URL` + `/feed`, from configuration; the email carries only public and the recipient's own data; the Redis key is two typed UUIDs and the value is `SENT`, so no collision with another pair or a `mail:confirmation:` key; the claim is one atomic `SET NX` and the pair key is directional; the follow logs hold ids, the violated field and the exception class (0 lines with an address or `token=` in the full log); the DTO caps email at 254 and usernames at 15.

---

## Twitter Timeline Service

Last updated: 2026-10-10

The `exploit-hunter` audits of the timeline service, one section per audit, condensed from the original per-audit reports (dates are the audit dates; the tweet details report was merged in on 2026-10-09). Only fixed findings are kept in this public file; the open and accepted ones are kept privately.

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-10-03 | Phase 1: feed read, listeners, retention, gateway `/internal/v1/**`, the feed route | 1 finding fixed; open ones kept privately |
| 2026-10-03 | Phase 2: saved tweets | 0 findings fixed; open ones kept privately |
| 2026-10-03 | Phase 3: views | 0 findings fixed; open ones kept privately |
| 2026-10-04 | Frontend phase 3: back-fill on follow | 4 findings fixed; open ones kept privately |
| 2026-10-06 | Phase 4: likes | 1 finding fixed; open ones kept privately |
| 2026-10-07 | The tweet details read (tweet service phase 2, step 22) | 2 findings fixed; open ones kept privately |

### Phase 1: feed (2026-10-03)
- **`/actuator/health` showed details on the service port (Low), fixed.** `show-details=always` returned the database product, disk path and free bytes; the gateway and tweet service return only `status` and `groups`. The line is removed, so the default `never` applies.
- **Clean:** a missing or wrong secret is a `404` like an unknown path; path-traversal variants are 401 or 400 and never forwarded; a spoofed `X-User-Id` is replaced by the JWT subject; bad `size` or cursor is a 400 with fixed messages; every SQL statement binds its parameters; downstream URLs come from configuration; the secret goes only to the gateway client, is compared in constant time and has no default.

### Phase 2: saved tweets (2026-10-03)
0 findings fixed; open ones kept privately.
- **Clean:** a non-UUID id is a 400 with no type name; `1-1-1-1-1` parses and is a 404; every query is scoped by `X-User-Id` and `unsave` deletes only the caller's row; logs carry UUIDs only; downstream bodies are never returned; a 20 KB body is a 413.

### Phase 3: views (2026-10-03)
0 findings fixed; open ones kept privately.
- **Clean:** no cookie is 401; a 9 KB body is 413; 51 ids is a 400; malformed ids and bodies are fixed 400s; `text/plain` and form bodies are 415; an extra `viewerId` field is ignored; `PUT`/`DELETE` are 405; no CORS headers for another origin; every statement binds its parameters; the counter moves only for ids the insert returned, so 50 parallel viewers give exactly 50.

### Back-fill on follow (2026-10-04)
- **A wrong secret, a missing endpoint or a wrong gateway URL read as "unfollowed" and the back-fill was deleted (Medium), fixed 2026-10-04.** The gateway answers a missing or wrong secret with the same `404` as an unknown path, which `FollowLookupServiceImpl` read as `false`, so the 50 rows just inserted were deleted: no retry, no DLT, one INFO line. The gateway now answers `200 {"following": true|false}` and only that is an answer (`DECISIONS.md`).
- **`FEED_BACKFILL_SIZE` above 100 started fine and then dead-lettered every follow (Low), fixed 2026-10-09.** The tweet service answers 400 above 100, which became a retryable "unavailable". Now the app refuses to start above 100, a constant as in the tweet service.
- **`occurredAt` was trusted: a far-past value skipped the 7-day window, `Instant.MIN` threw (Low), fixed 2026-10-09.** The window now starts at the clock's now minus the retention and the event time is not read (`DECISIONS.md`).
- **A forged self-follow event deleted the user's own tweets from their own feed (Low), fixed 2026-10-09.** A self-follow is now an invalid event: the DLT, no retry, no downstream call.

### Phase 4: likes (2026-10-06)
- **`GlobalExceptionHandler` logged the resource path unescaped at WARN (Info), fixed 2026-10-09.** A decoded line feed could forge a log line, only on direct access to :8083. The path is no longer logged, only the method.
- **Clean:** every statement binds its parameters; downstream URLs come from configuration; like, unlike and list are scoped to the caller and no endpoint lists who liked a tweet; `size` 1–100, strict cursors, 8 KB bodies; error bodies are fixed strings; the row insert or delete decides whether the counter moves, so parallel likes or unlikes by one user count once; the `PUT` / `DELETE` routes are not a CSRF path (a cross-origin `fetch` needs a preflight the gateway does not grant).

### Tweet details read (2026-10-07)
- **Two sequential downstream calls could outlive the gateway's 10 s read timeout (Low), fixed 2026-10-07.** Each call had 2 s connect and 5 s read, so ~14 s in the worst case; the gateway answered `504` while this service kept working. The defaults are now 1 s and 3 s, so the pair stays under 8 s (`DECISIONS.md`).
- **A malformed tweet-service answer ended as a `500` with a stack trace in the log (Info), fixed 2026-10-07.** A tweet without `id`, `authorId` or `images`, a null item or a null body is now a `502`; a test covers each.
- **Clean:** `tweetId` is a bound `UUID` (non-UUID is a fixed 400); JPQL and `findAllById` bind their parameters; downstream URLs come from configuration, so no SSRF or open redirect; any authenticated user may read any tweet, as through the tweet service, and `savedByMe` / `likedByMe` come from the header the gateway sets from the JWT; error bodies are fixed strings and the response carries no email; the service sets no cache entries and the response holds per-viewer flags; the endpoint returns JSON, sets no cookies and no raw-HTML sink in `frontend/src` consumes it; the route is behind `authenticated()` and wrong methods are `405`.

---

## Project-wide audit (2026-10-08)

Nothing fixed here; the audit found nothing to record beyond what was considered and cleared.

Considered, not a finding:

- **Injection:** `authorId` and the cursor reach Mongo only as a typed `UUID` and `Instant`. The cursor is re-encoded and compared, so it accepts only the exact canonical form. The timeline's `RestClient` URI templates encode values (default `TEMPLATE_AND_VALUES`), so `&` in a cursor cannot add parameters.
- **Path tricks to `/internal/**`:** Spring Security's `StrictHttpFirewall` rejects `..`, `%2e`, `%2f` and `;` before the proxy route, and `InternalApiSecretFilter` reads the path the way MVC does. Not exercised at runtime.
- **IDOR:** `PATCH /me` takes the id only from the JWT subject; the DTO has three fields, so there is no mass assignment. Tweets and an author's tweet list are readable by any user by design. `/count` and the author page return no private data.
- **Enumeration:** `author-tweets` answers 404 for unknown/unconfirmed users and 200 otherwise, but ids are random UUIDs, so there is nothing to guess.
- **Info disclosure:** all handlers return fixed messages; stack traces are only logged. Only `health` is exposed by actuator on the services.
- **Races:** `PATCH /me` uses `@DynamicUpdate` with no check-then-act. Paging has no write path.
- **CSRF/XSS:** cookie is HttpOnly + SameSite=Strict, and PATCH requires `application/json`. No `dangerouslySetInnerHTML`, `innerHTML` or `localStorage` in `frontend/src`.
- **Body size and page size:** an 8 KB body cap applies to the PATCH; page size is limited to 1–100.
- **SSRF, file upload, secrets, dependencies:** no user-supplied URLs in these routes; nothing uploaded; no secrets seen in the files read; dependency versions not reviewed.

---

## Project-wide dependency scan (2026-10-10)

`osv-scanner` (Docker image `ghcr.io/google/osv-scanner`) over the four `pom.xml` files and both `package-lock.json` files found 71 advisories (10 Critical, 34 High, 24 Medium, 3 Low) in `tomcat-embed-core` 11.0.24, `jackson-core` and `jackson-databind` 2.21.5 and 3.1.5, `lz4-java` 1.10.1 and, in the tweet service, `bcprov-jdk18on` 1.84. All were fixed by raising the versions in each `pom.xml` (Tomcat 11.0.25, Jackson 2.21.7 and 3.1.7, lz4-java 1.11.4, BouncyCastle 1.85), and a second scan reported no issues. Reachability was not assessed. Nothing runs the scan automatically yet, so repeat it before each deployment.
