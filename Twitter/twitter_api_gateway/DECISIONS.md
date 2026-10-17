# Decisions

Non-obvious calls made during implementation, the ones hard to re-derive from the code. Conventions live in
`CLAUDE.md`; open and accepted gaps are in `../ACCEPTED_GAPS_ROOT.md`.

One bullet per decision: what was chosen and why. Group by topic, keep the phase in parentheses for provenance. Skip
anything the code or its tests already say.

## Setup and test infrastructure

- **One root `.env.example`; Postgres on 5432, Kafka on 9094** (phase 1). Same ports as SignalFlow, so the property
  defaults work with no env, but the two composes can't run together. The optional `API_GATEWAY_DATASOURCE_*` lines
  stay commented out: an exported empty value would override the default with an empty string.
- **The test clock stands still** (phase 1). `MutableClock` moves only through `advance`, `setInstant` and `reset`, so
  "61 seconds later" is exact. It is shared across a cached context, so a test that advances it calls `reset()`.
- **Every full-context test extends `AbstractMinioIntegrationTest`** (phase 2): the bucket initializer is an
  `ApplicationRunner` that fails startup without MinIO. `@DataJpaTest` slices run no runners and stay on the Postgres base.
- **MinIO runs from `alpine/minio`, pinned, as root** (phase 2). `minio/minio` is no longer pullable and the alpine
  image's `minio` user can't write `/data`. The healthcheck is `wget`, since the image has no `mc`.
- **`okhttp-jvm` is a direct dependency** (phase 2): `io.minio:minio` pulls an `okhttp` 5.x shell and
  `MinioClient.builder()` exposes `okhttp3` types.
- **The test Postgres runs with `max_connections=300`** (timeline phase 1): each cached Spring context keeps a
  10-connection pool until the JVM exits.
- **`UserListControllerIntegrationTest` wipes `users` before each test** (phase 5), because exact counts need an empty
  table. **`UserListConcurrencyIntegrationTest` deletes its outbox rows afterwards**: its storm leaves ~200 `PENDING`
  rows and the publisher only takes `app.outbox.batch-size`, which broke `OutboxPublisherServiceKafkaIntegrationTest`.

## Auth and tokens

- **Token `iat`/`exp` and error-body timestamps use the system clock** (phase 1): the decoder validates `exp` against
  it, so a token minted from a moved test clock would be born expired. Everything that *compares* against now uses `Clock`.
- **Normalisation uses `Locale.ROOT`** (phase 1): a Turkish locale turns `I` into `ı` and breaks uniqueness and lookups.
- **The unknown-identifier login path hashes the submitted password** (phase 1), so it costs one bcrypt like a wrong one.
- **Passwords are capped at 72 bytes, not characters** (phase 1): `@Size(max = 72)` counts characters, bcrypt takes
  bytes, so a multi-byte password gave a 500. `@MaxUtf8Bytes(72)` sits beside `@Size` on register and login.
- **Startup refuses a `JWT_SECRET` under 32 bytes** (phase 1); Nimbus only fails when signing, a 500 on every login.

## Email confirmation and outbox

- **The token hash is SHA-256 over the UTF-8 bytes of the 43-character string** (phase 1), not a decoded form, so it
  can't disagree with the link.
- **Resend reads the user before locking it** (phase 1). A confirm committing in between can cost one extra email to a
  confirmed user: harmless, bounded by the cooldown.
- **The poll job's `initialDelay` equals its delay** (phase 1); `fixedDelay` alone fires once at startup and would drain
  rows mid-suite.
- **A row Kafka cannot take stops the batch and costs no attempt; only a row Kafka refuses for its own content counts
  one** (2026-10-10, replaces "every failed row counts and the batch goes on"). Counting every failure turned a ~30 s
  outage into permanent `FAILED` rows, and going on let a later event overtake an earlier one. A timeout, network or
  leader error, authorization error or interrupt leaves the row `PENDING` and ends the poll. Only
  `RecordTooLargeException`, `RecordBatchTooLargeException` and `InvalidTopicException` (anywhere in the cause chain)
  count an attempt (3, then `FAILED`) and the batch goes on. A whitelist, so an error that is not the row's fault
  (credentials, ACLs) never burns attempts. `KafkaTemplate.send` reports an unreachable broker by throwing
  `KafkaException("Send failed")` from the call, not the future, so both paths are caught.
- **The producer gives up before the poller does** (2026-10-10): `max.block.ms` 5 s, `request.timeout.ms` 5 s,
  `delivery.timeout.ms` 8 s, under `app.outbox.send-timeout` (10 s). At the defaults a down broker blocked `send()` a
  minute per row, and a record the poller had given up on could still be delivered later, a duplicate once retried.
  `OUTBOX_SEND_TIMEOUT` must stay above 8 s.
- **`FAILED` rows turn health to `DEGRADED`, not `DOWN`** (2026-10-10): `DOWN` fails the container health check, and
  the frontend, timeline and mail services wait for a healthy gateway. `DEGRADED` is a custom status ranked between
  `OUT_OF_SERVICE` and `UP` (`management.endpoint.health.status.order`) and answers 200. Requeueing is a manual
  statement, in `docs/EVENTS.md`.

## Profiles and pictures

- **Each picture slot is unique per file, and the FKs have no `ON DELETE` action** (phase 2): two users can't share a
  `DbFile`, deleting one a user still points at is rejected, and `SET NULL` would hide a flow that deleted in the wrong
  order. Upload repoints first, deletes second.
- **`ProfileService` returns the response DTO** (phase 2): picture URLs read lazy `DbFile` associations that must be
  read inside the transaction. `ProfileMapper` is static.
- **`@Past` on a birthdate uses Bean Validation's clock, not the `Clock` bean** (phase 2). Today is rejected too.
- **The upload body cap matches `PUT` on the two exact picture paths only** (phase 2): `RequestBodySizeLimitFilter`
  runs before security, so any other method, variant or trailing slash keeps the 8 KB cap. A mismatch fails small.
- **A missing object in storage is a 502** (phase 2): the `DbFile` row is the truth, so a row without its object is a
  storage fault. Deleting a missing key is a no-op.
- **The DB change runs in a `TransactionTemplate`, not `@Transactional`** (phase 2): the new object is stored before the
  transaction and the old one deleted after the commit.
- **The 5 MB limit is also checked in the service** (phase 2), because MockMvc never applies Spring's multipart limits.
  `PictureUploadLimitsIntegrationTest` proves the real ones on a real server, with a hand-built body that has a
  `Content-Length`.
- **Routes are four explicit mappings, not `/me/{slot}`** (phase 2), so the filter's exact-path match holds.
- **Concurrent uploads to one slot are serialised by a row lock** (phase 2 audit): `loadLockedUser` takes
  `UserRepository.lockById` first, otherwise parallel uploads left orphan rows and objects. One account's burst now
  queues on the lock while holding pool connections.
- **File serving reads the row, then streams the object, with no transaction** (phase 2), so a download doesn't pin a
  connection. Any logged-in user can read any file id; ids are time-based UUIDs, not random, but pictures are public.
- **`spring.servlet.multipart.enabled=true` is set explicitly** (phase 4): the gateway-mvc starter defaults it to
  `false` when unset and the picture `PUT`s answered 500 on a real server.
- **`PATCH /api/v1/users/me` replaced the `PUT`** (frontend phase 4): a missing or `null` field is kept (a birthdate
  can't be cleared), a blank bio or location clears it, values are stripped. Bio ≤ 160 and location ≤ 60 are counted
  in code points after stripping (`@MaxCodePoints`, not `@Size`), so the gateway and frontend agree on emoji. `PUT`
  answers 405.
- **`User.location` is `varchar(60)`.** `ddl-auto=update` won't widen an existing column; local databases need
  `ALTER TABLE users ALTER COLUMN location TYPE varchar(60);`.

## Follows

- **`Follow` stands alone, without `CommonEntity`** (phase 3): it is only inserted or deleted. The id and `createdAt`
  come from the native `insertIfAbsent`; the caller truncates `createdAt` to microseconds because the list cursor is
  built from it.
- **Counter statements take a delta** (phase 3): one `SET x = x + :delta` per counter, called with `+1`/`-1`. Columns
  carry `default 0` so `ddl-auto=update` can add them to a table with rows. `update` mode doesn't add the `@Check`, so
  only a fresh schema gets `ck_users_follow_counts_non_negative` (the `ddl-auto` gap in `../ACCEPTED_GAPS_ROOT.md`).
- **The fixed lock order uses `UUID.compareTo`, which is signed** (phase 3): a unit test's "larger" id must be `7fff…`.
- **List endpoints** (phase 3):
  - `InvalidCursorException` and `InvalidPageSizeException` are both 400. The size is checked in the service, not
    with `@Min`/`@Max`, so the message is ours.
  - The cursor is strict: `decode` re-encodes and must match, so `?cursor=` is a 400, not a first page.
  - Validation order is size, cursor, then user lookup, so a bad parameter is a 400 even for an unknown user.
  - Four repository queries, not one with a null cursor. Postgres breaks ties on unsigned UUID bytes, unlike
    `UUID.compareTo`, so tests compare ids in `UUID.toString` order.
  - `followedByMe` on items is one `IN` query per page, skipped for an empty page.
- **Repeated `size=1&size=2` is a 200 with the first value** (phase 5); a repeated `cursor` fails as invalid. The
  follow lists behave the same.
- **The user-list after-cursor query uses the row value `(created_at, id) < (:c, :id)`** (phase 5): Postgres can use
  it as an index bound, the `OR` form read every newer row. The follow lists keep the `OR` form since one account's
  follows bound it.

## Routing

- **Both service routes share `CallerIdentityFilters`** (timeline phase 1): drop `Cookie`, `Authorization` and every
  `X-User-*`, then set `X-User-Id` from the JWT. The HTTP/1.1-only client customizer stays in
  `TweetRoutesConfiguration` because it is global.
- **One `timeline-service` route with exact or narrow patterns** (timeline phases 1–4, tweet service phase 2, frontend
  phase 4): `/api/v1/feed` and `/api/v1/views` are exact; `/api/v1/saved-tweets/**` and `/api/v1/likes/**` cover the
  list and `/{tweetId}`; `/api/v1/tweet-details/*` and `/api/v1/author-tweets/*` take one segment. A sub-path of an
  exact route isn't forwarded and falls to the default rule (authenticated, then 404). The like stub controller was
  deleted when likes joined this route.
- **Multipart pass-through needs no extra code** (phase 4): a 21 MB multipart `POST /api/v1/tweets` reaches the
  downstream byte-identical. The only code is a third cap in `RequestBodySizeLimitFilter` for `POST` on the exact path.
  The cap tests run on a real server, with `RecordingHttpServer` as the stand-in, because WireMock's client fails on
  bodies over Jackson's 20 MB string limit.
- **The contract test brings its own Mongo, MinIO and Kafka** (phase 4): the shared containers aren't on a Docker
  network, so the real tweet service couldn't reach them. The gateway read timeout is 10 s for that context only, the
  image is built from `../twitter_tweet_service/Dockerfile` with the Twitter root files as its context (the Dockerfile
  expects the root), and the container gets a fixed test-only `INTERNAL_API_SECRET`
  (the image refuses to start without one).

## Internal API and events

- **`user.followed` is emitted on a new follow only**: enqueued in the follow transaction when `insertIfAbsent` returns
  1, so a repeat, a concurrent duplicate or a rollback sends nothing. Keyed by the followee id (the mail goes to them);
  the payload carries their email and both usernames so the mail service needs no call back.
- **`user.unfollowed` is enqueued only when `deleteByPair` returns 1**, keyed by `followerId`. `occurredAt` is
  `clock.instant()` untruncated, like `user.followed`.
- **`InternalApiSecretFilter` reads the path the way Spring MVC does** (timeline phase 1), via
  `UrlPathHelper.getPathWithinApplication`, so a raw variant can't skip the prefix check. The secret is compared in
  constant time; a missing or wrong secret gets the unknown-path 404. `/internal/v1/**` is in `PUBLIC_MATCHERS`.
- **Follower pages use the `FollowerEdge` projection** (timeline phase 1), so a page of 1,000 loads no user rows. The
  user lookup leaves out unconfirmed accounts and counts the 1-100 limit on ids as sent, before repeats collapse.
- **The follow check answers `200 {"following": true|false}`** (frontend phase 3) for every well-formed pair, unknown
  ids and a self pair included (`false`). The earlier `204`/bare `404` was indistinguishable from the guard or a gateway
  build without the route, so the timeline service couldn't tell "not following" from a misconfiguration. A 404 now
  means a wrong secret or a missing route; malformed ids are a 400.

## Two gateway instances (2026-10-10)

- **The deployment runs two gateways on one Postgres, behind nginx round robin.** Nothing else keeps per-instance
  state: sessions are JWT cookies and the resend cooldown is a column.
- **The outbox poller claims its batch with `FOR UPDATE SKIP LOCKED`** (`@Lock(PESSIMISTIC_WRITE)`, lock timeout hint
  `-2`) in one transaction per poll. The locks live while the batch is sent, so a poll holds a connection for up to
  `batch-size` times `send-timeout` in the worst case.
- **The cleanup job and the seeder take a session-level Postgres advisory lock** (`AdvisoryLockService`) on a connection
  of their own, held until the action ends (Postgres frees it if the instance dies). Chosen over ShedLock: no new
  dependency or table for two jobs. The seeder holds it for the whole seed, so the second instance skips, and a later
  one finds users and skips anyway.
- **`MinioBucketInitializer` accepts `BucketAlreadyOwnedByYou` and `BucketAlreadyExists`**: both instances can find the
  bucket missing on a cold start.
- **The second instance is `gateway-2` in compose**, with the network alias `gateway`, so other services' calls to
  `http://gateway:8080` and the frontend's nginx reach both. Not run end to end yet.

## Demo data (2026-10-17)

- **Every demo user is a real person with a password of their own** (`SEED_PASSWORD_<NAME>`, 12 of them, none with a
  default). The shared `SEED_PASSWORD` is gone. Usernames can't contain spaces, so `Gabi_Penchev` and `Gabi_Stoimenov`
  stand for the two Gabis; the picture files are named after the username.
