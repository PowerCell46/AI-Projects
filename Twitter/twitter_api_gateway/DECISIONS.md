# Decisions

Non-obvious calls made *during* implementation, the ones hard to re-derive from the code. Design settled up front
lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One bullet per decision: what was chosen and why, in a line or two. Group by topic and keep the plan step in
parentheses so references from `PLAN.md`, `TESTING.md` and the exploit reports still resolve. Skip anything the
code or its tests already say.

## Setup and test infrastructure

- **One root `.env.example`; Postgres on 5432, Kafka on 9094** (step 1). Same ports as SignalFlow, so the property
  defaults work with no env, but the two composes can't run together. The optional `API_GATEWAY_DATASOURCE_*` lines
  stay commented out: an exported empty value would override the default with an empty string.
- **The test clock stands still** (step 1). `MutableClock` moves only through `advance`, `setInstant` and `reset`, so
  "61 seconds later" is exact. It is shared across a cached context, so a test that advances it calls `reset()`.
- **Every full-context test extends `AbstractMinioIntegrationTest`** (step 11). The bucket initializer is an
  `ApplicationRunner` that fails startup without MinIO. `@DataJpaTest` slices run no runners and stay on the Postgres
  base.
- **MinIO runs from `alpine/minio`, pinned, as root** (step 11). `minio/minio` is no longer pullable, and the
  alpine image's `minio` user can't write `/data`. The compose healthcheck is `wget`, since the image has no `mc`.
- **`okhttp-jvm` is a direct dependency** (step 11). `io.minio:minio` pulls an `okhttp` 5.x shell, and
  `MinioClient.builder()` exposes `okhttp3` types, so main code didn't compile without it.
- **The test Postgres runs with `max_connections=300`** (timeline step 11). Each cached Spring context keeps a
  10-connection pool until the JVM exits, and the extra route contexts pushed the suite past the default 100.
- **`UserListControllerIntegrationTest` wipes `users` before each test** (phase 5 step 33). Exact counts need an
  empty table on the shared Postgres. No other suite wipes users.
- **`UserListConcurrencyIntegrationTest` deletes its outbox rows after each test** (phase 5 step 35). Its
  follow/unfollow storm leaves ~200 PENDING rows, and the publisher only takes the first `app.outbox.batch-size`,
  which made `OutboxPublisherServiceKafkaIntegrationTest` fail. Other suites adding many outbox rows will hit the
  same limit.

## Auth and tokens

- **Token `iat`/`exp` and error-body timestamps use the system clock** (step 4). The decoder validates `exp`
  against the system clock, so a token minted from a moved test clock would be born expired. A deliberate exception
  to "never call `Instant.now()`"; everything that *compares* against now uses `Clock`.
- **Normalisation uses `Locale.ROOT`** (step 3). A Turkish default locale would turn `I` into `ı` and break
  uniqueness and lookups.
- **The unknown-identifier login path hashes the submitted password** (step 8), so it costs one bcrypt like a wrong
  password.
- **Passwords are capped at 72 bytes, not characters** (step 10). `@Size(max = 72)` counts characters while bcrypt
  takes 72 bytes, so a multi-byte password gave a 500. `@MaxUtf8Bytes(72)` sits beside `@Size` on register and login.
- **Startup refuses a `JWT_SECRET` under 32 bytes** (step 10). Nimbus rejects it only when signing, which would
  turn a misconfiguration into a 500 on every login.

## Email confirmation and outbox

- **`ConfirmationRequestService` is shared by register and resend** (step 5). "Store a fresh token and queue the
  event" is one unit, joining the caller's transaction.
- **Confirm and resend live in `EmailConfirmationService`** (step 7), keeping the clock and cooldown out of
  `AuthServiceImpl`.
- **The token hash is SHA-256 over the UTF-8 bytes of the 43-character string** (step 5), not a decoded form, so it
  can't disagree with what the link carried.
- **Resend reads the user before locking it** (step 7). A confirm committing in between can cost one extra email to
  a confirmed user. That is harmless and bounded by the cooldown.
- **A token expiring exactly now is expired** (step 7): `expiresAt.isAfter(now)`. The cooldown mirrors it.
- **Event JSON has no field order** (step 5). Jackson 3 sorts alphabetically; the contract is the set of names.
- **The poll job's `initialDelay` equals its delay** (step 6). `fixedDelay` otherwise fires once at startup, which
  would drain rows mid-suite even with the test profile's 24h delay.
- **A failed outbox row doesn't stop the batch** (step 6), so a poison row can't block newer ones. An
  `InterruptedException` restores the flag and counts as a failed attempt.
- **The cleanup job logs the count; the service only deletes** (step 9), like the outbox publisher pair.

## Profiles and pictures

- **Each picture slot is unique per file, and the FKs have no `ON DELETE` action** (step 13). Two users can't share
  a `DbFile`, and deleting one a user still points at is rejected. The upload flow repoints first and deletes second,
  and `SET NULL` would hide a flow that deleted in the wrong order.
- **`ProfileService` returns the response DTO** (step 14), because the picture URLs read lazy `DbFile` associations
  that must be read inside the transaction. `ProfileMapper` is static because upload and delete return the same shape.
- **`@Past` on a birthdate uses Bean Validation's clock, not the `Clock` bean** (step 14), so the test clock doesn't
  move it. Today's date is rejected too.
- **Length checks run on the raw string, before trimming** (step 14). A 160-character bio plus spaces is a 400.
- **The upload body cap matches `PUT` on the two exact picture paths only** (step 15). `RequestBodySizeLimitFilter`
  runs before security, so any other method, path variant or trailing slash keeps the 8 KB cap. A mismatch fails
  small, never large. `UploadLimitsConfigurationTest` pins the derived multipart limits.
- **A missing object in storage is a 502** (step 15). The `DbFile` row is the source of truth for existence, so a
  row without its object is a storage fault. Deleting a missing key is a no-op.
- **The DB change runs in a `TransactionTemplate`, not `@Transactional`** (step 16). The new object must be stored
  before the transaction and the old one deleted after the commit.
- **The 5 MB limit is also checked in the service** (step 16), because MockMvc never applies Spring's multipart
  limits. `PictureUploadLimitsIntegrationTest` proves the real limits on a real server, with a hand-built body so
  the request has a `Content-Length`.
- **Routes are four explicit mappings, not `/me/{slot}`** (step 16), so the filter's exact-path match holds.
- **Concurrent uploads to one slot are serialised by a row lock** (step 16 audit fix). `loadLockedUser` takes
  `UserRepository.lockById` first; without it parallel uploads left orphan rows and objects. The cost is that one
  account's burst queues on the lock while holding pool connections (`SECURITY-AUDITS.md`, phase 2).
- **File serving reads the row, then streams the object, with no transaction** (step 17), so a download doesn't pin
  a connection. Any logged-in user can read any file id: ids are time-based UUIDs, not random, but the pictures are public anyway.
- **`spring.servlet.multipart.enabled=true` is set explicitly** (step 25). The gateway-mvc starter otherwise sets
  it to `false` when unset, and the picture `PUT`s answered 500 on a real server. With it on, the starter's
  `GatewayMvcMultipartResolver` also skips parsing for proxied requests.
- **`PATCH /api/v1/users/me` replaced the `PUT`** (frontend step 25). A missing or `null` field is kept (so a
  birthdate can't be cleared), a blank bio or location clears it, values are stripped. Bio ≤ 160 and location ≤ 60
  are counted in **code points after stripping** by `@MaxCodePoints`, not `@Size` (UTF-16 units), so the gateway and
  the frontend agree on emoji. `PUT` answers 405.
- **`User.location` is `varchar(60)`.** `ddl-auto=update` won't widen an existing column; local databases need
  `ALTER TABLE users ALTER COLUMN location TYPE varchar(60);` (a fresh Testcontainers database is created at 60).

## Follows

- **`Follow` stands alone, without `CommonEntity`** (step 20). It is only inserted or deleted. The id and
  `createdAt` come from the native `insertIfAbsent`, and the caller truncates `createdAt` to microseconds because
  the list cursor is built from it.
- **Counter statements take a delta** (step 20): one `SET x = x + :delta` per counter, called with `+1`/`-1`. The
  columns carry `default 0` so `ddl-auto=update` can add them to a table with rows. `update` mode doesn't add the
  `@Check`, so only a fresh schema gets `ck_users_follow_counts_non_negative` (see the `ddl-auto` gap in `PLAN.md`).
- **The fixed lock order uses `UUID.compareTo`, which is signed** (step 21). Any total order works, but a unit
  test's "larger" id must be `7fff…`, not `ffff…`.
- **The self-follow check runs after the target lookup** (step 21), since the request carries only a username.
  Nothing is written before the check.
- **`followedByMe` is false on every response about your own profile** (step 22), without a query.
- **List endpoints** (step 23):
  - `InvalidCursorException` and `InvalidPageSizeException` are both 400. The size is checked in the service, not
    with `@Min`/`@Max`, so the message is ours.
  - The cursor is strict: `decode` re-encodes and must match, so `?cursor=` (empty) is a 400, not a first page.
  - Validation order is size, cursor, then user lookup, so a bad parameter is a 400 even for an unknown user.
  - Four repository queries, not one with a null cursor. Postgres breaks ties on unsigned UUID bytes, which differs
    from `UUID.compareTo`, so tests compare ids in `UUID.toString` order.
  - `followedByMe` on items is one `IN` query per page, skipped for an empty page.
  - `FollowListService` keeps read paths out of `FollowServiceImpl`.
- **Repeated `size=1&size=2` is a 200 with the first value** (phase 5 step 33), and a repeated `cursor` fails as
  invalid. The follow lists behave the same, and the tests pin it.
- **The user-list after-cursor query uses the row value `(created_at, id) < (:c, :id)`** (phase 5 step 35), which
  Postgres can use as an index bound. The `OR` form read every row newer than the cursor. The follow-list queries
  keep the `OR` form, since one account's follows bound it (accepted gap). Time-based ids carrying the host IP is
  an accepted gap with a trigger.

## Routing

- **Both service routes share `CallerIdentityFilters`** (timeline step 11): drop `Cookie`, `Authorization` and every
  `X-User-*`, then set `X-User-Id` from the JWT. The HTTP/1.1-only client customizer stays in
  `TweetRoutesConfiguration` because it is global.
- **One `timeline-service` route with exact or narrow patterns** (timeline steps 11, 17, 25, 32): `/api/v1/feed` and
  `/api/v1/views` are exact, `/api/v1/saved-tweets/**` and `/api/v1/likes/**` cover the bare path (the list) and
  `/{tweetId}`. A sub-path of an exact route isn't forwarded and falls to the default rule (authenticated, then 404).
- **Multipart pass-through needs no extra code** (step 27). A 21 MB multipart `POST /api/v1/tweets` reaches the
  downstream byte-identical. The only code is a third cap in `RequestBodySizeLimitFilter` for `POST` on the exact
  path. The cap tests run on a real server, and the stand-in is `RecordingHttpServer`, not WireMock, whose client
  fails on bodies over Jackson's 20 MB string limit.
- **The contract test brings its own Mongo, MinIO and Kafka** (step 29). The shared containers aren't on a Docker
  network, so the real tweet service couldn't reach them. The gateway read timeout is raised to 10s for that
  context only, and the image is built from `../twitter_tweet_service`, so the test assumes that layout.
- **The like stub is a controller with no service** (frontend step 1). `PUT`/`DELETE /api/v1/likes/{tweetId}`
  answered 204 and stored nothing. Replaced by the timeline route in timeline step 32: `LikeController`, its test and
  its `TESTING.md` section are deleted, and the three like paths are served by the timeline service.

## Internal API and events

- **`user.followed` is emitted on a new follow only.** It is enqueued in the follow
  transaction when `insertIfAbsent` returns 1, so a repeat, a concurrent duplicate or a rollback sends nothing. The
  key is the followee id because the mail goes to them, and the payload carries their email and both usernames so
  the mail service needs no call back.
- **`user.unfollowed` is enqueued only when `deleteByPair` returns 1**, keyed by `followerId`. `occurredAt` is
  `clock.instant()` untruncated, like `user.followed`.
- **`InternalApiSecretFilter` reads the path the way Spring MVC does** (timeline step 2), via
  `UrlPathHelper.getPathWithinApplication`, so a raw variant can't skip the prefix check. The secret is compared in
  constant time, and a missing or wrong secret gets the unknown-path 404. `/internal/v1/**` is in `PUBLIC_MATCHERS`.
- **Follower pages use the `FollowerEdge` projection** (timeline step 2), so a page of 1,000 loads no user rows.
  The user lookup leaves out unconfirmed accounts and counts the 1-100 limit on ids as sent, before repeats collapse.
- **The follow check answers `200 {"following": true|false}`** (frontend steps 15 and 24), for every well-formed
  pair, unknown ids and a self pair included (`false`). It first answered `204`/bare `404`, but the guard and a
  gateway build without the route also answer a bare 404, so the timeline service couldn't tell "not following" from
  a misconfiguration. A 404 now means a wrong secret or a missing route. Malformed ids are a 400.

## The tweet-details route and the contract test (tweet service phase 2, step 20)

- **`/api/v1/tweet-details/*` joins the `timeline-service` route**, one path segment, so a path below the id is not
  forwarded. The same identity filters apply.
- **`TweetServiceContractIntegrationTest` hands the real tweet-service container an `INTERNAL_API_SECRET`** (a fixed,
  test-only value). Since tweet service step 12 the image refuses to start without one, which the gateway's suite
  only showed at this step. No assertion changed.

## The author-tweets route (frontend step 28)

- **`/api/v1/author-tweets/*` joins the `timeline-service` route**, one path segment, with the same identity filters as
  `tweet-details`: `X-User-Id` set from the JWT, a forged one replaced, the cookie and `Authorization` dropped.
