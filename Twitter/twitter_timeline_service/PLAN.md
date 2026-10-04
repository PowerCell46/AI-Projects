# Twitter Timeline Service — plan

Everything about one user's relationship to tweets they didn't write. Built in three ordered phases:

1. **Feed.** Tweets from people you follow (and your own), newest first, kept 7 days.
2. **Saved tweets.** Bookmark, unbookmark, list.
3. **Views.** Unique viewers per tweet, reported by the browser; the counter moves here out of the tweet service.

Liked tweets are out of scope (a separate service later). Every design call below was settled in the `/grill-me`
interview of 2026-10-03 (record at the bottom, Q-numbers in parentheses).

Building it touches two other projects. Their steps live **in this plan**, marked **[gateway]** or **[tweet]**;
their own `PLAN.md` files only point here. Those steps follow that project's `CLAUDE.md` and standing rules
(including its `TESTING.md` and `mvn verify` 3×).

**Hard rule for every step:** no step starts on a red or missing test, and each step ends green. Nothing in
phase *n+1* starts until phase *n*'s final gate passes (`mvn verify` green 3× in a row).

## Status

| Phase | Scope | Done | Audit |
|---|---|---|---|
| 1 | Feed (steps 1–13) | 2026-10-03 | exploit-report-2026-10-03.md |
| 2 | Saved tweets (steps 14–19) | 2026-10-03 | exploit-report-2026-10-03-phase2.md |
| 3 | Views (steps 20–27) | 2026-10-03 | exploit-report-2026-10-03-phase3.md |

---

## Global design

### Stack and layout

```
Twitter/
├── docker-compose.yml          + postgres-timeline (127.0.0.1:5433)
├── .env.example                + timeline section, INTERNAL_API_SECRET, TIMELINE_SERVICE_URL, USER_UNFOLLOWED_*
├── e2e/                        + tweet service, timeline service, mongo, postgres-timeline; one API spec per phase
├── twitter_api_gateway/        + /internal/v1/** (secret), user.unfollowed, routes to this service
├── twitter_tweet_service/      + /internal/v1/tweets?ids=, views removed (phase 3)
└── twitter_timeline_service/
    ├── PLAN.md                 this file
    ├── CLAUDE.md               ported from the tweet service's, adapted
    ├── DECISIONS.md            calls made during implementation
    └── TESTING.md              hand-maintained HTTP + listener scenario catalog
```

- **Build:** Java 25, Spring Boot **4.1.1**, Maven wrapper, multi-stage Dockerfile, all as the gateway. Folder
  and artifact `twitter_timeline_service`, package `com.peter_gerdzhikov.twitter_timeline_service`, port
  **8083**.
- **Starters:** `data-jpa`, `webmvc`, `actuator` (health only), `validation`, `kafka`, `restclient`, plus
  `postgresql` and `lombok`. Tests: `*-test` per module, `testcontainers-postgresql`, `testcontainers-kafka`,
  WireMock (`wiremock-standalone` + `wiremock-testcontainers-module`, versions as the gateway's pom),
  `awaitility`. No Spring Security, no MinIO, no Mongo.
- **Packages:** `configurations`, `controllers`, `DTOs/{request,response,event,client}`, `entities`,
  `exceptions`, `jobs`, `listeners`, `repositories`, `services/{interfaces,implementations}`, `utilities`.
- **Code rules, as the gateway:** controller → service → repository; every service an interface + `Impl`;
  constructor injection only; endpoints under `/api/v1`; tests snake_case `should_<behaviour>_when_<condition>`;
  listeners thin (as the mail service).
- **Ported, don't reinvent:** `ErrorResponseDTO` + `GlobalExceptionHandler` + `ErrorResponseWriter`, the 8 KB
  `RequestBodySizeLimitFilter`, `@CurrentUserId` + its resolver (canonical-UUID check), and the `Clock` bean
  (from the tweet service); `FollowCursorCodec` (from the gateway); Kafka consumer factory, error handler,
  backoff and DLT recoverer (from the mail service).

### Identity

- **"Who am I" is `X-User-Id`**, set by the gateway exactly as for the tweet routes. Trusted blindly: safe only
  while port 8083 is reachable through the gateway alone (accepted gap, same as the tweet service).
- **No secret on this service's own routes.** The shared secret guards the *gateway's* internal endpoints, which
  sit on the public port.

### Data (Q7, Q18)

- **Own Postgres container** `postgres-timeline` (`postgres:18`, `127.0.0.1:5433`, volume
  `postgres_timeline_data`, healthcheck and limits as `postgres-api-gateway`), database `twitter_timeline_service_db`, env
  `TIMELINE_DATASOURCE_USERNAME/PASSWORD` (shared with the app) and `TIMELINE_POSTGRES_DB`.
- **App env names are prefixed** because the root `.env` is shared: `TIMELINE_DATASOURCE_URL` (default
  `jdbc:postgresql://localhost:5433/twitter_timeline_service_db`), `TIMELINE_DATASOURCE_USERNAME`,
  `TIMELINE_DATASOURCE_PASSWORD`. `SERVER_PORT` stays out of `.env` (tweet service `DECISIONS.md`).
- **`ddl-auto=update`, no Flyway**, every constraint and index explicitly named, as the gateway.
- **No `CommonEntity` (Q18).** Rows are only inserted or deleted, never edited, so the natural pair is the key
  (as the gateway's `Follow`). Writes are native `INSERT … ON CONFLICT DO NOTHING`, so a redelivered event or a
  double-click changes nothing, and no Redis inbox is needed.
- **Timestamps** come from events or from the `Clock`, truncated to microseconds so they round-trip through
  Postgres exactly (the cursor is built from them).

### Calls to other services (Q4–Q6, Q14, Q15)

| Caller → callee | Endpoint | Used for |
|---|---|---|
| timeline → gateway | `GET /internal/v1/users/{id}/follower-ids?cursor=&size=` | fan-out on `tweet.created` |
| timeline → gateway | `GET /internal/v1/users?ids=` | author of each feed/saved item |
| timeline → tweet | `GET /internal/v1/tweets?ids=` | tweet bodies; existence check on save and on view reports |

- **Gateway internal endpoints** need `X-Internal-Secret: <INTERNAL_API_SECRET>` (Q5). Missing or wrong → `404`,
  as if the path didn't exist. `INTERNAL_API_SECRET` has no default and must be ≥32 bytes; both the gateway and
  this service refuse to start without it. Compared in constant time.
- **The tweet service's internal endpoint has no secret** (Q6): the gateway only forwards `/api/v1/tweets/**`, so
  it isn't public, and anything that reaches port 8081 can already do everything.
- **Base URLs:** `GATEWAY_INTERNAL_URL` (default `http://localhost:8080`), `TWEET_SERVICE_URL` (shared with the
  gateway, default `http://localhost:8081`).
- **Timeouts:** connect 2s, read 5s (`TIMELINE_HTTP_CONNECT_TIMEOUT`, `TIMELINE_HTTP_READ_TIMEOUT`). They stay
  under the gateway's 10s read timeout for proxied routes. HTTP/1.1 pinned (as the gateway's `RestClient`).
- **On a read (feed, saved list, view check): fail the request (Q15).** Unreachable or `5xx` → `502`, timeout →
  `504`, same messages as the gateway. No partial pages, no `author: null`.
- **Trade-off on record (Q6):** reading the tweet service's Mongo directly would be a few ms per page faster. We pay
  that for decoupling; the cache is an accepted gap.

### Kafka (Q15)

- **Consumes:** `tweet.created`, `tweet.deleted` (tweet service), `user.unfollowed` (gateway, new in phase 1).
  Consumer group `twitter-timeline-service`, `auto-offset-reset=earliest`, concurrency 3. Topic names read the
  producers' env vars (`TWEET_CREATED_TOPIC_NAME`, `TWEET_DELETED_TOPIC_NAME`, `USER_UNFOLLOWED_TOPIC_NAME`).
- **As the mail service:** one typed consumer factory and one error handler per topic (no type headers); DTO bean
  validation, where an invalid event is dead-lettered without retries; everything else retried with blocking
  exponential backoff (`KAFKA_RETRY_*`, 2s → 60s, 8 retries; test profile 100ms → 200ms, 3), then
  `DeadLetterPublishingRecoverer`. DLTs `tweet.created-dlt`, `tweet.deleted-dlt`, `user.unfollowed-dlt`, 3
  partitions each, declared here. This service declares no other topic.
- **Never log the payload** (tweet text). Log `eventId`, `tweetId`, user ids.

### Lists and cursors

- **Opaque keyset cursor** `(timestamp, tweetId)`, base64url, strict round-trip check: `TimelineCursorCodec`,
  ported from `FollowCursorCodec`. Bad cursor → `400`.
- **`size`:** default 20, min 1, max 100, otherwise `400`. Response `{ "items": [...], "nextCursor": "…" | null }`,
  as the gateway's follow lists.
- **Reads `size + 1` rows** to know whether there is a next page; `nextCursor` comes from the last *row read*,
  not the last item returned.
- **A short page is not the end.** Rows whose tweet (or author) no longer exists are skipped, so a page can hold
  fewer than `size` items; only `nextCursor: null` means the end.

### Time

- `Clock` bean for "now" (saved-at, retention cutoff), never `Instant.now()`. Tests use the mutable test clock.
- The retention job is disabled in the test profile and invoked directly.

---

## Phase 1 — Feed ✅ **Done** (2026-10-03)

### What was built

- **`FeedEntry` (`feed_entries`)**, PK `(user_id, tweet_created_at, tweet_id)` doubling as the page index (Hibernate
  orders key columns by attribute name, hence the attribute `ownerId`); indexes on `tweet_id`, `(user_id, author_id)`
  and `tweet_created_at`. Rows are only inserted (native `INSERT … ON CONFLICT DO NOTHING`, `unnest(uuid[])`) or
  deleted (Q2, Q7, Q18).
- **`tweet.created` fan-out (Q4, Q8):** validate (invalid → DLT, no retry), author's own entry, then the gateway's
  follower pages (1,000 each), one transaction per page; any failure retries from the top and the duplicates skip.
- **`tweet.deleted`:** delete by `tweet_id`, idempotent; a delete before the create leaves orphans that reads skip
  and retention removes.
- **`user.unfollowed` (Q9):** delete the follower's entries by that author up to `occurredAt`. A follow back-fills
  the feed through `user.followed` (frontend plan step 16).
- **Kafka:** `ErrorHandlingDeserializer` + JSON per topic, exponential backoff then `<topic>-dlt`, concurrency 3,
  one shared application context in tests so consumers don't split partitions.
- **`GET /api/v1/feed?cursor=&size=`:** keyset page (`size` 1–100, default 20), base64url `(micros, tweetId)` cursor
  with strict canonical round-trip; the tweet call and the author call run side by side on virtual threads (Q14);
  entries whose tweet or author is gone are skipped and `nextCursor` still advances (from the last row read);
  `502` / `504` on downstream failure, nothing leaked (Q15).
- **Retention (Q3):** `FeedRetentionJob` (`FEED_CLEANUP_CRON`, `FEED_CLEANUP_ZONE`) → `FeedRetentionService`, batches
  of `FEED_CLEANUP_BATCH_SIZE` until a short batch; `FEED_RETENTION` (7d) and the batch size checked at startup;
  cron `-` in the test profile.
- **[gateway]** `/internal/v1/**` behind `InternalApiSecretFilter` (404 without `X-Internal-Secret`;
  `INTERNAL_API_SECRET`, 32+ bytes, no default): `GET /internal/v1/users/{id}/follower-ids` and
  `GET /internal/v1/users?ids=`; `user.unfollowed` through the outbox (`EVENTS.md`); `/api/v1/feed` route
  (`TimelineRoutesConfiguration`, `TIMELINE_SERVICE_URL`) with the identity filters shared in
  `CallerIdentityFilters`.
- **[tweet]** `GET /internal/v1/tweets?ids=` (1–100 ids, one `_id $in` query, no view counted); collections created
  at startup (`MongoCollectionInitializer`), found by the e2e's first-write race.
- **Tests:** 234 in this service, 0 `@Disabled`; gateway 694; tweet service 232; e2e `feed.spec.ts` (3 tests) with the
  `createAccount` fixture. Catalog in `TESTING.md`; calls made while building in each project's `DECISIONS.md`.
- **Audit:** `exploit-report-2026-10-03.md`, nothing above Low; one fixed, four in Accepted gaps below.

---

## Phase 2 — Saved tweets ✅ **Done** (2026-10-03)

### What was built

- **`SavedTweet` (`saved_tweets`)**, PK `(user_id, tweet_id)` (attribute `ownerId`, as `FeedEntry`), plus `author_id`
  (from the existence check) and `saved_at` (`Clock`, micros); indexes `ix_saved_tweets_user_saved`
  `(user_id, saved_at, tweet_id)` for the list and `ix_saved_tweets_tweet` for the delete. Inserted (native
  `INSERT … ON CONFLICT DO NOTHING`) or deleted, never edited (Q10, Q18). No retention job: kept until unsaved or the
  tweet is deleted.
- **`PUT /api/v1/saved-tweets/{tweetId}` → 204:** asks the tweet service whether the tweet exists (`TweetNotFoundException`
  → `404` "Tweet not found."; `502` / `504` on a downstream failure, nothing stored), then inserts; saving again keeps
  the original `saved_at`.
- **`DELETE /api/v1/saved-tweets/{tweetId}` → 204** whether or not it was saved, no existence check, so a deleted tweet
  can still be removed.
- **`GET /api/v1/saved-tweets?cursor=&size=`:** newest saved first, cursor `(saved_at, tweet_id)` through the feed's
  codec, `SavedTweetsResponseDTO` (`items`, `nextCursor`), missing tweets or authors skipped, `nextCursor` from the last
  row read.
- **`TweetItemAssemblyService`:** the feed's parallel tweet + author lookup, skip-the-missing and mapping, extracted so
  the feed and the saved list share it (Q14); `FeedServiceImpl` now calls it.
- **`tweet.deleted`** removes the tweet from `feed_entries` and `saved_tweets` in one transaction
  (`FeedEntryCleanupServiceImpl.onTweetDeleted`, `@Transactional`).
- **[gateway]** `/api/v1/saved-tweets/**` joins the feed on the one timeline route (`TimelineRoutesConfiguration`), same
  identity filters and timeouts; 9 new route tests.
- **Tests:** 370 in this service (repository 23, service unit, controller 51, listener group 4, concurrency 3: 50
  parallel saves, save racing unsave), 0 `@Disabled`; e2e `saved-tweets.spec.ts` (3 tests) with `postTweet` /
  `deleteTweet` moved to the shared fixtures. Catalog in `TESTING.md` (HTTP and listener scenarios); calls made while
  building in `DECISIONS.md`.
- **Audit:** `exploit-report-2026-10-03-phase2.md`, nothing above Low; the one Low is folded into the "no rate
  limiting" gap below.

---

## Phase 3 — Views ✅ **Done** (2026-10-03)

### What was built

- **`TweetView` (`tweet_views`)**, PK `(tweet_id, viewer_id)` (Hibernate orders the key by attribute name, so the key also
  serves the delete by tweet), no timestamp; **`TweetViewCount` (`tweet_view_counts`)**, PK `tweet_id`, `views` (`bigint`,
  `updatable = false`, `ck_tweet_view_counts_views_non_negative`). Unique viewers, the author's own views count (Q11, Q12,
  Q18).
- **`POST /api/v1/views`** `{ "tweetIds": [...] }` → 204: 1–50 ids counted as sent, duplicates collapsed, one tweet-service
  call for the distinct ids (unknown ids dropped silently), then `ViewRecordingService.record` in one transaction:
  `INSERT … ON CONFLICT DO NOTHING RETURNING tweet_id`, then the counter upsert for the returned ids only. Both statements
  sort the ids in SQL (`ORDER BY`), so overlapping reports lock in one order. Tweet service down → `502` / `504`, nothing
  recorded. Reports log nothing.
- **`GET /api/v1/views?tweetIds=a,b`** → `{ "<id>": <views>, … }`: 1–100 ids, one entry per distinct id in request order,
  `0` for unknown ids, one `IN` query, no downstream call (Q13). Both endpoints need `X-User-Id`; a bad list is
  `InvalidTweetIdsException` → `400` through one `TweetIdsValidator`.
- **Inline counts (Q13):** feed and saved items gain `views` (second field of `TweetItemResponseDTO`) from
  `ViewService.countViews`, one query per page, `0` when missing.
- **`tweet.deleted`** also removes the tweet's `tweet_views` rows and its counter, in the one transaction with the feed and
  saved deletes (`FeedEntryCleanupServiceImpl.onTweetDeleted`).
- **[tweet]** `views`, `findAndIncrementViews` and the `$inc` are gone: `GET /tweets/{id}` is a plain `findById` and writes
  nothing; old dev documents keep a stale field Spring ignores (`DECISIONS.md` there). Its view scenarios were replaced by
  "no `views`, document unchanged" checks.
- **[gateway]** `/api/v1/views` is an exact path on the one timeline route (`TimelineRoutesConfiguration`), same identity
  filters and timeouts; the contract test with the real tweet service no longer asserts `views`.
- **Handoff to `frontend/PLAN.md`** (not built here): report a tweet once it has been at least half visible for about a
  second; batch the ids and `POST /api/v1/views` every few seconds (≤50 per request) and on page hide; render `views` from
  the feed and saved items, and from `GET /api/v1/views` on a tweet page.
- **Tests:** 432 in this service, 0 `@Disabled` (repository 21 new, controller 42 + inline 8 + listener 5, unit, and
  `ViewConcurrencyIntegrationTest`: 50 viewers, one viewer 50×, two workers in opposite order); tweet service 226; gateway
  711; e2e `views.spec.ts` (17 specs in all, `follow` moved to the shared fixtures). Catalog in `TESTING.md` (HTTP and
  listener scenarios); calls made while building in each project's `DECISIONS.md`.
- **Audit:** `exploit-report-2026-10-03-phase3.md`, nothing above Low; two Lows folded into the accepted gaps below (viewer
  minting through a forged `X-User-Id`, no rate limit on reports) and two Infos.

---

## Left open

- **The opposite-order concurrency scenario does not prove the counter upsert's lock order:** removing the `ORDER BY` did
  not fail it in five variants (`DECISIONS.md`, step 27; `exploit-report-2026-10-03-phase3.md` #4). **Decide:** a test that
  observes the lock order, or accept that the sort is a documented-hazard defence with no regression guard.

---

## Test strategy (all phases, Q17)

Unit (Mockito) → repository (real Postgres) → client (WireMock) → listener (real Kafka + Postgres, WireMock
downstreams) → HTTP (`*ControllerIntegrationTest`) → concurrency (latch-released threads, assert final state)
→ one API-level Playwright spec per phase in `../e2e` over the real chain (gateway, tweet service, timeline
service, Kafka, both Postgres, Mongo, MinIO). No embedded fakes; Docker is required.

**Deterministic only:** no `Thread.sleep` (Awaitility with an explicit `atMost`; `expect.poll` with a timeout in
Playwright); mutable test clock; the retention job disabled and invoked directly; fresh random user and tweet ids
per test; Kafka and DLT assertions filtered by the test's own key; negative async assertions via a sentinel event
under the same key. The approved `@Disabled` catalog comes first each phase. A phase exits on `mvn verify` green
3× in a row; a single flaky run is a failure to investigate, not to retry.

## Standing rules (go into `CLAUDE.md` in step 4)

- A behaviour change ships with its test change in the same commit.
- Never delete, `@Disabled`, or weaken an assertion to make a build pass without explicit approval (except the
  catalog each phase enables step by step).
- Adding, removing or changing a scenario in any `*ControllerIntegrationTest` or `*ListenerIntegrationTest`
  updates `TESTING.md` in the same change.
- Never report a suite that didn't run (for example, no Docker) as passing.
- Never log a payload, tweet text or the internal secret.

## Out of scope

Liked tweets; the frontend feed, saved and views UI (handoff above); a tweet details page; back-fill beyond 50 tweets / 7 days;
"tweets by author" / profile timelines; ranking or an algorithmic feed; replies, retweets, quotes; notifications;
account deletion; a Redis cache; a DLT replay tool.

## Accepted gaps — revisit when the named trigger lands

- **Tweets are fetched over HTTP on every read (Q6),** a few ms slower than reading Mongo directly. **Trigger:**
  the feed's p95 above about 200 ms → cache tweets by id (Redis, evicted on `tweet.deleted`, plus a
  `tweet.updated` event), or keep one copy per tweet in this DB.
- **A feed or saved read needs both downstreams (Q15);** one down → the whole page fails. **Trigger:** availability
  complaints → serve tweets without authors, or a cache.
- **Fan-out on write for big accounts.** A tweet from an account with N followers is N/1,000 gateway calls and N
  rows, holding a partition meanwhile. **Trigger:** fan-out lag, or an account past ~10k followers → fetch big
  accounts' tweets at read time instead (hybrid).
- **Unfollow race (Q9):** a tweet fanned out at the moment of an unfollow can still land; it drops off within 7
  days.
- **Fan-out uses followers at processing time.** A tweet whose event was delayed (retries, DLT) goes to whoever
  follows the author then.
- **Orphans from cross-topic order.** `tweet.deleted` before `tweet.created`, or a save / view racing a delete,
  leaves rows for a dead tweet. Feed orphans are hidden and expire in 7 days; saved and view orphans are hidden but
  never removed. **Trigger:** noticeable growth → a sweep job checking ids against the tweet service.
- **This service trusts `X-User-Id` blindly** (as the tweet service); since phase 3 that also lets anyone who reaches port 8083 mint viewers and inflate any tweet's count (`exploit-report-2026-10-03-phase3.md` #1). **Trigger:** port 8083 reachable by anything
  but the gateway → the shared secret on this service too, or mTLS.
- **One static internal secret on the gateway's public port, no rotation.** **Trigger:** any deployment → a private
  port or mTLS for `/internal/**`, and rotation.
- **The tweet service's internal endpoint has no secret.** Same trigger as its `X-User-Id` gap.
- **No rate limiting on feed reads** (`exploit-report-2026-10-03.md` #3), and none on saves (each one a tweet-service call and an INFO log line, `exploit-report-2026-10-03-phase2.md` #1) or view reports (each a tweet-service call and up to 50 new rows, `exploit-report-2026-10-03-phase3.md` #2). **Trigger:** abuse, or a second instance.
- **View rows kept forever** (one per viewer per tweet). **Trigger:** table size → HyperLogLog (e.g. Redis
  `PFADD`) for the count.
- **Hot counter row** on a viral tweet. **Trigger:** view-report latency → buffered or sharded counters.
- **The author's own views count (Q12).** **Trigger:** a complaint → pass the author per id from the tweet lookup
  and skip it.
- **No cap on saved tweets.** **Trigger:** abuse or table size.
- **Start order:** a listener started before the producer auto-creates its topic with 1 partition (same as the
  mail service's open item 2). **Trigger:** that item's decision applies here too.
- **DLT names are `<topic>-dlt`,** except `user.followed-timeline-dlt`: the mail service already owns
  `user.followed-dlt`. **Trigger:** a second service consumes another of these topics → a service-specific DLT
  name for that one.
- **No DLT replay tool.** **Trigger:** the first dead-lettered record someone wants processed.
- **Scheduled retention runs on every instance.** **Trigger:** a second instance → ShedLock.
- **`ddl-auto=update`, no migrations.** **Trigger:** a second environment or the first destructive change → Flyway.
- **`tweet.created` takes any `createdAt`** (audit #2): a far-future time pins the entry atop feeds and past
  retention. Needs write access to the topic. **Trigger:** any producer besides the tweet service → reject a
  `createdAt` more than a few minutes ahead (to the DLT).
- **Rejected `/internal/**` calls log one WARN line each** (audit #4). **Trigger:** log volume from probing →
  rate limit or sample the line.
- **A client's `X-Internal-Secret` is forwarded by the tweet and feed routes** (audit #5); ignored downstream.
  **Trigger:** a downstream starts reading that header → strip it in `CallerIdentityFilters`.
- **Dev compose has fixed credentials** (bound to `127.0.0.1`). **Trigger:** any shared environment.
- **Stale `views` field in existing dev tweet documents.** **Permanent** unless someone wants it gone.

---

## Interview record (`/grill-me`, 2026-10-03)

| # | Question | Answer |
|---|---|---|
| 1 | What's in the service, and its name? | Feed, saved tweets, views. `twitter_timeline_service`. Liked tweets later, elsewhere |
| 2 | What happens to a feed tweet once shown? | A normal timeline: entries stay, reads write nothing, no `hasBeenSeen` |
| 3 | How long does an entry stay? | 7 days from the tweet's time; nightly batched cleanup |
| 4 | How does the service learn Bob's followers? | HTTP to a gateway internal endpoint (no coupling to the user DB) |
| 5 | How is that endpoint hidden? | Shared secret header, `404` without it |
| 6 | How does the feed get tweet bodies? | One batch call per page to the tweet service; direct Mongo would be faster, decoupling wins |
| 7 | Which database? | Postgres, in its own container |
| 8 | Does Bob see his own tweets? | Yes |
| 9 | Follow / unfollow effects? | Unfollow removes the author's tweets (new `user.unfollowed`); follow shows only new tweets |
| 10 | Save twice / unsave unsaved? | Idempotent `PUT` / `DELETE`, `204`; save checks the tweet exists, unsave doesn't |
| 11 | What counts as a view? | The browser reports tweets actually on screen, batched `POST /views` |
| 12 | 5 scroll-pasts = 5 views or 1? | 1: unique viewers, counter bumped only on a new pair; author counts; ids checked |
| 13 | Where is the count shown? | Inline in feed and saved, plus `GET /views` for details and liked; `views` leaves `Tweet` |
| 14 | How does the feed show the author? | The timeline service fetches authors from the gateway, in parallel with tweets |
| 15 | A dependency is down? | Fan-out: retry, then DLT. Reads: `502` / `504` |
| 16 | Build order and where cross-project steps live? | Feed → saved → views; gateway and tweet steps in this plan |
| 17 | Cross-service test? | One API-level Playwright spec per phase in `../e2e` |
| 18 | `CommonEntity` for the new tables? | No: the pair is the key |
