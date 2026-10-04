# Security audits

The `exploit-hunter` audits of the timeline service, one section per audit, condensed from the original per-audit
reports (dates are the audit dates). Each "accepted" or "open" item is listed in `PLAN.md` under its accepted gaps or
left-open section, with a trigger. Dependencies were not scanned for CVEs in any audit.

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-10-03 | Phase 1: feed read, listeners, retention, gateway `/internal/v1/**`, the feed route | 1 Low fixed, 3 Low and 1 Info accepted |
| 2026-10-03 | Phase 2: saved tweets | 1 Low and 1 Info accepted |
| 2026-10-03 | Phase 3: views | 2 Low and 2 Info accepted |
| 2026-10-04 | Frontend phase 3: back-fill on follow | 1 Medium fixed, 7 Low and 1 Info open |

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
- **Clean:** a missing or wrong secret is a `404` like an unknown path; path-traversal variants out of
  `/api/v1/tweets/**` and `/api/v1/feed` are 401 or 400 and never forwarded; spoofed `X-User-Id` is replaced by the
  JWT subject; bad `size` or cursor is a 400 with fixed messages; every SQL statement binds its parameters;
  downstream URLs come from configuration with UUID path variables; the secret goes only to the gateway client and is
  compared in constant time, with no default.

## Phase 2: saved tweets (2026-10-03)

- **Every save and unsave costs a tweet-service call and an INFO log line (Low), accepted.** The row is idempotent, so
  the table doesn't grow; the cost is downstream load and log volume. Covered by the "no rate limiting" gap.
- **A save racing a tweet's delete can leave a permanent row (Info), accepted.** The existence check passes, the
  delete and `tweet.deleted` are processed, then the insert lands. Reads skip it and nothing removes it. Covered by
  "orphans from cross-topic order".
- **Clean:** a non-UUID id is a 400 with no type name; `1-1-1-1-1` parses and is a 404 "Tweet not found."; traversal
  variants are 401 or 400; every query is scoped by `X-User-Id` and `unsave` deletes only the caller's row; logs carry
  UUIDs only; downstream bodies are never returned; a 20 KB body is a 413.

## Phase 3: views (2026-10-03)

- **Anyone who can reach port 8083 can mint unlimited viewers and inflate any tweet's count (Low), accepted.** The
  viewer comes from `X-User-Id` and is never checked against a user, so a fresh UUID per request is a new
  `(tweet, viewer)` pair; 200 requests moved a count from 2 to 202. Needs the already accepted exposure (port 8083
  not published in dev or e2e), but before phase 3 the header could only act as an existing user. Would be Medium the
  day anything but the gateway can reach the port.
- **A report costs a tweet-service call and writes rows, with no rate limit (Low), accepted.** Up to 50 ids per `POST`,
  one Mongo `$in`, up to 50 inserts; 300 reports with 50 random ids took 4.1 s and left the tables at one row each.
  Covered by "no rate limiting", "view rows kept forever" and "no cap".
- **A report racing a tweet's delete can leave rows and a counter for a dead tweet (Info), accepted.** `GET /views`
  would report the stale count; one live attempt left no rows. Covered by "orphans from cross-topic order".
- **The opposite-order concurrency test does not prove the counter upsert's lock order (Info), open.** Removing the
  `ORDER BY` did not fail it in five variants, so nothing would catch someone deleting it; a deadlock would answer
  500 to one reporter, with no wrong data (`DECISIONS.md`).
- **Clean:** no cookie is 401; a 9 KB body is 413; 51 ids (or 51 copies of one) is a 400; malformed ids and bodies are
  fixed 400s; `text/plain` and form bodies are 415 (no cross-site form can send `application/json`); an extra
  `viewerId` field is ignored (the stored viewer is the JWT `sub`); `PUT`/`DELETE` are 405; a preflight from another
  origin gets no CORS headers; every statement binds `uuid[]`/`uuid` parameters; counts are public data; the counter
  moves only for ids the insert returned, so 50 parallel viewers give exactly 50.

## Back-fill on follow (2026-10-04)

Scope: `UserFollowedListener`, `FeedBackfillServiceImpl`, `FollowLookupServiceImpl`, `TweetLookupServiceImpl.findNewestByAuthor`,
the consumer factory, error handler and the `user.followed-timeline-dlt` DLT. Read from source plus a throwaway
harness (no Kafka or Docker); the Testcontainers suites were not run.

- **A wrong secret, a missing endpoint or a wrong gateway URL read as "unfollowed" and the back-fill was deleted
  (Medium), fixed 2026-10-04 (frontend step 24).** The gateway answers a missing or wrong secret with the same `404`
  as an unknown path, which `FollowLookupServiceImpl` read as `false`, so `FeedBackfillServiceImpl` silently deleted
  the 50 rows it had just inserted: no retry, no DLT, one INFO line. The guarding test stubbed a `401` the real
  gateway never sends. The gateway now answers `200 {"following": true|false}` and only that is an answer
  (`DECISIONS.md`).
- **A retry after an unfollow re-inserts the rows, and a dead-lettered event leaves them (Low), open.** Each attempt
  inserts before it checks; if the check fails for the whole ~242 s retry window while the public unfollow works, the
  unfollow's delete is undone by the next attempt. At most 50 tweets of an unfollowed author, until retention.
  Fix: look the follow up first, or run the undo from the DLT recoverer.
- **A follow toggled in a loop costs two internal calls and 50 commits each time (Low), open.** The gateway emits an
  event per real follow and nothing dampens a pair; one hot followee keeps its partition busy; `max.poll.records` is
  500, so a slow tweet service can push a backlog past `max.poll.interval.ms`. Fix: one multi-row insert (the trigger
  already named), skip the inserts when the check fails first, a per-pair dampener, a lower `max.poll.records`.
- **`FEED_BACKFILL_SIZE` above 100 starts fine and then dead-letters every follow (Low), open.** The tweet service
  answers 400 above 100, which becomes a retryable "unavailable", so each follow holds its partition ~4 minutes and
  ends in the DLT. Fix: reject a value above 100 at startup.
- **Events and answers that can never succeed are retried for about four minutes each (Low), open.** `occurredAt` at
  `Instant.MIN`, a `200` with a null body, a null item, a null `createdAt` or `id`, and a null event all throw
  something other than `InvalidEventException`; only a failed validation is fatal. A hostile list of 5000 tweets made
  5000 inserts because the response is not capped. Fix: validate `occurredAt`, treat null bodies and items as
  unavailable, cap the list at the back-fill size.
- **`occurredAt` is trusted: a far-past value skips the 7-day window (Low), open.** The window comes from the event,
  not the clock, and feed reads don't filter by age, so old tweets show until the nightly cleanup. Fix: clamp the lower
  bound to `clock.instant().minus(retention)`.
- **A forged self-follow event deletes the user's own tweets from their own feed (Low), open.** The gateway never
  emits one and this service doesn't check, so the check answers "not following" and the undo deletes the author's
  entries in that feed. Needs write access to the topic. Fix: reject follower equal to followee as an invalid event.
- **A malformed event's raw text reaches the log through the Kafka error handler (Low), open.** Jackson's
  `InvalidFormatException` repeats the offending string unescaped, newlines included, in the exception cause (and a
  13.5 KB header on the dead letter). A forged log line needs write access to the topic; the three older listeners are
  the same. Fix: escape line breaks, or log only the exception class for deserialization failures.
- **The tests miss the real wrong-secret answer and most malformed events (Info), open.** The poison-event test sends
  one shape; nothing sends a non-UUID id, `Instant.MIN`, non-JSON, a self-follow or a tombstone, or checks that a retry
  after an unfollow ends with no rows.
- **Clean:** the tweet service truncates `createdAt` to milliseconds and the back-fill to microseconds like the
  fan-out, so one tweet never becomes two rows; all SQL is bound; the secret reaches only the gateway client and no
  body, email or secret is logged; the service is not `@Transactional`, so no connection is held across the two HTTP
  calls; the ordering of check, unfollow and delete is safe except follow, unfollow, follow across the two topics
  (accepted); deleted tweets are not returned; unknown fields are ignored and malformed UUIDs and timestamps are
  deserialization failures that go to the DLT.
