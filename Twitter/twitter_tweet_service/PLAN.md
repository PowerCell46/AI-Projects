# Twitter Tweet Service: plan

**Status:** all three phases built and hardened. Open items: "Left open" and "Accepted gaps".

| Phase | Scope | Steps | Done | Audit |
|---|---|---|---|---|
| 1 | Tweets: create, read, edit, delete, events | 1-10 | 2026-09-30 | `docs/SECURITY-AUDITS.md` |
| 2 | Replies API (tweet, timeline, gateway, e2e) | 11-22 | 2026-10-07 | `docs/SECURITY-AUDITS.md` |
| 3 | Replies UI (frontend, e2e) | 23-30 | 2026-10-07 | `../frontend/SECURITY-FINDINGS.md` |

Where things live: calls made while building `DECISIONS.md`, test catalog `docs/TESTING.md`, Kafka contracts
`docs/EVENTS.md`, audits `docs/SECURITY-AUDITS.md`, conventions `CLAUDE.md`. The replies UI lives in `../frontend`
(its Vitest and Playwright test names are the catalog); views live in `../twitter_timeline_service`.

---

## Design in short

- **Stack:** Java 25, Spring Boot 4.1.1, Mongo (`mongo:8.2`, single-node replica set for transactions), MinIO (bucket
  `tweet-images`), Kafka, port 8081, no Spring Security. Ported from the gateway: error shape, body-size filter, image
  signature check, storage service, outbox publisher.
- **Identity:** `X-User-Id` (canonical UUID) read by `@CurrentUserId`, `400` if missing or malformed; trusted blindly.
- **Tweet:** `content` (trimmed, at most 280 code points) and up to 4 images (JPEG/PNG/WebP by magic bytes, 5 MB each,
  never editable); needs text or an image. `updatedAt` changes only on a text edit. Timestamps from the `Clock`, in ms.
- **Routes:** `POST /api/v1/tweets` (multipart, `201`; body cap 21 037 056 bytes, other routes 8 KB; images removed if
  the transaction fails), `GET /{id}`, `GET /{id}/images/{imageId}`, `PUT /{id}` (author only, text only, no event),
  `DELETE /{id}` (author only, hard delete with its `tweet.deleted` message in one transaction, images removed after
  commit, replies removed with it).
- **Replies:** `replies` collection, routes under `/api/v1/tweets/{tweetId}/replies` (create, keyset-paged list, edit by
  the author, delete by the reply's or the tweet's author). `Tweet.replyCount` moves only by `$inc`, in the same
  transaction: create inserts then `+1`, delete removes then `-1`. `ConflictRetrier` retries write conflicts for ~2 s,
  then `503 BUSY`. Authors are named through `UserLookupService` (gateway `/internal/v1/users`, `INTERNAL_API_SECRET`).
- **Events:** transactional outbox, at-least-once, key `tweetId`, 3 partitions per topic, consumers dedupe on `eventId`.
- **Tests:** Testcontainers only, deterministic (no sleeps, mutable clock, random author per test). Exit rule:
  `mvn verify` green 3× in a row.

## Left open

- **Phase 3 visual check waived** (step 30): the details page with a long thread, an edited reply and the edit and
  delete states at 1440 and 320 px was not looked at by hand.
- **The reply thread follows a cursor with no progress check** (`usePagedList`, frontend audit finding 5). No trigger
  today.

---

## Out of scope

Timelines, feeds, "tweets by user", retweets, quotes, likes, bookmarks, hashtags, mentions, search; editing images;
soft delete; views and unique viewers (timeline service); geolocation; user data in tweet responses. Replies: replies
to replies; images, likes, saves or views on replies; notifications; live updates of a thread; edit history.

## Accepted gaps — revisit when the named trigger lands

- **The service trusts `X-User-Id` blindly.** Anyone who reaches port 8081 can act as any user; mitigated by the gateway
  re-setting the header and the port being private. It also holds `INTERNAL_API_SECRET`. **Trigger:** reachable by
  anything but the gateway (a published port, a broader gateway route, a service on the network that is not trusted)
  → a shared internal secret, or mTLS. A private Docker network on one host does not trigger it.
- **No `tweet.updated` event.** **Trigger:** the first consumer that stores tweet text (search, timeline cache).
- **No edit window and no edit history.** **Trigger:** misuse of edits → a 1h window and/or stored versions.
- **Images can't be edited**; delete and repost instead.
- **Orphan MinIO objects** when a best-effort delete fails. **Trigger:** noticeable storage growth → a sweep job.
- **Images stored as uploaded: no resize, EXIF (including GPS) retained.** **Decision 2026-10-10: accepted, no stripping or resizing is planned**, including for a public deployment. Anyone who can load an image also gets its EXIF (GPS, camera model).
- **Any caller can fetch any tweet image** by id (all tweets are public). **Trigger:** private accounts or media.
- **No rate limiting on tweeting or replies.** **Trigger:** abuse, or a second instance.
- **Outbox is at-least-once**, has **no row-claiming** (trigger: second instance → claim with `findAndModify` on a
  `claimedBy`/`claimedAt` field), and **`FAILED` messages need manual handling** (trigger: the first one that matters).
- **Scheduled jobs run on every instance.** **Trigger:** a second instance → a lock (e.g. ShedLock's Mongo provider).
- **Dev Mongo has no auth** (bound to `127.0.0.1`). **Trigger:** any shared environment → auth plus a replica-set keyfile.
- **A large `content` part is read into memory before it is rejected** (up to the 21 MB create cap), and **up to 10 file
  parts are parsed before the 4-image limit applies** (`docs/SECURITY-AUDITS.md`, 2026-09-30). **Trigger:** a caller path
  that isn't authenticated and metered → a per-part size cap and Tomcat's `max-part-count` set to 5.
- **Tweet text keeps control and bidirectional characters.** **Trigger:** the first consumer that renders `content` →
  sanitise there, or reject control characters on write.
- **Mongo schema is enforced only by the application.** **Trigger:** a second writer to the collection.
- **Large tweet delete:** very many replies make one large transaction. **Trigger:** slow or failing deletes → delete
  the tweet, sweep its replies afterwards.
- **Hot tweet document:** replies on a viral tweet conflict on `replyCount` and retry; past ~2 s the caller gets `503`.
  **Trigger:** `503 BUSY` in practice → a counter outside the tweet document.
- **One gateway call per replies page,** no cache. **Trigger:** details p95, or gateway load → cache authors.
- **No `reply.created` event.** **Trigger:** notifications → an event through the outbox.
- **`replyCount` can exceed the visible replies** if an author disappears. **Trigger:** account deletion → remove or
  anonymise their replies.
- **Counts on a list post go stale after the details page:** reply, press Back, and the feed post shows the old count
  (and like/save state) until a reload. **Trigger:** users noticing → the details page reports changes to `Shell`.
- **`/saved` and `/liked` reload from the top on Back** from a details page. **Trigger:** long saved or liked lists →
  keep those pages mounted too.
- **Your new reply shows under the composer, not in order,** until a reload; **other people's replies appear only after
  a reload.** **Trigger:** live conversations → polling or a push channel.
