# Security audits

Last updated: 2026-10-09

The `exploit-hunter` audits of the timeline service, one section per audit, condensed from the original per-audit
reports (dates are the audit dates; the tweet details report was merged in on 2026-10-09). Each "accepted" or "open"
item is listed in `PLAN.md` under its accepted gaps or left-open section, with a trigger. Dependencies were not scanned
for CVEs in any audit.

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-10-03 | Phase 1: feed read, listeners, retention, gateway `/internal/v1/**`, the feed route | 1 Low fixed, 3 Low and 1 Info accepted |
| 2026-10-03 | Phase 2: saved tweets | 1 Low and 1 Info accepted |
| 2026-10-03 | Phase 3: views | 2 Low and 1 Info accepted, 1 Info open |
| 2026-10-04 | Frontend phase 3: back-fill on follow | 1 Medium and 3 Low fixed, 4 Low and 1 Info open |
| 2026-10-06 | Phase 4: likes | 1 Low and 2 Info accepted, 1 Info fixed |
| 2026-10-07 | The tweet details read (tweet service phase 2, step 22) | 1 Low and 1 Info fixed, 1 Low and 1 Info accepted |

## Phase 1: feed (2026-10-03)

- **`/actuator/health` showed details on the service port (Low), fixed.** `show-details=always` returned the database
  product, disk path and free bytes; the gateway and tweet service return only `status` and `groups`. The line is
  removed, so the default `never` applies.
- **`tweet.created` accepts any `createdAt` (Low), accepted.** The event time is the sort key and the retention clock,
  so a far-future value pins the entry atop every follower's feed past retention. Needs write access to the topic.
  Trigger: any producer besides the tweet service → reject a `createdAt` more than a few minutes ahead (to the DLT).
- **No rate limit on feed reads (Low), accepted.** Each `size=100` read costs a Postgres query here, a Mongo `$in` at
  the tweet service and a Postgres `IN` at the gateway. Same posture as every authenticated route.
- **Rejected `/internal/v1/**` calls log one WARN each (Low), accepted.** Cheap log flooding by an unauthenticated
  caller; nothing sensitive is logged. Trigger: log volume from probing → rate limit or sample the line.
- **A client's `X-Internal-Secret` is forwarded by the tweet and feed routes (Info), accepted.**
  `CallerIdentityFilters` strips only `Cookie`, `Authorization` and `X-User-*`; downstreams ignore the header today.
  Trigger: a downstream starts reading it → strip it there.
- **Clean:** a missing or wrong secret is a `404` like an unknown path; path-traversal variants are 401 or 400 and never
  forwarded; a spoofed `X-User-Id` is replaced by the JWT subject; bad `size` or cursor is a 400 with fixed messages;
  every SQL statement binds its parameters; downstream URLs come from configuration; the secret goes only to the
  gateway client, is compared in constant time and has no default.

## Phase 2: saved tweets (2026-10-03)

- **Every save and unsave costs a tweet-service call and an INFO log line (Low), accepted.** The row is idempotent, so
  the table doesn't grow; the cost is downstream load and log volume. Covered by the "no rate limiting" gap.
- **A save racing a tweet's delete can leave a permanent row (Info), accepted.** The existence check passes, the
  delete and `tweet.deleted` are processed, then the insert lands. Reads skip it and nothing removes it. Covered by
  "orphans from cross-topic order".
- **Clean:** a non-UUID id is a 400 with no type name; `1-1-1-1-1` parses and is a 404; every query is scoped by
  `X-User-Id` and `unsave` deletes only the caller's row; logs carry UUIDs only; downstream bodies are never returned;
  a 20 KB body is a 413.

## Phase 3: views (2026-10-03)

- **Anyone who can reach port 8083 can mint unlimited viewers and inflate any tweet's count (Low), accepted.** The
  viewer comes from `X-User-Id` and is never checked against a user, so a fresh UUID per request is a new
  `(tweet, viewer)` pair; 200 requests moved a count from 2 to 202. Needs the already accepted exposure (port 8083
  not published in dev or e2e). Would be Medium the day anything but the gateway can reach the port.
- **A report costs a tweet-service call and writes rows, with no rate limit (Low), accepted.** Up to 50 ids per `POST`,
  one Mongo `$in`, up to 50 inserts; 300 reports with 50 random ids took 4.1 s and left the tables at one row each.
  Covered by "no rate limiting" and "growth".
- **A report racing a tweet's delete can leave rows and a counter for a dead tweet (Info), accepted.** `GET /views`
  would report the stale count. Covered by "orphans from cross-topic order".
- **The opposite-order concurrency test does not prove the counter upsert's lock order (Info), open.** Removing the
  `ORDER BY` did not fail it in five variants, so nothing would catch someone deleting it; a deadlock would answer
  500 to one reporter, with no wrong data (`DECISIONS.md`).
- **Clean:** no cookie is 401; a 9 KB body is 413; 51 ids is a 400; malformed ids and bodies are fixed 400s;
  `text/plain` and form bodies are 415; an extra `viewerId` field is ignored; `PUT`/`DELETE` are 405; no CORS headers
  for another origin; every statement binds its parameters; the counter moves only for ids the insert returned, so 50
  parallel viewers give exactly 50.

## Back-fill on follow (2026-10-04)

Scope: `UserFollowedListener`, `FeedBackfillServiceImpl`, `FollowLookupServiceImpl`,
`TweetLookupServiceImpl.findNewestByAuthor`, the consumer factory, error handler and the `user.followed-timeline-dlt`
DLT. Read from source plus a throwaway harness (no Kafka or Docker); the Testcontainers suites were not run.

- **A wrong secret, a missing endpoint or a wrong gateway URL read as "unfollowed" and the back-fill was deleted
  (Medium), fixed 2026-10-04.** The gateway answers a missing or wrong secret with the same `404` as an unknown path,
  which `FollowLookupServiceImpl` read as `false`, so the 50 rows just inserted were deleted: no retry, no DLT, one
  INFO line. The gateway now answers `200 {"following": true|false}` and only that is an answer (`DECISIONS.md`).
- **`FEED_BACKFILL_SIZE` above 100 started fine and then dead-lettered every follow (Low), fixed 2026-10-09.** The
  tweet service answers 400 above 100, which became a retryable "unavailable". Now the app refuses to start above 100,
  a constant as in the tweet service.
- **`occurredAt` was trusted: a far-past value skipped the 7-day window, `Instant.MIN` threw (Low), fixed 2026-10-09.**
  The window now starts at the clock's now minus the retention and the event time is not read (`DECISIONS.md`).
- **A forged self-follow event deleted the user's own tweets from their own feed (Low), fixed 2026-10-09.** A
  self-follow is now an invalid event: the DLT, no retry, no downstream call.
- **Events and answers that can never succeed are retried for about four minutes each (Low), partly fixed
  2026-10-09.** `Instant.MIN` is gone and a longer tweet list is cut to the back-fill size (a hostile list of 5000 made
  5000 inserts). Still open: a `200` with a null body, a null item, a null `createdAt` or `id`, and a null event throw
  something other than `InvalidEventException`. Fix: treat null bodies and items as an invalid answer.
- **A retry after an unfollow re-inserts the rows, and a dead-lettered event leaves them (Low), open.** Each attempt
  inserts before it checks; if the check fails for the whole ~242 s retry window while the public unfollow works, the
  unfollow's delete is undone by the next attempt. At most 50 tweets of an unfollowed author, until retention. Fix:
  look the follow up first (which reopens the race the current order closes), or run the undo from the DLT recoverer.
- **A follow toggled in a loop costs two internal calls and 50 commits each time (Low), open.** The gateway emits an
  event per real follow and nothing dampens a pair; one hot followee keeps its partition busy; `max.poll.records` is
  500, so a slow tweet service can push a backlog past `max.poll.interval.ms`. Fix: one multi-row insert, skip the
  inserts when the check fails first, a per-pair dampener, a lower `max.poll.records`.
- **A malformed event's raw text reaches the log through the Kafka error handler (Low), open.** Jackson's
  `InvalidFormatException` repeats the offending string unescaped, newlines included, in the exception cause (and a
  13.5 KB header on the dead letter). A forged log line needs write access to the topic; the three older listeners are
  the same. Fix: escape line breaks, or log only the exception class for deserialization failures.
- **The tests miss the real wrong-secret answer and some malformed events (Info), open.** Since 2026-10-09 the
  self-follow, `Instant.MIN` and the over-long list are covered; nothing sends a non-UUID id, non-JSON or a tombstone,
  or checks that a retry after an unfollow ends with no rows.
- **Clean:** `createdAt` is cut to microseconds like the fan-out, so one tweet never becomes two rows; all SQL is
  bound; the secret reaches only the gateway client and no body, email or secret is logged; the service is not
  `@Transactional`, so no connection is held across the two HTTP calls; the order of check, unfollow and delete is
  safe except follow, unfollow, follow across the two topics (accepted); deleted tweets are not returned.

## Phase 4: likes (2026-10-06)

Read from source only; nothing was executed. Scope: the three like endpoints, the inline `likes` / `likedByMe`
fields, the like deletes in `tweet.deleted`, the gateway's `/api/v1/likes/**` route.

- **Likes widen the unauthenticated-header gap (Low), accepted.** The caller is whatever `X-User-Id` says, so anyone who
  reaches :8083 can mint likes (a fresh UUID is a new pair) and read any user's private liked list by naming them.
  Through the gateway the header is the JWT `sub` and the compose file publishes no port. Trigger: anything but the
  gateway able to reach :8083 → Medium; a shared secret or mTLS between the gateway and this service.
- **A forged `tweet.deleted` also wipes the tweet's likes and counter (Info), accepted.** Likes have no source to
  replay from. Needs write access to the topic (PLAINTEXT, no ACLs, bound to loopback). Trigger: a second producer or a
  non-loopback broker → Kafka ACLs, or confirm the delete with the tweet service first.
- **A like racing a tweet delete leaves a row and a counter (Info), accepted.** Hidden in lists, never removed; joins the
  saved and view orphans.
- **`GlobalExceptionHandler` logged the resource path unescaped at WARN (Info), fixed 2026-10-09.** A decoded line feed
  could forge a log line, only on direct access to :8083. The path is no longer logged, only the method.
- **Clean:** every statement binds its parameters; downstream URLs come from configuration; like, unlike and list are
  scoped to the caller and no endpoint lists who liked a tweet; `size` 1–100, strict cursors, 8 KB bodies; error bodies
  are fixed strings; the row insert or delete decides whether the counter moves, so parallel likes or unlikes by one
  user count once; the `PUT` / `DELETE` routes are not a CSRF path (a cross-origin `fetch` needs a preflight the
  gateway does not grant).

## Tweet details read (2026-10-07)

Read from source only; nothing was executed. Scope: `GET /api/v1/tweet-details/{tweetId}`, its two lookups, the
mapper and the gateway route.

- **Two sequential downstream calls could outlive the gateway's 10 s read timeout (Low), fixed 2026-10-07.** Each call
  had 2 s connect and 5 s read, so ~14 s in the worst case; the gateway answered `504` while this service kept working.
  The defaults are now 1 s and 3 s, so the pair stays under 8 s (`DECISIONS.md`).
- **A malformed tweet-service answer ended as a `500` with a stack trace in the log (Info), fixed 2026-10-07.** A tweet
  without `id`, `authorId` or `images`, a null item or a null body is now a `502`; a test covers each.
- **No rate limit on the single-tweet read (Low), accepted.** One Mongo read at the tweet service, one Postgres `IN` at
  the gateway and four queries here per call, nothing batched. Same posture as every authenticated read (`PLAN.md`,
  "No rate limiting").
- **The `404` body tells "tweet gone" from "author disabled" (Info), accepted.** It only confirms a random-UUID id the
  caller already has, and any user can read the tweet through the tweet service anyway.
- **Clean:** `tweetId` is a bound `UUID` (non-UUID is a fixed 400); JPQL and `findAllById` bind their parameters;
  downstream URLs come from configuration, so no SSRF or open redirect; any authenticated user may read any tweet, as
  through the tweet service, and `savedByMe` / `likedByMe` come from the header the gateway sets from the JWT; error
  bodies are fixed strings and the response carries no email; the service sets no cache entries and the response holds
  per-viewer flags; the endpoint returns JSON, sets no cookies and no raw-HTML sink in `frontend/src` consumes it; the
  route is behind `authenticated()` and wrong methods are `405`.
