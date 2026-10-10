# Twitter API Gateway — plan

The single front door of the Twitter clone: it owns users and all access rules. **Phases 1–5 are done
(2026-09-29 to 2026-10-04).** This file holds only what is still open: the accepted gaps with their triggers, and
what is out of scope. The step-by-step plan, design record and interview of phases 1–5 were trimmed on 2026-10-09 and
live in this file's git history.

Where things live: calls made while building in `DECISIONS.md`, the HTTP scenario catalog in `docs/TESTING.md`, the
Kafka contracts in `docs/EVENTS.md`, the audits in `docs/SECURITY-AUDITS.md`, conventions and standing rules in
`CLAUDE.md`.

## Status

| Phase | Scope | Done |
|---|---|---|
| 1 | Auth + email confirmation | 2026-09-29 |
| 2 | Profile + MinIO pictures | 2026-09-29 |
| 3 | Follows | 2026-09-30 |
| 4 | Tweet routing | 2026-09-30 |
| 5 | People to follow | 2026-10-04 |

**Left:** the accepted gaps below, when their triggers land. The gateway steps owned by other plans (the internal
API, `user.unfollowed`, the feed, saved-tweets, views, likes and tweet-details routes) are built and tracked in
`../twitter_timeline_service/PLAN.md` and `../twitter_tweet_service/PLAN.md`. The mail service's follow email is built.

## Out of scope
Mail service and SPA pages; password reset, account deletion, username change, display names; roles/admin; birthdate
at signup and age checks; tweet logic (owned by `../twitter_tweet_service`), timelines, notifications, blocks, mutes,
private accounts; anonymous profile viewing; refresh tokens; image resizing; searching or filtering the people list
and hiding people the caller already follows.

## Accepted gaps — revisit when the named trigger lands
- **One `enabled` flag means "confirmed"; no roles/admin.** Trigger: first ban, or moderation features.
- **No rate limiting** except the resend cooldown (login capped only by bcrypt-12; register, uploads, follows and the
  tweet routes unthrottled). Trigger: abuse, or a second instance.
- **Register leaks whether an email/username exists** (explicit `409`s). Login's `403` reveals "unconfirmed" only to
  someone holding the password.
- **Squatting:** a pending account holds an email/handle up to 7 days plus cleanup; a real owner clicking the
  squatter's link confirms an account the squatter controls. Trigger: password reset exists, or first report.
- **Email and live confirmation token travel through Kafka in plaintext.** Trigger: Kafka exposed → TLS/SASL.
- **Outbox is at-least-once** (the mail service dedupes on `eventId`); **no row-claiming** in the poller (trigger:
  second instance → `SKIP LOCKED`); **`FAILED` rows need manual handling** (trigger: first one that matters).
- **Scheduled jobs run on every instance.** Trigger: second instance → ShedLock/advisory lock.
- **Dev compose has default credentials and plaintext Kafka** (ports bound to `127.0.0.1`). Trigger: any shared
  environment.
- **Log lines can carry attacker-chosen or personal text** (unknown-path WARN, constraint-violation WARN with email).
  Trigger: logs shipped to a shared system.
- **`ddl-auto=update`, no migrations.** Trigger: second environment or first destructive change → Flyway.
- **JWT 1h, no refresh, no revocation.** Trigger: hourly logouts annoy, or need to kill sessions.
- **Orphan MinIO objects** when the post-commit delete of an old object fails. Trigger: storage growth → sweep job.
- **Images stored as uploaded: no resize, EXIF (incl. GPS) retained.** Trigger: before any public deployment.
- **Images (profile and tweet) are proxied through the gateway.** Trigger: image traffic dominates → presigned URLs/CDN.
- **Any logged-in user can fetch any file id** (all files are profile images). Trigger: first private media type.
- **No restrictive `Content-Security-Policy` on served files** (phase 2 audit, Low; not exploitable while only
  JPEG/PNG/WebP are accepted). Trigger: loosening the type check (e.g. SVG).
- **Follower and following lists are readable by any logged-in user** (phase 3 audit, Low): no private accounts by
  design. Trigger: private accounts → check the viewer against the target's visibility.
- **State-changing routes rely on `SameSite=Strict` alone, with CSRF disabled** (phase 3 audit, Low): it doesn't stop
  a request from a sibling subdomain of the same site. Trigger: untrusted content on a sibling subdomain → a CSRF token.
- **Hot-row contention on a popular account's counters.** Trigger: measurable follow latency → sharded counters.
- **No counter reconciliation job** (`@Check >= 0` catches only negative drift). Trigger: any observed drift.
- **Any logged-in user can page through every confirmed account** (phase 5), with each person's `followersCount`;
  profiles were already readable by username, and this makes them enumerable. Trigger: abuse, or a second instance →
  rate limit on `GET /api/v1/users`.
- **No popularity ordering** (phase 5): newest first, because counters move while a reader pages and would repeat
  or skip people. Trigger: newest-first stops being useful → a ranked list from a snapshot or a stored score, which
  needs its own design.
- **No search or filter on the people list** (phase 5). Trigger: the first request to find someone by name → a `q`
  parameter on the same endpoint.
- **User and file ids are time-based UUIDs** (`@UuidGenerator(style = TIME)` in `CommonEntity`): they embed the host's
  IPv4 address, JVM start time and a counter (phase 5 audit, Low). No id is a secret today. Trigger: a deployment
  whose network addresses matter, or any use of an id as a secret → a random or v7 generator.
- **The three follow-list queries still use the `OR` cursor predicate:** a page reads the rows newer than the cursor
  within one account's follows. Trigger: an account with six-figure follows or a slow list → the row-value form the
  people list uses.

### Phase 4 gaps
- **The tweet service trusts `X-User-Id`** and is safe only while nothing but the gateway can reach it. Trigger: any
  shared or deployed network → a shared internal secret or mTLS.
- **One 10s read timeout for every route.** Trigger: slow uploads start hitting `504` → a per-route timeout.
- **Only `X-User-*` headers are stripped;** look-alikes (`X-UserId`, `X-Original-User-Id`) reach the tweet service
  (audit finding 2, Low). Trigger: any downstream that trusts another identity-like header → forward an allowlist.
- **Path segments `;x=y`, `/./` and `//` answer `401`** even with a valid cookie (audit finding 3, Info); it fails
  closed. Trigger: a client that legitimately sends such paths.
- **Tomcat's HTML `400` page for paths it rejects** (audit finding 4, Info; gateway-wide). Trigger: a public
  deployment → an error page or filter that answers JSON.
