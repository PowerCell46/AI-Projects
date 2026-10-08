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
- **Superseded in phase 2, step 14: "at most 3 attempts" is gone; see "The conflict retrier" below.** **A delete write conflict is retried unless the tweet is gone** (step 8). The plan maps a conflict to `404`, but one
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

## Gateway user lookup (phase 2, step 12)

`UserLookupService` is ported from the timeline service: the gateway's `GET /internal/v1/users?ids=` with
`X-Internal-Secret`; any failure becomes `UpstreamUnavailableException` (`502`) or `UpstreamTimeoutException` (`504`).

- **Timeouts are fixed in `application.properties`** (`spring.http.clients.connect-timeout=2s`, `read-timeout=5s`),
  no env override: the spec says "same timeouts" and a new env name would be a new contract. The timeline service's
  names are prefixed because it shares the root `.env`; nothing here needs one yet.
- **No tweet-service RestClient and no executor.** Only the gateway is called, and a replies page makes one call, so
  the virtual-thread executor of the timeline service is not ported.
- **Packages:** `configurations/downstream`, `utilities/downstream`, `exceptions/upstream`, `DTOs/client` and
  `services/*/lookups`, grouped from the start (the flat packages already hold more than 5 files).
- **Startup failure tests** use `ApplicationContextRunner` on `RestClientConfiguration`: a missing secret fails on the
  unresolved placeholder, a short one on the 32-byte check.

## Reply document and repositories (phase 2, step 13)

- **`ReplyRepositoryCustom` has no cursor type.** `findFirstPage(tweetId, limit)` and `findPageAfter(tweetId,
  afterCreatedAt, afterId, limit)` take the keyset position as plain values; the cursor codec (step 15) decodes into
  them. The service asks for `size + 1` rows to know whether a next page exists.
- **`incrementReplyCount(id, delta)` on `TweetRepositoryCustom`**, an `$inc` that never touches `updatedAt`. `Tweet.replyCount`
  is a primitive `long`, so a tweet stored without the field reads `0`. A full `save` of a tweet would write the
  value it holds, so nothing saves an existing tweet.
- **`repositories` grouped** into `tweets`, `outbox` and `replies` (it would have held 7 files); the existing
  repository classes only changed package.

## The conflict retrier (phase 2, step 14)

- **`ConflictRetrier` is a service** (interface + `ConflictRetrierImpl`), not a utility: it holds the
  `TransactionTemplate`. It takes the transaction and a re-check that runs after every conflict (the tweet delete
  passes "tweet gone -> 404"; the reply delete will pass "reply gone -> 404"). The test class is
  `ConflictRetrierImplTest`, renamed from the catalog's `ConflictRetrierTest` to match the class under test.
- **Time source and sleeper are two small functional interfaces** (`MonotonicTimeSource`, `Sleeper` in
  `utilities/retry`), wired as beans in `RetryConfiguration`; tests drive both by hand. The 2 s budget and the
  5-30 ms pause are constants, not properties. The budget is checked after a conflict and the re-check, so a call can
  run one attempt past 2 s.
- **An interrupted pause** restores the interrupt flag and answers `503 BUSY`, not the conflict.
- **`ErrorResponseDTO` gains an optional `code`**, left out of the JSON when null (asked, 2026-10-06). Only
  `BUSY` (`WriteConflictBudgetExceededException`, `503`) sets it so far; `CALLER_UNKNOWN` comes with step 15.
- **`ReplyNotFoundException` (`404`) exists from this step**, because the retrier's catalog scenario for a reply gone
  between attempts needs it.
- **The tweet-delete test of the catalog was split:** the removal of the replies is enabled now
  (`DeleteTweet.should_remove_the_replies_of_the_tweet_and_keep_the_replies_of_others_when_the_tweet_is_deleted`); the
  `404` on `GET .../replies` stays disabled until the endpoint exists (step 15).
- **The old unit test "give up and rethrow after 3 attempts" became "throws busy past the retry budget"** (the plan
  replaces the 3 attempts).

## Create and list replies (phase 2, step 15)

- **Content is validated before the caller lookup**, so a blank or too long reply costs no gateway call. The limit is
  the tweets' `app.tweets.max-content-code-points`; the messages are "A reply needs text." and "A reply can be at most
  280 characters." (`EmptyReplyException`, `ReplyContentTooLongException`, both `400`).
- **`CallerUnknownException` answers `403`** with `code: "CALLER_UNKNOWN"`, in the exceptions root next to
  `InvalidCallerIdentityException`.
- **A page asks for `size + 1` rows**; the extra row only says there is a next page, and the cursor is built from the
  last row of the page, also when authors are then left out. So `items` can be shorter than `size` and only a `null`
  `nextCursor` means the end.
- **Bad cursor and bad size are checked before the tweet is looked up**, so they win over a `404`.
- **The caller's author shape in the create answer** comes from the lookup that already ran, not a second call.
- **`ConflictRetrier.execute(transaction)`** (no re-check) added for the reply create: a tweet deleted between
  attempts fails the retried `$inc`, which already answers `404`.
- **Packages:** `ReplyCursor` / `ReplyCursorCodec` / `PageSizeValidator` in `utilities/paging`, `InvalidCursorException`
  and `InvalidPageSizeException` in `exceptions/paging`, the reply DTOs in `DTOs/response/replies`, `ReplyMapper` in
  `utilities/mappers`. `TweetMapper` moved there too (the flat `utilities` package already held 6 files).
- **`TweetResponseDTO.replyCount` is a `long` after `content`.** It is on the public read, the create and edit
  answers and the internal batch (the timeline service ignores the unknown field until step 18).

## Edit and delete replies (phase 2, step 16)

- **Edit answers `404` when the conditional update matched nothing:** the reply is gone, of another tweet or someone
  else's, and the answer does not say which (superseded 2026-10-07: it used to tell someone else's reply apart with
  `403`). Validation runs before the caller lookup, as on create.
- **Delete needs no caller lookup** (the caller is only compared with the two authors) and reads the reply, then the
  tweet's author, before the transaction. A reply of another tweet is `404`, so its existence is not told.
- **Inside the delete transaction:** delete the reply by id and tweet, `404` when nothing was removed, then `-1`
  on the tweet. If that `-1` matches no tweet the transaction aborts with `404` (cannot happen while the tweet delete
  removes its replies in its own transaction). The re-check after a write conflict is "reply gone -> `404`".
- **Delete by anyone who wrote neither the reply nor the tweet answers `404`** (superseded 2026-10-07: it used to be
  `403`). `NotReplyAuthorException` and `NotAllowedToDeleteReplyException` are gone; only `CALLER_UNKNOWN` is a
  reply `403`.
- **The four-route Identity check** is a parameterized test in `ReplyControllerIntegrationTest.Identity` (method and
  path suffix), not an addition to the tweet routes' `endpoints()`.

## Audit fixes (phase 2, step 22, 2026-10-07)

- **Only the tweet-create route takes multipart.** `RequestBodySizeLimitFilter` answers `415` ("This route does not
  accept multipart bodies.") to a `multipart/form-data` request anywhere else. The counting wrapper cannot see
  multipart (the container parses it), so a chunked upload skipped the 8 KB cap on every route; the check is on the
  content type, before any parsing. No other route reads multipart.
- **A non-owner's edit or delete of a reply is `404`, not `403`** (audit finding 2, Info). Reply ids are public in the
  list, so this hides little, but the answer no longer separates "not yours" from "not there". The tweet routes keep
  their `403`. The reply edit's re-read of the reply to tell the two apart is gone.

## The paged by-author read and the count (frontend step 26)

- **One keyset cursor for replies and tweets.** `ReplyCursor` / `ReplyCursorCodec` became `KeysetCursor` /
  `KeysetCursorCodec` (a timestamp and an id); the replies list is unchanged. A tweet page continues strictly after
  `(createdAt, id)` descending, on `ix_tweets_author_created_id`.
- **The page returns whole tweets**, the same body as `findByIds`, so the timeline service builds feed-shaped items
  without a second read. `size` defaults to 20 and must be 1–100 (`PageSizeValidator`, as replies).
- **`GET /api/v1/tweets/count?authorId=`** answers `{"count": n}` from `countByAuthorId` (a count query on the
  same index prefix); an unknown author is `0`. It sits on the identity-checked public route like every `/api/v1`
  endpoint, though the count is not tied to the caller.
- **`DTOs/response` grouped:** the tweet DTOs moved to `DTOs/response/tweets` (the package would have held 7 files).
