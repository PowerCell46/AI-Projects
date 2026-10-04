# Decisions

Non-obvious calls made *during* implementation - the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Infra and config

- **`mongo:8.2`, not `mongo:8`** (step 1). On the dev Docker VM (kernel 7.0.12) `mongo:8` and `mongo:8.0` refuse to
  start: "Linux kernel versions 6.19 and newer has a known incompatibility" (SERVER-121912). `8.2` is pinned in the
  compose service and in `AbstractMongoIntegrationTest`, over `7` (a further step from the plan's major). Short-lived
  line: move back to `mongo:8` once the fix ships.
- **`SERVER_PORT` stays out of the root `.env`** (step 1). Boot binds it to `server.port` for every app started from
  the shared `.env`, so it would move the gateway too. The property default is 8081; `.env.example` carries the
  variable commented out, with the reason.
- **MinIO keys are required at startup** (step 4). `app.minio.access-key` / `secret-key` have no default, as in the
  gateway, so `spring-boot:run` needs `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` exported and MinIO up (the bucket
  initializer fails startup when it is unreachable).
- **The test Mongo container needs `.withReplicaSet()`** (step 3). Testcontainers 2.x's `MongoDBContainer` starts a
  standalone server without it (1.x was a replica set); standalone failed every transactional write with "does not
  support retryable writes". The base class always asks for the replica set.

## API behaviour

- **`X-User-Id` must be the canonical UUID form** (step 1). `UUID.fromString` accepts `1-1-1-1-1`; the resolver only
  accepts a value that round-trips to the 36-character form (case-insensitive), so a malformed header is a `400`.
- **GET routes declare `@CurrentUserId` without using it** (step 6). Every route must answer 400 without
  `X-User-Id`, and the resolver only runs for a declared parameter; a filter would need its own path rules.
- **Text is trimmed with `strip()`** (step 5). It removes Unicode whitespace, `trim()` only ASCII control chars and
  spaces. A tweet of only U+00A0 is not blank (`isWhitespace` is false for it); accepted, it is a visible-length-1
  tweet either way.
- **A missing `content` is blank** (step 7). `{}` and `{"content": null}` follow the `""` rules: 400 on a text-only
  tweet, accepted when it has images.
- **Update re-reads the tweet after the conditional write** (step 7). `updateContentIfAuthor` only says whether a
  document matched; a second `findById` returns current data, and a delete in between makes that read a `404` too.
- **Internal batch read reuses `findAllById`** (timeline step 1). Already one `_id $in` query. The 1-100 limit counts
  ids as sent, before repeats collapse, so request size is bounded; out of range answers 400 through
  `TweetIdsOutOfRangeException`.
- **`views` is removed** (timeline step 24). `GET /tweets/{id}` is a plain `findById` that writes nothing. Old dev
  documents keep a stale `views` field: Spring Data ignores it on read and edits are a conditional `$set`, so it is
  never touched again. No `$unset` script (development data only).

## Mongo

- **`insert`, not `save`, for new documents** (step 3). Ids are assigned, so `save` is an upsert and would silently
  overwrite a colliding id. Tests that need a plain write use `save`.
- **Custom repository operations return `boolean` / `Optional`** (step 3). `updateContentIfAuthor` and
  `deleteIfAuthor` say whether a document matched; services map `false` to `404`.
- **A delete write conflict is retried unless the tweet is gone** (step 8). The plan maps a conflict to `404`, but one
  can also come from a concurrent *edit* landing after the delete's snapshot, and `404` would leave the tweet alive.
  On a conflict (label `TransientTransactionError` or code 112) the service checks existence: gone -> `404`; still
  there -> retry, at most 3 attempts, then rethrow (`500`). So "edit || delete" always ends with no document.
- **Collections are created at startup** (timeline step 12). Three first-ever tweets at once on a fresh Mongo made
  each transaction try to create `tweets`, and all but one failed with `WriteConflict` (500).
  `MongoCollectionInitializer` creates `tweets` and `outbox` as a `SmartInitializingSingleton` (runs before the web
  server accepts requests; an `ApplicationRunner` would leave a window). If another instance wins, the failure is
  swallowed once the collection exists. Chosen over a transaction retry, which would hide the cause.

## Kafka and outbox

- **Publisher tests prove absence with a sentinel** (step 9). "A FAILED message is never published" publishes a
  sentinel after the action, reads until it shows up, and asserts the failed key is not among the records before it,
  instead of the gateway's fixed 2-second poll. Failing-send and `FAILED`-at-3 paths are mocked unit tests.
- **A failed attempt is saved back with `save`** (step 9). That is an upsert on an assigned id: safe with one
  instance, but a second could resurrect a message the other just deleted. Covered by the "no row-claiming" accepted
  gap in `PLAN.md`.

## Tests

- **Body cap through MockMvc, multipart limits over real HTTP** (step 5). MockMvc applies the body-size filter to a
  raw body but never Spring's multipart limits, so "one byte over 5 MB" and "4 images at exactly 5 MB" live in
  `TweetUploadLimitsIntegrationTest` (real port), as in the gateway.
- **The concurrency suite runs through MockMvc** (step 10). The contended operations are Mongo writes, not the
  servlet layer, so threads overlap on one latch without a real port.

## By-author read (frontend step 14)

`GET /internal/v1/tweets/by-author/{authorId}?since=&limit=` answers `[{id, createdAt}]`, newest first, for the
timeline's back-fill on follow.

- **Both parameters are required.** `since` uses `@DateTimeFormat(iso = DATE_TIME)`; without it Spring's `Instant`
  conversion also accepts epoch millis, which the contract calls a bad value. `limit` has no default; a missing,
  non-numeric or out-of-range one is a 400, range-checked in the service (`TweetLimitOutOfRangeException`).
- **The repository projects only `id` and `createdAt`.** The partial `Tweet`s never leave the service.
- **Index `ix_tweets_author_created_id {authorId: 1, createdAt: -1, _id: -1}`.** Without `_id` the planner could not
  use the index for the `createdAt, _id` sort and sorted in memory (300,000 keys for 100 rows). The new name avoids a
  conflict with an old-named index that a dev database keeps until dropped.
- **A `createdAt` tie orders by `_id` descending** (binary UUID byte order). Tests compare ids as lower-case strings,
  not with `UUID.compareTo` (signed longs).
