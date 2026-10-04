# Twitter Timeline Service — plan

Everything about one user's relationship to tweets they didn't write. Built in three ordered phases:

1. **Feed.** Tweets from people you follow (and your own), newest first, kept 7 days.
2. **Saved tweets.** Bookmark, unbookmark, list.
3. **Views.** Unique viewers per tweet, reported by the browser; the counter moves here out of the tweet service.

Liked tweets are out of scope (a separate service later). Every design call below was settled in the `/grill-me`
interview of 2026-10-03 (record at the bottom, Q-numbers in parentheses).

Building it touched two other projects; their steps lived in this plan, marked **[gateway]** or **[tweet]**, and their
own `PLAN.md` files only point here. Where things live now: calls made while building in `DECISIONS.md`, the test
catalog in `TESTING.md`, conventions and standing rules in `CLAUDE.md`, the security audits in `SECURITY-AUDITS.md`.

## Status

| Phase | Scope | Done |
|---|---|---|
| 1 | Feed (steps 1–13) | 2026-10-03 |
| 2 | Saved tweets (steps 14–19) | 2026-10-03 |
| 3 | Views (steps 20–27) | 2026-10-03 |

Back-filling the feed on follow (`user.followed`) was added later by the frontend plan (step 16); see `DECISIONS.md`.

---

## Global design

- **Build:** Java 25, Spring Boot **4.1.1**, Maven wrapper, as the gateway; package
  `com.peter_gerdzhikov.twitter_timeline_service`, port **8083**. Starters: `data-jpa`, `webmvc`, `actuator` (health
  only), `validation`, `kafka`, `restclient`. No Spring Security, no MinIO, no Mongo.
- **Ported, don't reinvent:** error shape, the 8 KB body-size filter, `@CurrentUserId` and the `Clock` bean (tweet
  service); `FollowCursorCodec` (gateway); the Kafka consumer factory, error handler, backoff and DLT recoverer (mail
  service).

### Identity

- **"Who am I" is `X-User-Id`**, set by the gateway as for the tweet routes. Trusted blindly: safe only while port
  8083 is reachable through the gateway alone (accepted gap).
- **No secret on this service's own routes.** The shared secret guards the *gateway's* internal endpoints, which sit
  on the public port.

### Data (Q7, Q18)

- **Own Postgres container** `postgres-timeline` (`postgres:18`, `127.0.0.1:5433`), database
  `twitter_timeline_service_db`. App env names are prefixed because the root `.env` is shared:
  `TIMELINE_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`. `SERVER_PORT` stays out of `.env`.
- **`ddl-auto=update`, no Flyway**, every constraint and index explicitly named, as the gateway.
- **No `CommonEntity` (Q18).** Rows are only inserted or deleted, never edited, so the natural pair is the key. Writes
  are native `INSERT … ON CONFLICT DO NOTHING`, so a redelivered event or a double click changes nothing, and no Redis
  inbox is needed.
- **Timestamps** come from events or the `Clock`, truncated to microseconds so they round-trip through Postgres.

### Calls to other services (Q4–Q6, Q14, Q15)

| Caller → callee | Endpoint | Used for |
|---|---|---|
| timeline → gateway | `GET /internal/v1/users/{id}/follower-ids?cursor=&size=` | fan-out on `tweet.created` |
| timeline → gateway | `GET /internal/v1/users?ids=` | author of each feed/saved item |
| timeline → gateway | `GET /internal/v1/users/{a}/follows/{b}` | back-fill check on `user.followed` |
| timeline → tweet | `GET /internal/v1/tweets?ids=` | tweet bodies; existence check on save and on view reports |
| timeline → tweet | `GET /internal/v1/tweets/by-author/{id}?since=&limit=` | back-fill on `user.followed` |

- **Gateway internal endpoints** need `X-Internal-Secret` (Q5). Missing or wrong → `404`, as if the path didn't exist.
  `INTERNAL_API_SECRET` has no default, must be ≥32 bytes, and both services refuse to start without it.
- **The tweet service's internal endpoints have no secret** (Q6): the gateway only forwards `/api/v1/tweets/**`, so they
  aren't public, and anything that reaches port 8081 can already do everything.
- **Base URLs:** `GATEWAY_INTERNAL_URL` (default `http://localhost:8080`), `TWEET_SERVICE_URL` (default
  `http://localhost:8081`). **Timeouts:** connect 2s, read 5s, under the gateway's 10s read timeout.
- **On a read (feed, saved list, view check): fail the request (Q15).** Unreachable or `5xx` → `502`, timeout →
  `504`. No partial pages, no `author: null`.
- **Trade-off on record (Q6):** reading the tweet service's Mongo directly would be a few ms per page faster. We pay
  that for decoupling.

### Kafka (Q15)

- **Consumes:** `tweet.created`, `tweet.deleted` (tweet service), `user.unfollowed` and `user.followed` (gateway).
  Group `twitter-timeline-service`, `auto-offset-reset=earliest`, concurrency 3.
- **As the mail service:** one typed consumer factory and one error handler per topic; an invalid event is
  dead-lettered without retries; everything else is retried with blocking exponential backoff (2s → 60s, 8 retries),
  then `DeadLetterPublishingRecoverer`. DLTs `<topic>-dlt`, 3 partitions, declared here.
- **Never log the payload** (tweet text). Log `eventId`, `tweetId`, user ids.

### Lists and cursors

- **Opaque keyset cursor** `(timestamp, tweetId)`, base64url, strict round-trip check (`TimelineCursorCodec`). Bad
  cursor → `400`. `size`: default 20, 1–100, otherwise `400`. Response `{ "items": [...], "nextCursor": "…" | null }`.
- **Reads `size + 1` rows**; `nextCursor` comes from the last *row read*, not the last item returned.
- **A short page is not the end.** Rows whose tweet or author no longer exists are skipped; only
  `nextCursor: null` means the end.

---

## Phase 1 — Feed ✅ **Done** (2026-10-03)

- **`FeedEntry` (`feed_entries`)**, PK `(user_id, tweet_created_at, tweet_id)` doubling as the page index; rows only
  inserted or deleted.
- **`tweet.created` fan-out (Q4, Q8):** validate, the author's own entry, then the gateway's follower pages (1,000
  each), one transaction per page; any failure retries from the top and the duplicates skip.
- **`tweet.deleted`:** delete by `tweet_id`; a delete before the create leaves orphans that reads skip and retention
  removes. **`user.unfollowed` (Q9):** delete the follower's entries by that author up to `occurredAt`.
- **`GET /api/v1/feed?cursor=&size=`:** the tweet call and the author call run side by side (Q14); missing tweets or
  authors are skipped; `502` / `504` on a downstream failure.
- **Retention (Q3):** a nightly job (`FEED_CLEANUP_CRON`, `FEED_CLEANUP_ZONE`) deletes in batches of
  `FEED_CLEANUP_BATCH_SIZE` entries older than `FEED_RETENTION` (7d).
- **[gateway]** `/internal/v1/**` behind `InternalApiSecretFilter`, the `user.unfollowed` event through the outbox, and
  the `/api/v1/feed` route. **[tweet]** `GET /internal/v1/tweets?ids=` and collections created at startup.

## Phase 2 — Saved tweets ✅ **Done** (2026-10-03)

- **`SavedTweet` (`saved_tweets`)**, PK `(user_id, tweet_id)` plus `author_id` and `saved_at`; no retention job: kept
  until unsaved or the tweet is deleted (Q10).
- **`PUT /api/v1/saved-tweets/{tweetId}` → 204** after an existence check at the tweet service (`404` if unknown);
  saving again keeps the original `saved_at`. **`DELETE` → 204** whether or not it was saved, no check.
- **`GET /api/v1/saved-tweets?cursor=&size=`:** newest saved first, same cursor and skip rules as the feed, through the
  shared `TweetItemAssemblyService`. `tweet.deleted` also removes the saved rows, in one transaction.
- **[gateway]** `/api/v1/saved-tweets/**` joins the feed on the one timeline route.

## Phase 3 — Views ✅ **Done** (2026-10-03)

- **`TweetView` (`tweet_views`)**, PK `(tweet_id, viewer_id)`, and **`TweetViewCount` (`tweet_view_counts`)**. Unique
  viewers; the author's own views count (Q11, Q12).
- **`POST /api/v1/views`** `{ "tweetIds": [...] }` → 204: 1–50 ids, one tweet-service call (unknown ids dropped), then
  the insert and the counter upsert in one transaction. **`GET /api/v1/views?tweetIds=a,b`** → `{ "<id>": <views> }`:
  1–100 ids, `0` for unknown, no downstream call (Q13). Feed and saved items carry `views` inline.
- **`tweet.deleted`** also removes the tweet's views and counter. **[tweet]** `views` and the `$inc` are gone.
  **[gateway]** `/api/v1/views` joins the timeline route.
- **Handoff to `frontend/PLAN.md`:** report a tweet once it has been at least half visible for about a second; batch the
  ids and `POST` every few seconds (≤50 per request) and on page hide.

---

## Left open

- **The opposite-order concurrency scenario does not prove the counter upsert's lock order:** removing the `ORDER BY`
  did not fail it in five variants (`DECISIONS.md`). **Decide:** a test that observes the lock order, or accept that
  the sort is a documented-hazard defence with no regression guard.
- **Back-fill findings still open** (`SECURITY-AUDITS.md`, all Low unless noted): a retry after an unfollow re-inserts
  rows and a dead-lettered event leaves them; no per-pair dampening of follow toggling (2 internal calls and 50 commits
  per event); `FEED_BACKFILL_SIZE` above 100 starts and then dead-letters every follow; events that can never succeed
  are retried ~4 minutes each and an oversized tweet list isn't capped; a far-past `occurredAt` skips the 7-day
  window; a forged self-follow event deletes the user's own tweets; a malformed event's raw text reaches the log;
  and the tests miss the real wrong-secret answer and most malformed events (Info). **Decide:** fix the cheap ones
  (startup bound on the back-fill size, self-follow and `occurredAt` validation, a list cap) or accept them with
  triggers.

## Test strategy

Unit (Mockito) → repository (real Postgres) → client (WireMock) → listener (real Kafka + Postgres, WireMock
downstreams) → HTTP (`*ControllerIntegrationTest`) → concurrency (latch-released threads, assert final state) → one
API-level Playwright spec per phase in `../e2e` over the real chain. No embedded fakes; Docker is required. The
deterministic-test and 3×-green rules are in `CLAUDE.md`.

## Out of scope

Liked tweets; the frontend feed, saved and views UI (handoff above); a tweet details page; "tweets by author" /
profile timelines; ranking or an algorithmic feed; replies, retweets, quotes; notifications; account deletion; a Redis
cache; a DLT replay tool.

## Accepted gaps — revisit when the named trigger lands

- **Tweets are fetched over HTTP on every read (Q6),** a few ms slower than reading Mongo directly. **Trigger:** the
  feed's p95 above about 200 ms → cache tweets by id (Redis, evicted on `tweet.deleted`, plus a `tweet.updated`
  event), or keep one copy per tweet in this DB.
- **A feed or saved read needs both downstreams (Q15);** one down → the whole page fails. **Trigger:** availability
  complaints → serve tweets without authors, or a cache.
- **Fan-out on write for big accounts.** A tweet from an account with N followers is N/1,000 gateway calls and N
  rows, holding a partition meanwhile. **Trigger:** fan-out lag, or an account past ~10k followers → fetch big
  accounts' tweets at read time (hybrid).
- **Unfollow race (Q9):** a tweet fanned out at the moment of an unfollow can still land; it drops off within 7 days.
  A follow, unfollow, follow sequence can also lose the second back-fill when the old unfollow's delete runs after it.
- **Fan-out uses followers at processing time.** A tweet whose event was delayed (retries, DLT) goes to whoever
  follows the author then.
- **Orphans from cross-topic order.** `tweet.deleted` before `tweet.created`, or a save / view racing a delete, leaves
  rows for a dead tweet. Feed orphans are hidden and expire in 7 days; saved and view orphans are hidden but never
  removed. **Trigger:** noticeable growth → a sweep job checking ids against the tweet service.
- **This service trusts `X-User-Id` blindly** (as the tweet service); since phase 3 that also lets anyone who reaches
  port 8083 mint viewers and inflate any tweet's count. **Trigger:** port 8083 reachable by anything but the gateway →
  the shared secret on this service too, or mTLS.
- **One static internal secret on the gateway's public port, no rotation.** **Trigger:** any deployment → a private
  port or mTLS for `/internal/**`, and rotation. The tweet service's internal endpoints have no secret either; same
  trigger as its `X-User-Id` gap.
- **No rate limiting** on feed reads, saves (each a tweet-service call and an INFO log line) or view reports (each a
  tweet-service call and up to 50 new rows). **Trigger:** abuse, or a second instance.
- **View rows kept forever** (one per viewer per tweet). **Trigger:** table size → HyperLogLog (e.g. Redis `PFADD`).
  **Hot counter row** on a viral tweet. **Trigger:** view-report latency → buffered or sharded counters. **No cap on
  saved tweets.** **Trigger:** abuse or table size.
- **The author's own views count (Q12).** **Trigger:** a complaint → pass the author per id and skip it.
- **Start order:** a listener started before the producer auto-creates its topic with 1 partition (as the mail
  service's open item).
- **DLT names are `<topic>-dlt`,** except `user.followed-timeline-dlt`: the mail service already owns
  `user.followed-dlt`. **Trigger:** a second service consumes another of these topics → a service-specific DLT name.
- **No DLT replay tool.** **Trigger:** the first dead-lettered record someone wants processed.
- **Scheduled retention runs on every instance.** **Trigger:** a second instance → ShedLock.
- **`ddl-auto=update`, no migrations.** **Trigger:** a second environment or the first destructive change → Flyway.
- **`tweet.created` takes any `createdAt`.** A far-future time pins the entry atop feeds and past retention; needs
  write access to the topic. **Trigger:** any producer besides the tweet service → reject a `createdAt` more than a
  few minutes ahead (to the DLT).
- **Rejected `/internal/**` calls log one WARN line each.** **Trigger:** log volume from probing → rate limit or
  sample the line.
- **A client's `X-Internal-Secret` is forwarded by the tweet and feed routes;** ignored downstream. **Trigger:** a
  downstream starts reading that header → strip it in `CallerIdentityFilters`.
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
| 17 | Cross-service test? | One API-level Playwright spec per phase in `../e2e` over the real chain |
| 18 | `CommonEntity` for the new tables? | No: the pair is the key |
