# Twitter Tweet Service: plan

**Status: built (steps 1-10 done, 2026-09-30).** `mvn verify` is green 3x in a row (202 tests, none
disabled). Gateway phase 4 (`../twitter_api_gateway/PLAN.md`) may start.

**Later changes:** `../twitter_timeline_service/PLAN.md` added `GET /internal/v1/tweets?ids=` here (its phase 1,
built 2026-10-03, plus the startup collection creation) and removed `views` (its phase 3, step 24, 2026-10-03):
the counter now lives in the timeline service, and the view gaps moved with it. Both are planned there, not here.

Owns tweets: create (text and up to 4 images), read, edit the text, delete, and the `tweet.created` /
`tweet.deleted` events. It knows nothing about users or authentication: the gateway proxies
`/api/v1/tweets/**` and sets `X-User-Id`. Designed via `/grill-me` on 2026-09-30.

Where things live now: calls made while building are in `DECISIONS.md`, the test catalog in `TESTING.md`, the
Kafka contracts in `EVENTS.md`, conventions in `CLAUDE.md`, the security audits in
`SECURITY-AUDITS.md`. The original step-by-step plan (steps, gates, interview record) was trimmed out of this file.

---

## Design in short

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
    tweet is gone (up to 3 attempts), so exactly one caller gets `204`.
- **Events:** transactional outbox, at-least-once, key `tweetId`, 3 partitions per topic; consumers dedupe on
  `eventId`. No `tweet.updated`. Contracts in `EVENTS.md`.
- **Tests:** Testcontainers only (Mongo, Kafka, MinIO), deterministic (no sleeps, mutable clock, random author
  per test). Unit, repository, HTTP e2e (`TweetControllerIntegrationTest`, real-port limits test), Kafka
  publisher, and a concurrency suite. Exit rule: `mvn verify` green 3x in a row.

## Out of scope

Timelines, feeds, "tweets by user", replies, retweets, quotes, likes, bookmarks, hashtags, mentions, search;
editing images; soft delete; unique viewers; geolocation; user data in tweet responses; wiring into the
gateway (gateway phase 4).

## Accepted gaps — revisit when the named trigger lands

- **The service trusts `X-User-Id` blindly.** Anyone who can reach port 8081 can act as any user. Mitigated
  by the gateway stripping and re-setting the header, and by the port being private. **Trigger:** the
  service is reachable by anything but the gateway (any shared or deployed network) → a shared internal
  secret, or mTLS between gateway and service.
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
