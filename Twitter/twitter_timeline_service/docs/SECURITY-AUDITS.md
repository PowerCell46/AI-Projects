# Security audits

Last updated: 2026-10-10

The `exploit-hunter` audits of the timeline service, one section per audit, condensed from the original per-audit
reports (dates are the audit dates; the tweet details report was merged in on 2026-10-09). Only fixed findings are kept
in this public file; the open and accepted ones are kept privately. Dependencies were scanned for CVEs on 2026-10-10 (see
the end of this file).

| Audited | Scope | Result |
| --- | --- | --- |
| 2026-10-03 | Phase 1: feed read, listeners, retention, gateway `/internal/v1/**`, the feed route | 1 finding fixed; the open ones are kept privately |
| 2026-10-03 | Phase 2: saved tweets | 0 findings fixed; the open ones are kept privately |
| 2026-10-03 | Phase 3: views | 0 findings fixed; the open ones are kept privately |
| 2026-10-04 | Frontend phase 3: back-fill on follow | 4 findings fixed; the open ones are kept privately |
| 2026-10-06 | Phase 4: likes | 1 finding fixed; the open ones are kept privately |
| 2026-10-07 | The tweet details read (tweet service phase 2, step 22) | 2 findings fixed; the open ones are kept privately |

## Phase 1: feed (2026-10-03)

- **`/actuator/health` showed details on the service port (Low), fixed.** `show-details=always` returned the database
  product, disk path and free bytes; the gateway and tweet service return only `status` and `groups`. The line is
  removed, so the default `never` applies.
- **Clean:** a missing or wrong secret is a `404` like an unknown path; path-traversal variants are 401 or 400 and never
  forwarded; a spoofed `X-User-Id` is replaced by the JWT subject; bad `size` or cursor is a 400 with fixed messages;
  every SQL statement binds its parameters; downstream URLs come from configuration; the secret goes only to the
  gateway client, is compared in constant time and has no default.

## Phase 2: saved tweets (2026-10-03)

0 findings fixed; the open ones are kept privately.

- **Clean:** a non-UUID id is a 400 with no type name; `1-1-1-1-1` parses and is a 404; every query is scoped by
  `X-User-Id` and `unsave` deletes only the caller's row; logs carry UUIDs only; downstream bodies are never returned;
  a 20 KB body is a 413.

## Phase 3: views (2026-10-03)

0 findings fixed; the open ones are kept privately.

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

## Phase 4: likes (2026-10-06)

Read from source only; nothing was executed. Scope: the three like endpoints, the inline `likes` / `likedByMe`
fields, the like deletes in `tweet.deleted`, the gateway's `/api/v1/likes/**` route.

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
- **Clean:** `tweetId` is a bound `UUID` (non-UUID is a fixed 400); JPQL and `findAllById` bind their parameters;
  downstream URLs come from configuration, so no SSRF or open redirect; any authenticated user may read any tweet, as
  through the tweet service, and `savedByMe` / `likedByMe` come from the header the gateway sets from the JWT; error
  bodies are fixed strings and the response carries no email; the service sets no cache entries and the response holds
  per-viewer flags; the endpoint returns JSON, sets no cookies and no raw-HTML sink in `frontend/src` consumes it; the
  route is behind `authenticated()` and wrong methods are `405`.

## Dependency scan (2026-10-10)

`osv-scanner` (Docker image `ghcr.io/google/osv-scanner`) over the four `pom.xml` files and both `package-lock.json`
files found 71 advisories (10 Critical, 34 High, 24 Medium, 3 Low) in `tomcat-embed-core` 11.0.24, `jackson-core` and
`jackson-databind` 2.21.5 and 3.1.5, `lz4-java` 1.10.1 and, in the tweet service, `bcprov-jdk18on` 1.84. All were fixed by
raising the versions in each `pom.xml` (Tomcat 11.0.25, Jackson 2.21.7 and 3.1.7, lz4-java 1.11.4, BouncyCastle 1.85), and
a second scan reported no issues. Reachability was not assessed. Nothing runs the scan automatically yet, so repeat it
before each deployment.
