# Twitter Tweet Service: plan

**Status:** phase 1 built (steps 1-10 done, 2026-09-30). Phases 2-3 (replies) planned 2026-10-06; phase 2 built and hardened 2026-10-07 (steps 11-22), phase 3 built and hardened 2026-10-07 (steps 23-30).

| Phase | Scope | Done |
|---|---|---|
| 1 | Tweets: create, read, edit, delete, events (steps 1-10) | 2026-09-30 |
| 2 | Replies API (steps 11-22) | 2026-10-07 (audit: `SECURITY-FINDINGS.md`, 1 Low and 1 Info fixed; timeline: 1 Low and 1 Info fixed, 1 Low and 1 Info accepted) |
| 3 | Replies UI (steps 23-30) | 2026-10-07 (audit: `../frontend/SECURITY-FINDINGS.md`, 1 Low fixed; visual check waived) |

**Later changes:** `../twitter_timeline_service/PLAN.md` added `GET /internal/v1/tweets?ids=` here (its phase 1,
built 2026-10-03, plus the startup collection creation) and removed `views` (its phase 3, step 24, 2026-10-03):
the counter now lives in the timeline service, and the view gaps moved with it. Both are planned there, not here.

Owns tweets: create (text and up to 4 images), read, edit the text, delete, and the `tweet.created` /
`tweet.deleted` events; from phase 2 also their replies. Until phase 2 it knows nothing about users or
authentication: the gateway proxies `/api/v1/tweets/**` and sets `X-User-Id`. Designed via `/grill-me` on
2026-09-30; phases 2-3 via `/grill-me` on 2026-10-06 (Q1-Q14, spec `../docs/replies-design.md`).

Phases 2-3 touch four other projects; their steps live here, marked **[timeline]**, **[gateway]**, **[frontend]** or
**[e2e]**, and their own `PLAN.md` files only point here (as likes did in the timeline plan).

Where things live now: calls made while building are in `DECISIONS.md`, the test catalog in `TESTING.md`, the
Kafka contracts in `EVENTS.md`, conventions in `CLAUDE.md`, the security audits in
`SECURITY-AUDITS.md`. The original step-by-step plan of phase 1 (steps, gates, interview record) was trimmed out of
this file.

---

## Design in short (phase 1)

- **Stack:** Java 25, Spring Boot 4.1.1, Mongo (`mongo:8.2`, single-node replica set for transactions),
  MinIO (own bucket `tweet-images`), Kafka. No Spring Security. Port 8081. Ported from the gateway: error
  shape, body-size filter, image signature check, storage service, outbox publisher.
- **Identity:** `X-User-Id` (canonical UUID) read by `@CurrentUserId`; missing or malformed gives `400`. Trusted
  blindly, so the service must only be reachable through the gateway.
- **Tweet:** `id`, `authorId`, `content` (trimmed, at most 280 code points), up to 4 embedded
  images (JPEG/PNG/WebP by magic bytes, 5 MB each, never editable), `createdAt`, `updatedAt` (changes only on a
  text edit). A tweet needs text or an image. Timestamps come from the `Clock`, in milliseconds.
- **Endpoints** (all need `X-User-Id`):
  - `POST /api/v1/tweets`: multipart `content` and `images`, `201`. Validates everything first, stores the
    images, then saves the tweet and its outbox message in one transaction; the images are removed if that
    fails. Body cap 21 037 056 bytes; other routes keep 8 KB.
  - `GET /api/v1/tweets/{id}`: returns the tweet and writes nothing.
  - `GET /api/v1/tweets/{id}/images/{imageId}`: streams the image.
  - `PUT /api/v1/tweets/{id}`: author only (`403` otherwise), text only, conditional update, no event.
  - `DELETE /api/v1/tweets/{id}`: author only, hard delete; tweet delete and `tweet.deleted` message in one
    transaction, images deleted after commit (failure logged only). A write conflict is retried unless the
    tweet is gone (up to 3 attempts), so exactly one caller gets `204`. Phase 2 replaces the 3 attempts (Q5).
- **Events:** transactional outbox, at-least-once, key `tweetId`, 3 partitions per topic; consumers dedupe on
  `eventId`. No `tweet.updated`. Contracts in `EVENTS.md`.
- **Tests:** Testcontainers only (Mongo, Kafka, MinIO), deterministic (no sleeps, mutable clock, random author
  per test). Unit, repository, HTTP e2e (`TweetControllerIntegrationTest`, real-port limits test), Kafka
  publisher, and a concurrency suite. Exit rule: `mvn verify` green 3x in a row.

---

## Phase 2 — Replies API ✅ **Done** (2026-10-07)

Spec: `../docs/replies-design.md`. Built in steps 11-22 (tweet, timeline, gateway, e2e); details in `DECISIONS.md`, the
test catalog in `TESTING.md`, the audit in `SECURITY-AUDITS.md`.

### What was built

- **Reply document** (`replies` collection, created at startup next to `tweets`): index
  `ix_replies_tweet_created_id {tweetId, createdAt, _id}`; `Tweet.replyCount` (a missing field reads `0`, no migration),
  moved only by a `$inc` that leaves `updatedAt` alone.
- **Four routes** under `/api/v1/tweets/{tweetId}/replies`: create (`201`), list (keyset cursor, oldest first),
  edit (`PUT`, author only, `edited = true`, same text still counts, Q3), delete (reply author or tweet author).
  Someone else's reply and a reply of another tweet answer `404`, as does a missing one (audit, step 22).
- **Create order (Q6):** insert the reply, then `$inc +1`; no tweet matched → `404` and the abort undoes the insert.
  Delete removes the reply and only then `-1`.
- **Conflict retrier (Q5):** `ConflictRetrier` retries a transaction on a write conflict with a 5-30 ms random pause
  for ~2 s, then `503 {code: "BUSY"}`; time source and sleeper injected. Reply create, reply delete and the tweet
  delete use it; the tweet delete also removes its replies in its transaction.
- **Gateway user lookup:** `UserLookupService` (`/internal/v1/users`, `X-Internal-Secret`, `INTERNAL_API_SECRET`
  32+ bytes, no default) names reply authors; `502` / `504` on upstream failure; a page hides a reply whose author is
  not returned. Caller not found → `403 CALLER_UNKNOWN`, nothing written (Q7).
- **`replyCount`** on every tweet answer (public read, create and edit answers, the internal batch).
- **[timeline]** `replyCount` passed into the feed item (after `likedByMe`); `assembleFetched` split out of `assemble`
  (Q4); `GET /api/v1/tweet-details/{tweetId}` (one tweet as a feed item), `404` for a gone tweet or author.
- **[gateway]** `/api/v1/tweet-details/*` on the `timeline-service` route; the Identity group covers the four routes.
- **Tests:** repository, unit (service, retrier, client), HTTP (`ReplyControllerIntegrationTest`), concurrency
  (`ReplyConcurrencyIntegrationTest`), timeline `TweetDetailsControllerIntegrationTest`, gateway
  `TimelineRoutesIntegrationTest.TweetDetails`, e2e `replies.spec.ts`. HTTP, concurrency, timeline and gateway
  scenarios are in the `TESTING.md` files.
- **Audit (2026-10-07):** tweet service 1 Low (chunked multipart skipped the body cap: multipart is now `415` off the
  create route) and 1 Info (`403` vs `404` on someone else's reply: now `404`) fixed; timeline details read 1 Low
  (sequential calls could pass the gateway's 10 s: timeouts now 1 s + 3 s) and 1 Info (malformed tweet answer: `502`)
  fixed, 1 Low and 1 Info accepted (below). Gate: tweet 423, timeline 670, gateway 851 tests green 3×; e2e 54 green 3×
  from a fresh stack.

---

## Phase 3 — Replies UI ✅ **Done** (2026-10-07)

Built in steps 23-30 (frontend, e2e); the Vitest and Playwright test names are the catalog. Calls made while building are
in `../frontend/DECISIONS.md`, the conventions in `../frontend/CLAUDE.md`, the audit in
`../frontend/SECURITY-FINDINGS.md` ("Audit — 2026-10-07 (tweet details page, post cell, tab panels)").

### What was built

- **Data (Q8):** `TweetItem.replyCount` (`toOwnPost` starts at `0`); `src/api/replies.ts` (`fetchReplies`, `createReply`,
  `updateReply`, `deleteReply`, `Reply` with the author's picture mapped), `src/api/tweetDetails.ts`; `ENDPOINTS.replies`,
  `reply`, `tweetDetails`, every id encoded as one path segment.
- **Reply count and the clickable post (Q9, Q10):** `PostActions` has a reply link (speech-bubble icon, `formatCount`,
  label "Replies") before the heart; a click anywhere on a `PostCell` opens `/tweets/:id` except on a button, link or image
  or at the end of a text selection (`isClickable` is off on the details page).
- **Route (Q14):** `ROUTES.tweet` + `tweetPath`; `TabPanels` renders the details as a third non-tab panel keyed by the
  tweet id (no tab row), so the feed and People stay mounted and Back restores the scroll (`useTabScrollMemory`,
  `useTabVisits`); `useOpenTweetId` accepts only a UUID, any other id redirects to `/feed` (audit finding 6).
- **Details page (Q11, Q13):** back link (`navigate(-1)` with history, else `/feed`), the post reported through
  `ViewReporter`, `POST NOT FOUND` on `404`, `SIGNAL LOST` / `TRY AGAIN` on other failures; the details read and the
  first replies page start together (`useTweetDetails`, `useReplyThread` on `usePagedList`); `END OF REPLIES`,
  `NO REPLIES YET`, the replies error with retry under the composer.
- **Composer (Q11):** `ReplyComposer` + shared `ReplyTextField` (label `YOUR REPLY`, code-point counter, `REPLY` off when
  empty or over 280); errors under the field (`404`, `503`, other); a sent reply shows under the composer and the post's
  count goes up by 1.
- **Reply, edit, delete (Q12):** `ReplyCell` (`EDITED`, `EDIT` on your reply, `DELETE` on your reply or any reply under your
  post), `ReplyEditor` (`SAVE` off while empty, too long or unchanged; Escape cancels; focus returns to `EDIT`),
  `DELETE? YES / NO` (focus on `NO`; a `404` also removes the reply); edits and deletes are overrides in `useReplyThread`.
- **Tests:** Vitest 960 (API, `PostActions`, `PostCell`, `TabPanels`, `TweetDetailsPage`, `ReplyComposer`, `ReplyCell`,
  `endpoints`); Playwright `replies-ui.spec.ts` (4 journeys). Gate: build, lint, `npm test` clean; e2e 58 green 3× from a
  fresh stack.
- **Audit (2026-10-07):** no Critical, High or Medium; 1 Low fixed (the address reached API paths), the reply thread joins
  finding 5 (`usePagedList` follows a cursor with no progress check; open, no trigger today).

## Left open

- **Phase 3 visual check waived** (step 30): the details page with a long thread, an edited reply and the edit and delete
  states at 1440 and 320 px was not looked at by hand.

---

## Out of scope

Timelines, feeds, "tweets by user", retweets, quotes, likes, bookmarks, hashtags, mentions, search;
editing images; soft delete; unique viewers; geolocation; user data in tweet responses; wiring into the
gateway (gateway phase 4). From phases 2-3: replies to replies; images, likes, saves or views on replies; a
`reply.created` event and reply notifications; live updates of a reply thread; edit history of a reply.

## Accepted gaps — revisit when the named trigger lands

- **The service trusts `X-User-Id` blindly.** Anyone who can reach port 8081 can act as any user. Mitigated
  by the gateway stripping and re-setting the header, and by the port being private. From phase 2 the service also
  holds `INTERNAL_API_SECRET`. **Trigger:** the service is reachable by anything but the gateway (any shared or
  deployed network) → a shared internal secret, or mTLS between gateway and service.
- **No `tweet.updated` event** (Q11). **Trigger:** the first consumer that stores tweet text (search,
  timeline cache).
- **Views are not counted here any more** (Q7 is superseded). The timeline service counts unique viewers; its
  gaps (rows kept forever, hot counter, the author's own views) are in `../twitter_timeline_service/PLAN.md`.
- **No edit window and no edit history.** **Trigger:** misuse of edits (e.g. changing a tweet after it gets
  attention) → a 1h window and/or stored versions.
- **Images can't be edited** (Q5); delete and repost instead.
- **Orphan MinIO objects** when a best-effort delete fails (after a failed create or after a delete commits).
  **Trigger:** noticeable storage growth → a sweep job deleting objects no tweet references.
- **Images stored as uploaded: no resize, EXIF (including GPS) retained.** **Trigger:** before any public
  deployment.
- **Any caller can fetch any tweet image** by id (all tweets are public). **Trigger:** private accounts or
  private media.
- **No rate limiting on tweeting.** **Trigger:** spam, or a second instance.
- **Outbox is at-least-once**, has **no row-claiming** (trigger: second instance → claim with
  `findAndModify` on a `claimedBy`/`claimedAt` field), and **`FAILED` messages need manual handling**
  (trigger: the first one that matters).
- **Scheduled jobs run on every instance.** **Trigger:** a second instance → a lock (e.g. ShedLock's Mongo
  provider).
- **Dev Mongo has no auth** (bound to `127.0.0.1`). **Trigger:** any shared environment → auth plus a
  replica-set keyfile.
- **A large `content` text part is read into memory before it is rejected** (up to the 21 MB create cap), and
  **up to 10 file parts are parsed before the 4-image limit applies** (`SECURITY-AUDITS.md`, 2026-09-30). **Trigger:** a caller path that isn't authenticated and metered → a per-part size cap
  (e.g. a custom multipart check or a smaller `content` limit) and Tomcat's `max-part-count` set to 5.
- **Tweet text keeps control and bidirectional characters** (`SECURITY-AUDITS.md`). **Trigger:** the first consumer that
  renders `content` → sanitise there, or reject control characters on write.
- **A `since` outside `java.util.Date`'s range on the by-author read answers `500` with a stack trace** (Low).
  **Trigger:** any caller that doesn't compute `since` itself → reject out-of-window values with the `limit` 400 shape,
  or map `ConversionFailedException` to 400.
- **Mongo schema is enforced only by the application** (no `$jsonSchema` validator). **Trigger:** a second
  writer to the collection.

### Phases 2-3 (replies)

- **No `reply.created` event.** **Trigger:** notifications ("X replied to your post") → an event through the outbox.
- **Large tweet delete:** a tweet with very many replies makes one large transaction. **Trigger:** slow or failing
  deletes → delete the tweet, sweep its replies afterwards.
- **Hot tweet document:** replies on a viral tweet conflict on `replyCount` and retry; past ~2 s the caller gets
  `503` (Q5). **Trigger:** `503 BUSY` answers in practice → a counter outside the tweet document.
- **One gateway call per replies page,** no cache. **Trigger:** details p95, or gateway load → cache authors.
- **No rate limiting on replies.** **Trigger:** abuse, or a second instance.
- **`replyCount` can exceed the visible replies** if an author disappears (account deletion is out of scope).
  **Trigger:** account deletion → remove or anonymise their replies.
- **Counts on a list post go stale after the details page** (Q14): reply there, press Back, and the feed post still
  shows the old reply count (and like/save state) until a reload. **Trigger:** users noticing → the details page
  reports your changes to `Shell`, which patches the list item.
- **`/saved` and `/liked` reload from the top on Back** from a details page (only the feed and People stay
  mounted). **Trigger:** long saved or liked lists → keep those pages mounted too.
- **Your new reply shows under the composer, not in order,** until a reload (Q11); **other people's replies appear
  only after a reload.** **Trigger:** live conversations → polling or a push channel.

---

## Interview record (`/grill-me`, 2026-10-06)

Replies, from `../docs/replies-design.md`.

| # | Question | Answer |
|---|---|---|
| 1 | Where do the steps go? | New phases here; timeline, gateway, frontend and e2e steps tagged; their plans point here |
| 2 | Phase cut? | Phase 2 Replies API (tweet, timeline, gateway, API e2e), phase 3 Replies UI (frontend, UI e2e), as likes 4/5 |
| 3 | Edit with the same text? | Marks `edited` and moves `updatedAt`, as the tweet edit |
| 4 | Details read without reading the tweet twice? | Split `assemble`: `assembleFetched(viewerId, tweets, authorsById)` |
| 5 | Retry budget for reply write conflicts? | Retry with a 5-30 ms random pause for ~2 s, then `503`; one helper, the tweet delete moves onto it |
| 6 | Create order in the transaction? | Insert the reply, then `+1`; `+1` matching nothing aborts with `404` |
| 7 | The caller lookup finds no user? | `403 CALLER_UNKNOWN`, nothing written |
| 8 | Settle the UI now? | Yes |
| 9 | What opens the details page? | A click anywhere on the post |
| 10 | Click rules and the keyboard path? | Click except on buttons, links, images or a text selection; a reply-count link in the action row for keyboard and screen readers; not clickable on the details page |
| 11 | Composer and new reply placement? | Composer under the post; your sent reply straight under it, count +1; in order after a reload |
| 12 | Edit and delete UI? | Inline `EDIT` / `DELETE`; edit in place with counter and `SAVE` / `CANCEL`; delete asks `DELETE? YES / NO`; a `404` removes it |
| 13 | Does the details page count a view? | Yes, through the same `ViewReporter`; replies report nothing |
| 14 | Back from details? | Same spot: details is a third panel in `TabPanels`, the feed stays mounted; stale counts accepted |
| — | `(my call)` | Error codes `BUSY`, `CALLER_UNKNOWN`; the retrier's time source and sleeper injected (test clock is frozen); no migration for `replyCount`; `replyCount` after `likedByMe`; the details read `400`s as other routes; UI texts (`YOUR REPLY`, `REPLY`, `EDITED`, `END OF REPLIES`, `NO REPLIES YET`, `POST NOT FOUND`, error messages); the back link's fallback to `/feed`; catalogs as `@Disabled`, `it.todo`, `test.fixme`; an `/exploit-hunter` step closing each phase; a manual visual check in phase 3 |
