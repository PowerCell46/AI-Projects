# Twitter Timeline Service — plan

Everything about one user's relationship to tweets they didn't write: the feed, saved tweets, unique viewers per tweet
and likes. Designed in two interviews (`/grill-me` 2026-10-03, `/plan-backend` 2026-10-05, spec `../docs/likes-design.md`).

**Status: all five phases are built.** The full original plan (step gates, phase sections, test lists, interview record)
was trimmed on 2026-10-09; it lives in git history (`git log -- PLAN.md`). Scenarios are in `docs/TESTING.md`, calls made
while building in `DECISIONS.md`, audits in `docs/SECURITY-AUDITS.md`.

| Phase | Scope | Steps | Done |
|---|---|---|---|
| 1 | Feed: fan-out on `tweet.created`, 7-day retention, `user.unfollowed`; gateway internal endpoints, tweet batch read | 1–13 | 2026-10-03 |
| 2 | Saved tweets | 14–19 | 2026-10-03 |
| 3 | Views: unique viewers per tweet; the counter moved here from the tweet service | 20–27 | 2026-10-03 |
| 4 | Likes: like, unlike, your own liked list, a public count on every post; replaced the gateway's like stub | 28–34 | 2026-10-06 |
| 5 | Likes UI: real counts and hearts, `/liked`, the stub's leftovers removed | 35–42 | 2026-10-06 |

Added later by other plans (see `DECISIONS.md`): feed back-fill on follow (`user.followed`), one author's tweets, and the
tweet details read with `replyCount` (`../twitter_tweet_service/PLAN.md`, phase 2).

---

## Design in short

- **Stack:** Java 25, Spring Boot 4.1.1, port **8083**, no Spring Security. Ported, not reinvented: error shape, body-size
  filter, `@CurrentUserId`, `Clock` (tweet service); `FollowCursorCodec` (gateway); Kafka consumer factory, error handler,
  backoff and DLT recoverer (mail service).
- **Identity:** `X-User-Id`, set by the gateway, trusted blindly (accepted gap below). The shared secret guards only the
  *gateway's* `/internal/v1/**` (`X-Internal-Secret`, `404` when missing or wrong, ≥32 bytes, no default); the tweet
  service's internal endpoints have none.
- **Data:** own Postgres `postgres-timeline` (`127.0.0.1:5433`), env names prefixed `TIMELINE_DATASOURCE_*` because the
  root `.env` is shared. `ddl-auto=update`, no Flyway. Rows are only inserted or deleted (native `INSERT … ON CONFLICT DO
  NOTHING`), so a redelivered event or a double click changes nothing and there is no `CommonEntity` or Redis inbox. A
  counter moves only in the transaction of a row actually added or removed.
- **Behaviour:**
  - `tweet.created` → the author's own entry, then the gateway's follower pages (1,000 each), one transaction per page,
    retried from the top. `tweet.deleted` removes the tweet's feed, saved, view and like rows and counters in one
    transaction. `user.unfollowed` deletes the follower's entries by that author up to `occurredAt`. Nightly retention, 7 days.
  - Save and like `PUT` are idempotent, keep the original timestamp and check the tweet exists; `DELETE` is idempotent with
    no check. Your own post's like counts; the liked list is your own only. Items carry `views`, `likes`, `likedByMe`.
  - Lists: opaque keyset cursor, `size` 1–100 (default 20), `size + 1` rows read; rows whose tweet or author is gone are
    skipped, so a short page is not the end, only `nextCursor: null` is.
- **Downstreams:** gateway (follower ids, authors, follow check), tweet service (tweets by ids, tweets by author). A read
  fails when one fails: `502` unreachable or `5xx`, `504` timeout, no partial pages. Timeouts 1s connect, 3s read, so the
  details read's two sequential calls stay under the gateway's 10s.
- **Kafka:** consumes `tweet.created`, `tweet.deleted`, `user.unfollowed`, `user.followed`. An invalid event is
  dead-lettered without retries, the rest retried with backoff (2s → 60s, 8 retries), then `<topic>-dlt`. Never log a payload.

---

## Left open

- **Phase 5 visual check waived:** `/liked` and a post with `1.2K` likes were not looked at in a browser at 1440 and
  320 px (the action row could wrap). **Decide:** look once, or accept.
- **The opposite-order concurrency scenario does not prove the counter upsert's lock order:** removing the `ORDER BY`
  did not fail it in five variants (`DECISIONS.md`). **Decide:** a test that observes the lock order, or accept the sort
  as a defence with no regression guard.
- **Back-fill findings still open** (`docs/SECURITY-AUDITS.md`, all Low unless noted): a retry after an unfollow
  re-inserts rows and a dead-lettered event leaves them; no dampening of follow toggling; a null tweet-service body,
  item or field is retried ~4 minutes each; a malformed event's raw text reaches the log through the Kafka error
  handler; tests miss non-UUID ids, non-JSON and tombstones (Info). **Decide:** fix the cheap ones (null answers as an
  invalid answer, escape or drop the raw text) or accept them with triggers. Fixed 2026-10-09: self-follow, far-past
  `occurredAt`, `FEED_BACKFILL_SIZE` above 100, the uncapped tweet list, and the logged request path.

## Out of scope

Ranking or an algorithmic feed; retweets, quotes; notifications; account deletion; a Redis cache; a DLT replay tool;
other people's liked lists; a "who liked this" list.

## Accepted gaps — revisit when the named trigger lands

- **Tweets are fetched over HTTP on every read, and a read needs both downstreams.** **Trigger:** feed p95 above ~200 ms
  or availability complaints → cache tweets by id (Redis, evicted on `tweet.deleted`, plus a `tweet.updated` event), or
  serve tweets without authors.
- **Fan-out on write for big accounts,** at processing time: N followers = N/1,000 gateway calls and N rows, holding a
  partition; a delayed event goes to whoever follows then. **Trigger:** fan-out lag, or an account past ~10k followers →
  fetch big accounts' tweets at read time.
- **Unfollow race:** a tweet fanned out during an unfollow can still land (gone within 7 days); follow, unfollow, follow
  can lose the second back-fill.
- **Orphans from cross-topic order:** `tweet.deleted` before `tweet.created`, or a save / view / like racing a delete,
  leaves rows and counters for a dead tweet. Feed orphans expire in 7 days; the rest are hidden, never removed.
  **Trigger:** noticeable growth → a sweep job checking ids against the tweet service.
- **`X-User-Id` trusted blindly:** anyone who reaches port 8083 can mint views and likes, inflate counts and read any
  user's private liked list (audit 2026-10-06, Low). **Trigger:** anything but the gateway able to reach :8083 → Medium;
  the shared secret on this service too, or mTLS.
- **One static internal secret on the gateway's public port, no rotation.** **Trigger:** any deployment → a private port
  or mTLS for `/internal/**`, and rotation.
- **No rate limiting** on feed reads, the details read, saves, likes (each a tweet-service call and an INFO line) or view
  reports (up to 50 new rows). **Trigger:** abuse, or a second instance.
- **Growth:** view rows kept forever; saved tweets and likes uncapped per user; a hot counter row on a viral tweet;
  `tweet.deleted` deletes a tweet's likes, saved rows and views in one transaction. **Trigger:** table size, latency or a
  slow `tweet.deleted` → HyperLogLog for views, caps, sharded counters, batched deletes.
- **The author's own views and likes count.** **Trigger:** a complaint → skip the author.
- **No `tweet.liked` event, no "who liked this" list, only your own liked list.** **Trigger:** a notification consumer →
  an outbox here; a likers page → index `(tweet_id, liked_at, user_id)`; profile pages → `GET /api/v1/users/{id}/likes`
  after deciding whether likes are public.
- **A forged `tweet.deleted` wipes the tweet's likes and counter** (audit 2026-10-06, Info); **`tweet.created` takes any
  `createdAt`,** so a far-future time pins the entry atop feeds. Both need write access to the topic. **Trigger:** a
  second producer or a non-loopback broker → Kafka ACLs, confirm deletes with the tweet service, reject a `createdAt`
  more than a few minutes ahead.
- **The details read's `404` body tells a gone tweet from a disabled author** (audit 2026-10-07, Info). **Trigger:**
  private tweets or accounts → one `404` body for both.
- **Counts on screen are a snapshot,** and the count and `likedByMe` come from two queries (a like between them can show
  `likes: 0, likedByMe: true` once; the UI never shows below `0`). **Trigger:** stale-count complaints → re-read counts
  for visible posts. The two-query part is **permanent**.
- **Start order:** a listener started before the producer auto-creates its topic with 1 partition.
- **DLT names are `<topic>-dlt`,** except `user.followed-timeline-dlt` (the mail service owns `user.followed-dlt`); **no
  DLT replay tool.** **Trigger:** a second consumer of another of these topics → a service-specific name; the first
  dead-lettered record someone wants processed → a replay tool.
- **Scheduled retention runs on every instance.** **Trigger:** a second instance → ShedLock.
- **`ddl-auto=update`, no migrations.** **Trigger:** a second environment or the first destructive change → Flyway.
- **Rejected `/internal/**` calls log one WARN line each.** **Trigger:** log volume from probing → rate limit or sample.
- **A client's `X-Internal-Secret` is forwarded by the tweet and feed routes;** ignored downstream. **Trigger:** a
  downstream starts reading it → strip it in `CallerIdentityFilters`.
- **Dev compose has fixed credentials** (bound to `127.0.0.1`). **Trigger:** any shared environment.
- **Stale `views` field in existing dev tweet documents.** **Permanent** unless someone wants it gone.
