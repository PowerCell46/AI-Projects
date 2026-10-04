# Twitter API Gateway — plan

The single front door of the Twitter clone: it owns users and all access rules. One deployable, built in
ordered phases. **Phases 1–5 are done (2026-09-29 to 2026-10-04).** Design calls were settled in the `/grill-me`
interview of 2026-09-29 (Q-numbers in parentheses). Calls made during implementation live in `DECISIONS.md`, the HTTP
scenario catalog in `TESTING.md`, the Kafka contract in `EVENTS.md`. The reference for "as SignalFlow" is
`../../SignalFlow/signal_flow_api_gateway`.

**Hard rule:** no step starts on a red or missing test, each step ends green, and nothing in phase *n+1* starts
until phase *n*'s final gate passes (`mvn verify` green 3× in a row).

## Status

| Phase | Scope | Done | Audit |
|---|---|---|---|
| 1 | Auth + email confirmation (steps 1–10) | 2026-09-29 | `exploit-report-2026-09-29.md` |
| 2 | Profile + MinIO pictures (steps 11–18) | 2026-09-29 | `exploit-report-2026-09-29-phase2.md`; finding 1 fixed 2026-09-30 |
| 3 | Follows (steps 19–24) | 2026-09-30 | `exploit-report-2026-09-30-phase3.md`: 3 Low, all covered by accepted gaps |
| 4 | Tweet routing (steps 25–30) | 2026-09-30 | `exploit-report-2026-09-30-phase4.md`: 1 Low fixed, 1 Low + 2 Info accepted |
| 5 | People to follow (steps 31–35) | 2026-10-04 | `exploit-report-2026-10-04-phase5.md`: 1 Medium fixed, 1 Low accepted |

**Left:** the mail service's follow email (separate project, phase 2), plus the accepted gaps below when their triggers land.

**Next:** the gateway steps of `../twitter_timeline_service/PLAN.md` (`/internal/v1/**` behind a shared secret,
`user.unfollowed`, routes to the timeline service) are planned there, not here. Phase 1's (internal endpoints,
`user.unfollowed`, the `/api/v1/feed` route) were built 2026-10-03; the saved-tweets route was built 2026-10-03 (step 17); the views route was built 2026-10-03 (step 25), and the contract test no longer asserts `views`.

---

## What was built

### Stack and global design
- Java 25, Spring Boot 4.1.1, Maven wrapper, multi-stage Dockerfile, port 8080, package
  `com.peter_gerdzhikov.twitter_api_gateway`. Postgres 18, Kafka (KRaft) and MinIO in the root
  `docker-compose.yml`. No Spring Cloud Gateway until a downstream service exists (it arrives in phase 4).
- Layered controller → service → repository; every service is an interface + `Impl`; constructor injection;
  endpoints under `/api/v1`; `Clock` bean for all "now" comparisons (mutable test clock in tests).
- **Ported from SignalFlow (Q20):** password rules (8–72, upper/lower/digit, BCrypt 12, 4 in tests); `access_token`
  cookie (`HttpOnly`, `SameSite=Strict`, `COOKIE_SECURE`); CSRF off; public logout; no default `JWT_SECRET`;
  `ErrorResponseDTO` via `GlobalExceptionHandler` (never echoes internals); 8 KB body cap filter (`413`); dummy
  bcrypt on unknown login identifier.
- **Auth model:** HS256 JWT, claims `sub`/`username`/`email`/`iat`/`exp`, TTL 1h, no roles, no refresh, no revocation
  (Q5, Q19). Authorization reads claims only, never the DB (Q18). Public: `GET /actuator/health`,
  `POST /api/v1/auth/{register,login,logout,confirm,confirm/resend}`; everything else authenticated (Q29).
- **Schema:** `ddl-auto=update`, no Flyway (Q22–23); every unique constraint/FK explicitly named so services map
  `DataIntegrityViolationException` by constraint; cascades via `@OnDelete(CASCADE)`.

### Phase 1 — Auth
- **Entities:** `User` (username as typed + `usernameNormalized`, lowercased email, `enabled` = confirmed, Q4),
  `EmailConfirmationToken` (SHA-256 hash of a 32-byte random token, single-use, 24h), generic `Outbox`.
- **Endpoints:** `register` (201, no cookie, `409` by constraint name), `confirm` (204, pessimistic lock,
  `400` for unknown/used/expired), `confirm/resend` (always `202`, 60s cooldown under a user row lock, Q13),
  `login` (email or username, `401` generic, `403` only after a correct password, Q14/Q17), `logout`, `me`.
- **Events:** `user.confirmation-requested` (key `userId`, `eventId` for dedupe) written to the outbox in the
  register/resend transaction; `OutboxPublisherJob` (3s, 500/batch, 3 attempts then `FAILED`) sends to Kafka.
- **Cleanup:** daily job deletes unconfirmed users older than 7 days (tokens cascade, outbox untouched, Q12).
- **Tests:** full `AuthControllerIntegrationTest` catalog, publisher (Kafka + failure paths), cleanup, unit tests
  for ported security pieces, and a concurrency suite (duplicate register, parallel confirm/resend).

### Phase 2 — Profile
- **Entities:** `DbFile` (random UUID `objectKey`, detected `contentType`, `sizeBytes`); `User` gains
  `profilePicture`, `profileCoverPicture`, `bio` (≤160), `location` (≤30), `birthdate` (past); `@DynamicUpdate`.
- **MinIO:** official SDK, one private auto-created bucket, `ObjectStorageService` (failures → `502`),
  `ImageSignatureValidator` (magic bytes for JPEG/PNG/WebP, else `415`; client type/filename ignored).
- **Endpoints:** `GET /users/{username}` (unknown or unconfirmed → `404`), `PUT /users/me` (full replace, blank →
  null), `PUT|DELETE /users/me/{profile-picture|cover-picture}` (5 MB cap with route-specific body limit, empty →
  `400`, delete idempotent), `GET /files/{id}` (streamed, `private, immutable` cache, `nosniff`).
- **Upload order:** validate → put new object → DB transaction (save `DbFile`, repoint, delete old row) → after
  commit delete old object (orphan on failure is accepted). The transaction now **locks the user row first**, so
  same-user uploads/deletes serialise (audit finding 1; previously parallel uploads leaked rows and objects).
- **Tests:** profile/files HTTP suites, signature validator, upload failure-ordering unit tests, storage
  integration test, concurrency (edit ∥ upload keeps both; parallel uploads leave no orphan rows).

### Phase 3 — Follows
- **Entity:** `Follow` (unique pair, self-follow `@Check`, two keyset indexes, `createdAt` truncated to micros);
  `User.followersCount`/`followingCount` (`updatable = false`, `@Check >= 0`).
- **Counters (Q34–35):** native `INSERT … ON CONFLICT DO NOTHING` / `DELETE`; counters change by atomic
  `x = x ± 1` only when a row changed, in fixed `UUID.compareTo` order so mutual follows can't deadlock.
- **Endpoints:** `PUT|DELETE /users/{username}/follow` (204, idempotent; self → `400`, unknown/unconfirmed →
  `404`); profile gains counts + `followedByMe`; `GET /users/{username}/{followers|following}` with opaque
  keyset cursor (`FollowCursorCodec`), default 20, max 100, one `IN` query for `followedByMe` (Q36).
- **Tests:** follow/unfollow/profile/list HTTP suites, repository (`ON CONFLICT`, `@Check`s, cascade, keyset
  ties), codec + lock-order unit tests, concurrency (50 distinct followers, same-pair storm, A↔B ×50, mixed
  storm then `COUNT(*)` invariant).

### Phase 4 — Tweet routing
- **Proxy, no tweet code (T-Q1):** Spring Cloud Gateway MVC (BOM 2025.1.3). `TweetRoutesConfiguration` forwards
  `/api/v1/tweets/**` to `app.tweet-service.url` (`TWEET_SERVICE_URL`, default `http://localhost:8081`) with the
  path unchanged and HTTP/1.1 pinned. The route is `authenticated()` by the default rule. The tweet service's design
  and interview record (T-Qn) live in `../twitter_tweet_service/PLAN.md`.
- **Identity (T-Q2, T-Q14):** strip `Cookie`, `Authorization` and every `X-User-*` header (case-insensitive) first,
  then add `X-User-Id` = the JWT `sub`.
- **Body caps (T-Q4):** `RequestBodySizeLimitFilter` gets a third cap, `app.request.max-tweet-body-bytes`
  (`MAX_TWEET_BODY_BYTES`, 21 037 056), for exactly `POST /api/v1/tweets`; everything else keeps 8 KB.
  `spring.servlet.multipart.enabled=true` makes the starter's resolver skip parsing on proxied requests, so a
  21 MB body streams through byte-identical (see `DECISIONS.md`).
- **Failures:** `spring.http.clients` connect 2s / read 10s, one value for all routes. Connect failure → `502`,
  read timeout → `504`, a chunked body over a cap → `413`, no retries. Tweet-service error bodies pass through.
- **Tests:** `TweetRoutesIntegrationTest` (WireMock: auth, forwarding, identity, failures),
  `TweetBodyCapsIntegrationTest` (real server, recording stand-in), `TweetServiceContractIntegrationTest` (the real
  tweet service built from its Dockerfile, with its own Mongo/MinIO/Kafka on a Docker network), plus filter and
  handler unit tests. Scenarios are in `TESTING.md`.
- **Audit:** `exploit-report-2026-09-30-phase4.md`: 1 Low fixed (chunked overrun answered `502`), 1 Low and 2 Info
  logged below. `mvn verify` green 3× (573 tests).

### Phase 5 — People to follow
- **Endpoint:** `GET /api/v1/users?cursor=&size=` → `{items, nextCursor}`: every confirmed user except the caller, newest
  account first (Q1). Item `{id, username, bio, profilePictureUrl, followersCount, followedByMe}` (Q2). Other methods on
  the path answer `405` with `Allow: GET`; `HEAD` answers `200`; the trailing-slash path is `404`.
- **Code:** `UserListService` + `UserListServiceImpl` (read-only transaction), `UserController.getUsers`,
  `UserListItemResponseDTO` / `UserListResponseDTO` in `DTOs/response/users`, `ProfileMapper.toUserListItem`. The size
  check is shared with the follow lists in `utilities/paging/PageSizeValidator`; the cursor is `FollowCursorCodec`.
- **Queries:** `UserRepository.findUserListFirstPage` / `findUserListAfter` (`LEFT JOIN FETCH` of the picture; keyset on
  the row value `(created_at, id)`) over `ix_users_enabled_created (enabled, created_at, id)`. Two SQL statements per
  call, one for an empty page; counted in tests with Hibernate statistics (`SqlStatementCounter`, test profile only).
- **Behaviour pinned by tests, not by the plan:** a repeated `size=1&size=2` is `200` with the first value (Spring's
  conversion); a deleted caller's token sees every confirmed user, all `followedByMe = false`.
- **Tests:** `UserListControllerIntegrationTest` (80, each starting from an empty `users` table), repository, service,
  mapper and page-size unit tests, `UserListConcurrencyIntegrationTest` (follow/unfollow storm while paging; inserts during
  a walk), `people.spec.ts` (two journeys, 3× green). Scenarios are in `TESTING.md`; calls made while building in
  `DECISIONS.md`.
- **Audit:** `exploit-report-2026-10-04-phase5.md`: finding 1 (Medium: the `OR` cursor predicate made a crafted cursor
  scan every newer row; 8× slower at 300,003 users) fixed with the row-value form; finding 2 (Low: time-based ids carry
  the host's IP) accepted below. The index was used at 300,003 rows. `mvnw verify` green 3×.

## Test strategy (Q37–38)
Unit (Mockito) → repository (real Postgres) → HTTP e2e (Postgres, Kafka, MinIO via Testcontainers) →
concurrency (latch-released threads, assert final state). No embedded fakes; Docker is required. The approved
`@Disabled` catalog came first each phase, then each step enabled its group. Deterministic only: no
`Thread.sleep` (bounded Awaitility), test clock, jobs disabled and invoked directly, unique data via `TestUsers`,
Kafka assertions filtered by `userId`. A phase exits on `mvn verify` green 3× in a row.

---

## Phase 5 — People to follow ✅ **Done** (2026-10-04)

What was built is under "What was built" above (Phase 5). Design settled in the `/plan-backend` interview of 2026-10-04
(record at the bottom, Q1–Q3); `(my call)` items were accepted with the phase cut (Q3). Steps 31–35 are in the Status
table; scenarios are in `TESTING.md`.

- **Handoff to `frontend/PLAN.md`** (not built here): a "who to follow" view calls `GET /api/v1/users`, then follows
  `nextCursor` as the user scrolls or presses "more", sending `size` and the cursor back untouched. Each card shows
  `username`, `bio`, the picture and `followersCount`. The follow button calls the existing
  `PUT|DELETE /api/v1/users/{username}/follow`; the list is not refetched for that, so the UI flips `followedByMe` and
  moves `followersCount` by 1 itself. The list is ordered by registration time, so an account that confirms later
  appears at its registration position: at the top of the next fresh load if it is recent, never in a part of a walk
  that already passed it.
- **e2e (`people.spec.ts`, `e2e/tests/`):** (1) three accounts sign up and confirm; the first lists `GET /api/v1/users`,
  walks `nextCursor` (at most 5 pages; the new accounts are the newest) until it finds the other two, both
  `followedByMe: false`, itself absent; it follows one, lists again and sees `followedByMe: true` and `followersCount` 1
  on that one and 0 on the other; (2) an account registered but not yet confirmed is absent from the first page of
  `size=100`, then present after its confirmation; the same call without a cookie answers `401`. Both look only at their
  own users, because the database is shared with the specs running in parallel.

---

## Phase 4 accepted gaps
- **The tweet service trusts `X-User-Id`** and is safe only while nothing but the gateway can reach it.
  **Trigger:** any shared or deployed network → a shared internal secret or mTLS.
- **No rate limiting on tweet routes** (same as the gateway-wide gap).
- **Tweet images are proxied through the gateway** (same trigger as profile images → presigned URLs/CDN).
- **One 10s read timeout for every route.** **Trigger:** slow uploads start hitting `504` → a per-route timeout.
- **Only `X-User-*` headers are stripped;** look-alikes (`X-UserId`, `X-Original-User-Id`) reach the tweet service
  (phase 4 audit finding 2, Low). Safe while the tweet service reads only `X-User-Id`. **Trigger:** any downstream
  that trusts another identity-like header → forward an allowlist of request headers instead.
- **Path segments `;x=y`, `/./` and `//` answer `401`** even with a valid cookie (audit finding 3, Info). It fails
  closed and is identical to the anonymous answer. **Trigger:** a client that legitimately sends such paths.
- **Tomcat's HTML `400` page for paths it rejects** (audit finding 4, Info; gateway-wide). **Trigger:** a public
  deployment → an error page or filter that answers JSON.

## Handoff to `twitter_mail_service` (separate plan and `/grill-me`, Q8)
The plan ends at "the event is on Kafka". The mail service must consume `user.confirmation-requested` per
`EVENTS.md`, dedupe on `eventId` (outbox is at-least-once), render `confirmationUrl` and `expiresAt`, and say
**"If you didn't sign up, ignore this email"** (mitigates the squatter gap). The mail service now does all of
that, and `../e2e` has the click-the-link test: Playwright reads the link from the email in Mailpit. Gateway
tests still take the token from the outbox payload or Kafka record.

## Out of scope
Mail service; SPA pages (incl. `/confirm`); password reset, account deletion, username change, display names
(Q16); roles/admin (Q5); birthdate at signup and age checks (Q21); tweet logic (owned by
`../twitter_tweet_service`; the gateway only routes, phase 4), timelines, notifications, blocks, mutes, private
accounts; anonymous profile viewing (Q29); refresh tokens (Q19); image resizing; searching or filtering the people list,
hiding people the caller already follows, and any frontend step for it (phase 5, Q1–Q3).

## Accepted gaps — revisit when the named trigger lands
- **One `enabled` flag means "confirmed"; no roles/admin.** Trigger: first ban, or moderation features.
- **No rate limiting** except the resend cooldown (login capped only by bcrypt-12; register unthrottled; also
  lets a user spam uploads). Trigger: abuse, or a second instance.
- **Register leaks whether an email/username exists** (explicit `409`s). Login's `403` reveals "unconfirmed" only
  to someone holding the password (Q14).
- **Squatting:** a pending account holds an email/handle up to 7 days + cleanup; a real owner clicking the
  squatter's link confirms an account the squatter controls. Trigger: password reset exists, or first report.
- **Email + live confirmation token travel through Kafka in plaintext.** Trigger: Kafka exposed → TLS/SASL.
- **Outbox is at-least-once** (mail service dedupes on `eventId`); **no row-claiming** in the poller (trigger: second
  instance → `SKIP LOCKED`); **`FAILED` rows need manual handling** (trigger: first one that matters).
- **Scheduled jobs run on every instance.** Trigger: second instance → ShedLock/advisory lock.
- **Dev compose has default credentials and plaintext Kafka** (ports bound to `127.0.0.1`). Trigger: any shared
  environment.
- **`COOKIE_SECURE` defaults to `false`.** Trigger: first deployment.
- **Log lines can carry attacker-chosen or personal text** (unknown-path WARN, constraint-violation WARN with
  email). Trigger: logs shipped to a shared system.
- **`ddl-auto=update`, no migrations.** Trigger: second environment or first destructive change → Flyway.
- **JWT 1h, no refresh, no revocation.** Trigger: hourly logouts annoy, or need to kill sessions.
- **Orphan MinIO objects** when the post-commit delete of an old object fails. Trigger: storage growth → sweep job.
- **Images stored as uploaded: no resize, EXIF (incl. GPS) retained.** Trigger: before any public deployment.
- **Images proxied through the gateway.** Trigger: image traffic dominates → presigned URLs/CDN.
- **Any logged-in user can fetch any file id** (random UUIDs; all files are profile images). Trigger: first
  private media type.
- **No restrictive `Content-Security-Policy` on served files** (phase 2 finding 2, Low; not exploitable while
  only JPEG/PNG/WebP are accepted). Trigger: loosening the type check (e.g. SVG).
- **Hot-row contention on a popular account's counters.** Trigger: measurable follow latency → sharded counters.
- **No counter reconciliation job** (`@Check >= 0` catches only negative drift). Trigger: any observed drift.
- **Any logged-in user can page through every confirmed account** (phase 5), and the list shows each person's
  `followersCount`. Profiles were already readable by username; this makes them enumerable. Same gateway-wide
  no-rate-limit gap as above. **Trigger:** abuse, or a second instance → rate limit on `GET /api/v1/users`.
- **No popularity ordering** (phase 5, Q1): the list is newest account first, because counters move while a reader
  pages and would repeat or skip people. **Trigger:** newest-first stops being useful (many accounts, the frontend asks
  for "most followed") → a ranked list from a snapshot or a stored score, which needs its own design.
- **User ids are time-based UUIDs** (`@UuidGenerator(style = TIME)` in `CommonEntity`): they embed the gateway host's
  IPv4 address, JVM start time and a counter, and the people list hands out ids in bulk (phase 5 audit finding 2, Low;
  file ids are the same, so the phase 2 report's "random" was wrong). No id is a secret today. **Trigger:** a deployment
  whose network addresses matter, or any use of an id as a secret → a random or v7 generator (existing rows keep their ids).
- **The three follow-list queries still use the `OR` cursor predicate** (audit finding 1, note): a page reads the rows
  newer than the cursor within one account's follows. **Trigger:** an account with six-figure follows or a slow list →
  the row-value form the people list uses.
- **No search or filter on the people list** (phase 5). **Trigger:** the first request to find someone by name →
  a `q` parameter on the same endpoint.

---

## Interview record (`/plan-backend`, 2026-10-04)

New phase 5 of this plan: a paged "people you can follow" endpoint, paged like the timeline service's feed.

| # | Question | Answer |
|---|---|---|
| 1 | Order of the list | Newest account first (`createdAt DESC, id DESC` keyset). Popularity ordering is an accepted gap. List includes everyone confirmed except the caller, followed or not, marked `followedByMe` |
| 2 | Fields per person | `id`, `username`, `bio`, `profilePictureUrl`, `followersCount`, `followedByMe`; no email, location, birthdate, createdAt, followingCount or cover picture |
| 3 | Phase cut | One phase, steps 31–35: catalog, index + repository, service + endpoint, e2e, hardening. The listed calls were accepted with it: path `GET /api/v1/users`, paging as the follow lists, two statements per call, no new event or service call, the three accepted gaps |
| — | `(my call)` | Index `ix_users_enabled_created` (use unproven, logged as a gap); picture fetched in the page query, statements counted with Hibernate statistics; reuse of `FollowCursorCodec` (a follow-list cursor also decodes); a deleted caller sees everyone; the size check shared between the two lists instead of copied; `405` for other methods, `404` for the trailing slash |
