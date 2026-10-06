# Twitter Timeline Service — plan

Everything about one user's relationship to tweets they didn't write. Built in five ordered phases:

1. **Feed.** Tweets from people you follow (and your own), newest first, kept 7 days.
2. **Saved tweets.** Bookmark, unbookmark, list.
3. **Views.** Unique viewers per tweet, reported by the browser; the counter moves here out of the tweet service.
4. **Likes.** Like, unlike, your own liked list, a public count on every post; replaces the gateway's like stub.
5. **Likes UI.** The frontend shows real counts and hearts, gains `/liked`, and drops what was left of the stub.

Phases 1–3 were settled in the `/grill-me` interview of 2026-10-03 (Q1–Q18), phases 4–5 in the `/plan-backend`
interview of 2026-10-05 (Q19–Q26, spec `../docs/likes-design.md`), which reverses Q1's "liked tweets later,
elsewhere". Records at the bottom, Q-numbers in parentheses.

Building it touched two other projects; their steps lived in this plan, marked **[gateway]** or **[tweet]**, and their
own `PLAN.md` files only point here. Where things live now: calls made while building in `DECISIONS.md`, the test
catalog in `TESTING.md`, conventions and standing rules in `CLAUDE.md`, the security audits in `SECURITY-AUDITS.md`.

## Status

| Phase | Scope | Done |
|---|---|---|
| 1 | Feed (steps 1–13) | 2026-10-03 |
| 2 | Saved tweets (steps 14–19) | 2026-10-03 |
| 3 | Views (steps 20–27) | 2026-10-03 |
| 4 | Likes (steps 28–34) | 2026-10-06 |
| 5 | Likes UI (steps 35–42) | 2026-10-06 |

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

## Phase 4 — Likes ✅ **Done** (2026-10-06)

Spec: `../docs/likes-design.md`. A copy of saved tweets plus a public counter (a copy of the views counter); writes are
synchronous HTTP, no Kafka, no new event, no new env var.

- **`TweetLike` (`tweet_likes`)**, PK `(user_id, tweet_id)` plus `author_id` and `liked_at`, and **`TweetLikeCount`
  (`tweet_like_counts`)** with `ck_tweet_like_counts_likes_non_negative`; every column `updatable = false`, only native
  statements move the counter. Indexes `ix_tweet_likes_tweet` and `ix_tweet_likes_user_liked`.
- **Invariant:** `likes` = the tweet's `tweet_likes` rows; the counter moves only in the transaction of a row actually
  added or removed (`LikeRecordingService.like` / `unlike`, its own bean, as views).
- **`PUT /api/v1/likes/{tweetId}` → 204**, idempotent: one tweet-service read outside any transaction (`404` unknown,
  `502` / `504` on a downstream failure, nothing written), then insert plus upsert. Liking again keeps `liked_at`.
  Your own post can be liked and counts; a new post starts at `0` (Q21).
- **`DELETE /api/v1/likes/{tweetId}` → 204**, idempotent, no downstream call.
- **`GET /api/v1/likes?cursor=&size=`** → `LikedTweetsResponseDTO`: your own likes only (Q20), newest like first, same
  cursor, size and skip rules as the saved list, through `TweetItemAssemblyService`.
- **`likes` and `likedByMe` inline** on every feed, saved and liked item (`TweetItemResponseDTO`, after `savedByMe`): one
  count query and one `findLikedTweetIds` per page, no new HTTP call.
- **`tweet.deleted`** also removes the tweet's likes and counter in its existing transaction.
- **[gateway]** `/api/v1/likes/**` joins the timeline route; the like stub (`LikeController`, its test) is gone (Q26).
- **Tests:** repository, unit, HTTP and `tweet.deleted` suites (catalog in `TESTING.md`), `LikeConcurrencyIntegrationTest`
  (50 likers, one user 50×, like/unlike churn, 50 parallel unlikes), gateway `TimelineRoutesIntegrationTest.Likes`,
  e2e `likes.spec.ts` (5 journeys, Q25).
- **Audit (2026-10-06, `SECURITY-AUDITS.md`):** nothing Medium or above; 1 Low and 2 Info accepted with triggers
  (accepted gaps below), 1 Info unconfirmed (Left open).
- **Final gate:** timeline and gateway `./mvnw verify` green 3× in a row, e2e `npm test` green 3× from a fresh stack.

---

## Phase 5 — Likes UI ✅ **Done** (2026-10-06)

Connected the like stub to phase 4 and removed what it left behind (Q26). Tests: the Vitest and Playwright names are the
catalog (no frontend `TESTING.md`).

- **Data:** `TweetItem` gains `likes` and `likedByMe`; `fetchLikedTweets` and `ENDPOINTS.likes` in `src/api/likes.ts`;
  `toOwnPost` starts at `likes: 0, likedByMe: false` (Q21).
- **One formatter (Q23):** `formatFollowers` became `formatCount` (`utils/count.ts`), same rule; `PersonCard` keeps its
  word.
- **Real like state:** `PostCell` passes `isLikedInitially` and `likeCount`; `PostActions` shows
  `formatCount(max(0, likes − (liked on load) + (liked now)))`, never `-1`; a failed or `404` request reverts silently.
  The zero-start tests were replaced.
- **`/liked` (Q20, Q22, Q24):** `ROUTES.liked`, `LikedPage` (`LIKED TWEETS`, `END OF LIKED TWEETS`, `NO LIKED TWEETS
  YET`), a `Shell` child with no tab row; menu `SAVED TWEETS`, `LIKED TWEETS`, `LOG OUT`. An unliked post stays, heart
  empty, until a reload; no `NEW POSTS` check.
- **Stub gone (Q26):** frontend `CLAUDE.md`, `PLAN.md` and `DECISIONS.md` updated; the root `PLAN.md` timeline line names
  the likes and its old scratch note is marked superseded.
- **e2e (Q25):** four UI journeys in `feed-ui.spec.ts` (reload keeps the like, another user's like shows `1` then `2`,
  the menu opens `/liked`, an unliked post stays until reload).
- **Audit (2026-10-06):** no new findings; the earlier Lows and Info stay open in `frontend/SECURITY-FINDINGS.md`.
- **Final gate:** build, lint and 880 Vitest tests clean; e2e 49/49 green 3× from a fresh stack. Visual check waived
  (Left open).

---

## Left open

- **Phase 5 visual check waived (step 42):** `/liked` and a post with `1.2K` likes were not looked at in a browser at
  1440 and 320 px (the action row could wrap). **Decide:** look once, or accept.
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

- **Unconfirmed log forging (Info, audit 2026-10-06):** `GlobalExceptionHandler` logs `e.getResourcePath()` unescaped at
  WARN; a decoded line feed in the path could forge a log line, only on direct access to :8083. Not run against a live
  instance. **Decide:** `curl 'localhost:8083/%0aWARN%20forged'` once the stack is up, then escape the path or drop
  the item.

## Test strategy

Unit (Mockito) → repository (real Postgres) → client (WireMock) → listener (real Kafka + Postgres, WireMock
downstreams) → HTTP (`*ControllerIntegrationTest`) → concurrency (latch-released threads, assert final state) → one
API-level Playwright spec per phase in `../e2e` over the real chain. No embedded fakes; Docker is required. The
deterministic-test and 3×-green rules are in `CLAUDE.md`.

Phase 5 runs the frontend's layers instead: unit and component tests in Vitest (jsdom, fake timers advanced in stages,
`src/api/*` stubbed with `vi.mock`, a stubbed `fetch` for the API modules), then Playwright UI journeys in `../e2e`
(`reducedMotion: 'reduce'`, fresh users, `expect.poll` for fan-out, never a fixed wait). The approved catalog comes
first in both phases; one flaky run is a failure to investigate, not to retry.

## Out of scope

The frontend feed, saved and views UI (handoff above); a tweet details page; "tweets by author" / profile timelines;
ranking or an algorithmic feed; replies, retweets, quotes; notifications; account deletion; a Redis cache; a DLT replay
tool. From phases 4–5: other people's liked lists (Q20); a "who liked this" list; like notifications; likes feeding any
ranking.

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

From phases 4–5 (likes):

- **No `tweet.liked` event.** Nothing would consume it. **Trigger:** a consumer, e.g. a "X liked your post"
  notification → an outbox in this service (a copy of the tweet service's) and the event.
- **Only your own liked list (Q20).** **Trigger:** profile pages → `GET /api/v1/users/{id}/likes`, after deciding
  whether likes are public.
- **No "who liked this" list.** **Trigger:** a likers page → index `(tweet_id, liked_at, user_id)` and an endpoint.
- **Self-likes count (Q21).** **Trigger:** a complaint about inflated counts → skip the author's like in the counter.
- **Like orphans:** a like racing a tweet delete leaves a row and a counter for a dead tweet, hidden in lists, never
  removed. Same trigger as the saved and view orphans above.
- **No rate limiting on likes** (each one a tweet-service call and an INFO line), and **no cap on likes per user**.
  **Trigger:** abuse, a second instance, or table size.
- **Hot like counter** on a viral tweet. **Trigger:** like latency → buffered or sharded counters.
- **`tweet.deleted` removes every like of the tweet in one transaction** (as its saved rows and views), so a tweet with
  N likes is one N-row delete holding the listener. **Trigger:** a slow or timed-out `tweet.deleted` → delete in
  batches, each its own transaction.
- **`X-User-Id` trusted blindly** lets anyone who reaches port 8083 mint likes and read any user's private liked list
  by naming their id (audit 2026-10-06, Low). Same trigger as that gap above: anything but the gateway able to reach
  :8083 → Medium; the shared secret or mTLS then.
- **A forged `tweet.deleted` also wipes the tweet's likes and counter,** which have no source to replay from (audit
  2026-10-06, Info). Needs write access to the topic. **Trigger:** a second producer or a non-loopback broker → Kafka
  ACLs, or the listener confirms the delete with the tweet service before acting.
- **Counts on screen are a snapshot** plus your own change; other people's likes on posts already shown appear after a
  reload (a `NEW POSTS` load adds only new posts). **Trigger:** complaints that counts look stale → re-read counts for
  visible posts.
- **Two reads per page are not one snapshot:** the count and `likedByMe` come from separate queries, so a like or
  unlike landing between them can show `likes: 0, likedByMe: true` once; the UI never shows below `0`. **Permanent**
  unless counts must be exact on screen.

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

## Interview record (`/plan-backend`, 2026-10-05)

Likes, from `../docs/likes-design.md`. Numbers continue from the `/grill-me` record above.

| # | Question | Answer |
|---|---|---|
| 19 | Where do the likes steps go? | One plan: phase 4 (likes) and phase 5 (likes UI) here; the frontend and gateway plans get a pointer |
| 20 | Whose liked tweets can you see? | Only your own (`GET /api/v1/likes`); anyone's is a gap until profile pages |
| 21 | Can you like your own post? | Yes, and it counts, but only by tapping the heart: posting never likes it, a new post starts at `0` |
| 22 | Unlike a post on `/liked`? | It stays, heart empty, count down; gone after a reload or leaving the page, as `/saved` (frontend Q5) |
| 23 | Big like counts? | Shortened as follower counts (rounded down, K / M, one decimal below 10); one shared formatter |
| 24 | Liked page texts and address | As proposed: menu `SAVED TWEETS`, `LIKED TWEETS`, `LOG OUT`; `/liked`; title `LIKED TWEETS`; `END OF LIKED TWEETS`; `NO LIKED TWEETS YET`; no tab row |
| 25 | E2E journeys | As proposed: 5 API journeys in `likes.spec.ts` (incl. a forged `X-User-Id`), 4 UI journeys in `feed-ui.spec.ts` |
| 26 | Phase cut; what "the mockup" is | Phase 4 likes (timeline, gateway route replaces the stub, API e2e), phase 5 likes UI (stub leftovers in the UI replaced, `/liked`, UI e2e); the mockup is the like stub, gone everywhere by the end of phase 5 |
| — | `(my call)` | The like transaction in its own `LikeRecordingService` (as views); the assembly service reads the like repositories itself (a cycle otherwise); `likes`, `likedByMe` after `savedByMe`; path ids parsed leniently, as saved (upper-case = same id); a failed or `404` like reverts silently, as save; the shown count never below `0`; catalogs as `@Disabled`, `it.todo` and `test.fixme`; the stub's tests deleted in step 32, its UI tests replaced in step 38; a manual visual check of `/liked` and a `1.2K` count |
