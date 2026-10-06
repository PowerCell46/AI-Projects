# Twitter API Gateway — plan

The single front door of the Twitter clone: it owns users and all access rules. One deployable, built in
ordered phases. **Phases 1–5 are done (2026-09-29 to 2026-10-04).** Design calls were settled in the `/grill-me`
interview of 2026-09-29 (Q-numbers in parentheses). Calls made during implementation live in `DECISIONS.md`, the HTTP
scenario catalog in `TESTING.md`, the Kafka contract in `EVENTS.md`, the audits in `SECURITY-AUDITS.md`. The reference
for "as SignalFlow" is `../../SignalFlow/signal_flow_api_gateway`.

**Hard rule:** no step starts on a red or missing test, each step ends green, and nothing in phase *n+1* starts
until phase *n*'s final gate passes (`mvn verify` green 3× in a row).

## Status

| Phase | Scope | Done | Audit |
|---|---|---|---|
| 1 | Auth + email confirmation (steps 1–10) | 2026-09-29 | 2 fixed, 4 accepted |
| 2 | Profile + MinIO pictures (steps 11–18) | 2026-09-29 | finding 1 fixed 2026-09-30 |
| 3 | Follows (steps 19–24) | 2026-09-30 | 3 Low, all accepted below |
| 4 | Tweet routing (steps 25–30) | 2026-09-30 | 1 Low fixed, 1 Low + 2 Info accepted |
| 5 | People to follow (steps 31–35) | 2026-10-04 | 1 Medium fixed, 1 Low accepted |

**Left:** the mail service's follow email (separate project), plus the accepted gaps below when their triggers land.
The gateway steps of `../twitter_timeline_service/PLAN.md` (`/internal/v1/**` behind a shared secret, `user.unfollowed`,
the feed, saved-tweets and views routes) are planned and tracked there, and are built. The likes route (`/api/v1/likes/**`, replacing the
like stub) is timeline phase 4, step 32, and is built.

---

## What was built

### Stack and global design
- Java 25, Spring Boot 4.1.1, Maven wrapper, multi-stage Dockerfile, port 8080, package
  `com.peter_gerdzhikov.twitter_api_gateway`. Postgres 18, Kafka (KRaft) and MinIO in the root compose files.
- Layered controller → service → repository; every service is an interface + `Impl`; constructor injection;
  endpoints under `/api/v1`; `Clock` bean for all "now" comparisons (mutable test clock in tests).
- **Ported from SignalFlow (Q20):** password rules (8–72, upper/lower/digit, BCrypt 12, 4 in tests); `access_token`
  cookie (`HttpOnly`, `SameSite=Strict`, `COOKIE_SECURE`); CSRF off; public logout; no default `JWT_SECRET`;
  `ErrorResponseDTO` via `GlobalExceptionHandler` (never echoes internals); 8 KB body cap filter (`413`); dummy
  bcrypt on an unknown login identifier.
- **Auth model:** HS256 JWT, claims `sub`/`username`/`email`/`iat`/`exp`, TTL 1h, no roles, no refresh, no revocation
  (Q5, Q19). Authorization reads claims only, never the DB (Q18). Public: `GET /actuator/health`,
  `POST /api/v1/auth/{register,login,logout,confirm,confirm/resend}`; everything else authenticated (Q29).
- **Schema:** `ddl-auto=update`, no Flyway (Q22–23); every unique constraint and FK is named so services map
  `DataIntegrityViolationException` by constraint; cascades via `@OnDelete(CASCADE)`.

### Phase 1 — Auth
- **Entities:** `User` (username as typed plus `usernameNormalized`, lowercased email, `enabled` = confirmed, Q4),
  `EmailConfirmationToken` (SHA-256 hash of a 32-byte random token, single-use, 24h), generic `Outbox`.
- **Endpoints:** `register` (201, no cookie, `409` by constraint name), `confirm` (204, `400` for unknown/used/expired),
  `confirm/resend` (always `202`, 60s cooldown under a user row lock, Q13), `login` (email or username, generic `401`,
  `403` only after a correct password, Q14/Q17), `logout`, `me`.
- **Events:** `user.confirmation-requested` is written to the outbox in the register/resend transaction;
  `OutboxPublisherJob` (3s, 500 per batch, 3 attempts then `FAILED`) sends it to Kafka.
- **Cleanup:** a daily job deletes unconfirmed users older than 7 days (tokens cascade, outbox untouched, Q12).

### Phase 2 — Profile
- **Entities:** `DbFile` (random `objectKey`, detected `contentType`, `sizeBytes`); `User` gains `profilePicture`,
  `profileCoverPicture`, `bio` (≤160), `location` (≤30) and `birthdate` (past); `@DynamicUpdate`.
- **MinIO:** official SDK, one private auto-created bucket, `ObjectStorageService` (failures → `502`),
  `ImageSignatureValidator` (magic bytes for JPEG/PNG/WebP, else `415`; client type and filename ignored).
- **Endpoints:** `GET /users/{username}` (unknown or unconfirmed → `404`), `PUT /users/me` (full replace, blank →
  null), `PUT|DELETE /users/me/{profile-picture|cover-picture}` (5 MB cap, empty → `400`, delete idempotent),
  `GET /files/{id}` (streamed, `private, immutable` cache, `nosniff`).
- **Upload order:** validate → put the new object → DB transaction (lock the user row, save `DbFile`, repoint, delete
  the old row) → after commit delete the old object (an orphan on failure is accepted).

### Phase 3 — Follows
- **Entity:** `Follow` (unique pair, self-follow `@Check`, two keyset indexes); `User.followersCount` and
  `followingCount` (`updatable = false`, `@Check >= 0`).
- **Counters (Q34–35):** native `INSERT … ON CONFLICT DO NOTHING` / `DELETE`; counters change by an atomic
  `x = x ± 1` only when a row changed, in fixed `UUID.compareTo` order so mutual follows can't deadlock.
- **Endpoints:** `PUT|DELETE /users/{username}/follow` (204, idempotent; self → `400`, unknown or unconfirmed →
  `404`); the profile gains counts and `followedByMe`; `GET /users/{username}/{followers|following}` with an opaque
  keyset cursor (`FollowCursorCodec`), default 20, max 100, one `IN` query for `followedByMe` (Q36).

### Phase 4 — Tweet routing
- **Proxy, no tweet code (T-Q1):** Spring Cloud Gateway MVC. `TweetRoutesConfiguration` forwards `/api/v1/tweets/**`
  to `app.tweet-service.url` (`TWEET_SERVICE_URL`) with the path unchanged and HTTP/1.1 pinned; the route is
  `authenticated()` by the default rule. The tweet service's design lives in `../twitter_tweet_service/PLAN.md`.
- **Identity (T-Q2, T-Q14):** strip `Cookie`, `Authorization` and every `X-User-*` header, then add `X-User-Id` = the
  JWT `sub`.
- **Body caps (T-Q4):** a third cap, `app.request.max-tweet-body-bytes` (21 037 056), for exactly `POST /api/v1/tweets`;
  everything else keeps 8 KB. A 21 MB body streams through byte-identical (see `DECISIONS.md`).
- **Failures:** connect timeout 2s, read timeout 10s for all routes. Connect failure → `502`, read timeout → `504`,
  a chunked body over a cap → `413`, no retries. Tweet-service error bodies pass through.

### Phase 5 — People to follow
- **Endpoint:** `GET /api/v1/users?cursor=&size=` → `{items, nextCursor}`: every confirmed user except the caller,
  newest account first (Q1). Item: `{id, username, bio, profilePictureUrl, followersCount, followedByMe}` (Q2). Other
  methods answer `405` with `Allow: GET`, `HEAD` answers `200`, the trailing-slash path is `404`.
- **Code:** `UserListService(Impl)`, `UserController.getUsers`, `ProfileMapper.toUserListItem`; the size check is shared
  with the follow lists in `utilities/paging/PageSizeValidator`, and the cursor is `FollowCursorCodec`.
- **Queries:** `findUserListFirstPage` / `findUserListAfter` (`LEFT JOIN FETCH` of the picture; keyset on the row value
  `(created_at, id)`) over `ix_users_enabled_created`. Two SQL statements per call, counted in tests with Hibernate
  statistics.
- **Frontend handoff** (built in `frontend/PLAN.md`): cards show username, bio, picture and `followersCount`; the
  follow button uses the existing follow endpoints and the UI flips `followedByMe` itself, without refetching.

## Test strategy (Q37–38)
Unit (Mockito) → repository (real Postgres) → HTTP e2e (Postgres, Kafka, MinIO via Testcontainers) → concurrency
(latch-released threads, assert final state). No embedded fakes; Docker is required. The approved `@Disabled` catalog
came first each phase, then each step enabled its group. Deterministic only: no `Thread.sleep` (bounded Awaitility),
test clock, jobs disabled and invoked directly, unique data via `TestUsers`, Kafka assertions filtered by `userId`.

## Out of scope
Mail service and SPA pages; password reset, account deletion, username change, display names (Q16); roles/admin
(Q5); birthdate at signup and age checks (Q21); tweet logic (owned by `../twitter_tweet_service`), timelines,
notifications, blocks, mutes, private accounts; anonymous profile viewing (Q29); refresh tokens (Q19); image resizing;
searching or filtering the people list and hiding people the caller already follows (phase 5).

## Accepted gaps — revisit when the named trigger lands
- **One `enabled` flag means "confirmed"; no roles/admin.** Trigger: first ban, or moderation features.
- **No rate limiting** except the resend cooldown (login capped only by bcrypt-12; register, uploads, follows and the
  tweet routes unthrottled). Trigger: abuse, or a second instance.
- **Register leaks whether an email/username exists** (explicit `409`s). Login's `403` reveals "unconfirmed" only to
  someone holding the password (Q14).
- **Squatting:** a pending account holds an email/handle up to 7 days plus cleanup; a real owner clicking the
  squatter's link confirms an account the squatter controls. Trigger: password reset exists, or first report.
- **Email and live confirmation token travel through Kafka in plaintext.** Trigger: Kafka exposed → TLS/SASL.
- **Outbox is at-least-once** (the mail service dedupes on `eventId`); **no row-claiming** in the poller (trigger:
  second instance → `SKIP LOCKED`); **`FAILED` rows need manual handling** (trigger: first one that matters).
- **Scheduled jobs run on every instance.** Trigger: second instance → ShedLock/advisory lock.
- **Dev compose has default credentials and plaintext Kafka** (ports bound to `127.0.0.1`). Trigger: any shared
  environment.
- **`COOKIE_SECURE` defaults to `false`.** Trigger: first deployment.
- **Log lines can carry attacker-chosen or personal text** (unknown-path WARN, constraint-violation WARN with email).
  Trigger: logs shipped to a shared system.
- **`ddl-auto=update`, no migrations.** Trigger: second environment or first destructive change → Flyway.
- **JWT 1h, no refresh, no revocation.** Trigger: hourly logouts annoy, or need to kill sessions.
- **Orphan MinIO objects** when the post-commit delete of an old object fails. Trigger: storage growth → sweep job.
- **Images stored as uploaded: no resize, EXIF (incl. GPS) retained.** Trigger: before any public deployment.
- **Images (profile and tweet) are proxied through the gateway.** Trigger: image traffic dominates → presigned URLs/CDN.
- **Any logged-in user can fetch any file id** (all files are profile images). Trigger: first private media type.
- **No restrictive `Content-Security-Policy` on served files** (phase 2 audit, Low; not exploitable while only
  JPEG/PNG/WebP are accepted). Trigger: loosening the type check (e.g. SVG).
- **Follower and following lists are readable by any logged-in user** (phase 3 audit, Low): no private accounts by
  design. Trigger: private accounts → check the viewer against the target's visibility.
- **State-changing routes rely on `SameSite=Strict` alone, with CSRF disabled** (phase 3 audit, Low): it doesn't stop
  a request from a sibling subdomain of the same site. Trigger: untrusted content on a sibling subdomain → a CSRF token.
- **Hot-row contention on a popular account's counters.** Trigger: measurable follow latency → sharded counters.
- **No counter reconciliation job** (`@Check >= 0` catches only negative drift). Trigger: any observed drift.
- **Any logged-in user can page through every confirmed account** (phase 5), with each person's `followersCount`;
  profiles were already readable by username, and this makes them enumerable. Trigger: abuse, or a second instance →
  rate limit on `GET /api/v1/users`.
- **No popularity ordering** (phase 5, Q1): newest first, because counters move while a reader pages and would repeat
  or skip people. Trigger: newest-first stops being useful → a ranked list from a snapshot or a stored score, which
  needs its own design.
- **No search or filter on the people list** (phase 5). Trigger: the first request to find someone by name → a `q`
  parameter on the same endpoint.
- **User and file ids are time-based UUIDs** (`@UuidGenerator(style = TIME)` in `CommonEntity`): they embed the host's
  IPv4 address, JVM start time and a counter (phase 5 audit, Low). No id is a secret today. Trigger: a deployment
  whose network addresses matter, or any use of an id as a secret → a random or v7 generator.
- **The three follow-list queries still use the `OR` cursor predicate:** a page reads the rows newer than the cursor
  within one account's follows. Trigger: an account with six-figure follows or a slow list → the row-value form the
  people list uses.

### Phase 4 gaps
- **The tweet service trusts `X-User-Id`** and is safe only while nothing but the gateway can reach it. Trigger: any
  shared or deployed network → a shared internal secret or mTLS.
- **One 10s read timeout for every route.** Trigger: slow uploads start hitting `504` → a per-route timeout.
- **Only `X-User-*` headers are stripped;** look-alikes (`X-UserId`, `X-Original-User-Id`) reach the tweet service
  (audit finding 2, Low). Trigger: any downstream that trusts another identity-like header → forward an allowlist.
- **Path segments `;x=y`, `/./` and `//` answer `401`** even with a valid cookie (audit finding 3, Info); it fails
  closed. Trigger: a client that legitimately sends such paths.
- **Tomcat's HTML `400` page for paths it rejects** (audit finding 4, Info; gateway-wide). Trigger: a public
  deployment → an error page or filter that answers JSON.

## Interview record (`/plan-backend`, 2026-10-04)
Phase 5 calls: (1) newest account first, everyone confirmed except the caller, marked `followedByMe`; (2) fields
`id`, `username`, `bio`, `profilePictureUrl`, `followersCount`, `followedByMe`, with no email, location, birthdate,
`createdAt`, `followingCount` or cover picture; (3) one phase, steps 31–35, with `GET /api/v1/users`, paging as the
follow lists, two statements per call and no new event or service call. Also accepted: the index
`ix_users_enabled_created`, the picture fetched in the page query, `FollowCursorCodec` reused, a deleted caller sees
everyone, the size check shared between the two lists, `405` for other methods and `404` for the trailing slash.
