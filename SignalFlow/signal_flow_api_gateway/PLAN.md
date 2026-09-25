# SignalFlow API Gateway — what's left

The **auth** (2026-09-21), **subscriptions** (2026-09-22), **interest-topic routing** (2026-09-23),
**subscription reconciliation** (2026-09-23), **topic-news notification fan-out** (2026-09-24) and
**topic-news notification inbox/outbox** (2026-09-24) phases have shipped and are green. Their design,
step-by-step gates and `/grill-me` interview records are no longer carried here. Read them from git history
(`git log -p -- PLAN.md`). Calls made during implementation live in `DECISIONS.md`, the e2e catalog in
`TESTING.md`.

A full exploit-hunter audit ran per phase between 2026-09-22 and 2026-09-24 (five reports, one per phase).
Every finding was triaged 2026-09-25: fixed, or explicitly decided as an accepted gap with a trigger. The
reports themselves were deleted once nothing in them was left undecided — their reasoning lives in
`DECISIONS.md` (what was fixed and why) and below (what's still open and why).

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Fix — real defects, ranked

Nothing open.

## Build — deferred by decision, not oversight

- **The SPA's admin topic/category screens.** The user-facing read-only list shipped as the feed page
  (`GET /api/v1/feed`, subscribe/unsubscribe wired from a topic card). Admin create/edit/delete is still
  unbuilt. Unblocked, since all the endpoints are routed through the gateway.
- **Email confirmation.** Register activates immediately today, so anyone can register with an address
  they do not own.

## Accepted gaps — revisit when the named trigger lands

- **No rate limiting anywhere.** Login brute-force is capped only by bcrypt-12 (~4 guesses/sec/core), and
  register is `permitAll` with no throttle at all — which the subscription cap amplifies: one anonymous
  signup yields a user row *plus* up to `max-per-user` subscription rows. Topic routing adds unthrottled
  `GET /api/v1/interest-topics?size=100` for any user, each a downstream DB query that can hold a thread for
  up to the 10s read timeout. **Decided 2026-09-25 this will not be built regardless of trigger** — see
  `DECISIONS.md`. Listed here for what it still means for the gateway's behavior, not as a pending fix.
- **Claims are stale for up to an hour, including for writes.** Disabling an account or changing a role
  takes effect only when the current token expires. A demoted ADMIN keeps topic and category
  create/edit/delete for the rest of the window; a disabled account keeps writing subscriptions for the
  same reason. Direct consequence of "no DB read on an authenticated request." **Trigger:** needing to cut
  off an account immediately.
- **No server-side logout revocation.** A stolen token lives until it expires (≤1h). Same trigger.
- **The reconciliation job runs on every instance.** No scheduler lock, so N instances make N full passes at
  05:00. Harmless, since the deletes give the same result either way; only wasted lookups. Same shape as the
  topic service's single-instance scheduler gap. **Trigger:** a second instance → ShedLock or a
  `pg_try_advisory_lock` guard.
- **`interestTopicId` is never validated against a real topic on subscribe.** Decided 2026-09-25 not to add
  real-time validation — see `DECISIONS.md`. The nightly reconciliation job already bounds the damage to
  ~24h of junk rows, capped per user.
- **Register leaks whether an email is registered** (explicit `409`, chosen over a silent success).
- **`ddl-auto=update`, no versioned migrations.** Schema drift between environments is possible.
  **Trigger:** a second deployed environment, or the first destructive schema change.
- **Downstream trust model** (identity headers over a private network) is unimplemented and unverified.
  The topic service is forwarded to with credentials stripped and no identity, because it consumes none; it
  trusts the gateway on network isolation alone. Client-supplied `X-User-*` headers are stripped before
  forwarding (fixed 2026-09-25, ahead of this gap's own trigger — see `DECISIONS.md`); what's still open is
  building the identity headers themselves. **Trigger:** the first downstream that needs caller identity.
- **Reconciliation trusts the topic service's answer completely.** If
  `INTEREST_TOPIC_SERVICE_URL` points at the wrong topic service, such as one with an empty database, it
  validly answers "none exist". The 05:00 run then hard-deletes every subscription, with no undo. A guard would
  check every batch first, then delete, and abort without deleting if more than about 50% of checked topic ids
  come back missing. Not built by decision. **Trigger:** a second deployed environment (where a mis-pointed
  URL becomes plausible), or the first accidental mass delete.
- **User emails travel through Kafka in plaintext** (the `topic-news.notification-requested` payload). Fine
  on a private network. **Trigger:** Kafka is shared or exposed beyond the internal network → TLS/SASL, or
  send `userId` only and let the consumer resolve the email.
- **`topic-news.generated-dlt` records are only logged, no replay tool.** **Trigger:** the first record that
  needs replaying.
- **A `NotificationOutboxPublisherJob` crash between a successful Kafka publish and deleting that row
  duplicates that one row on the next poll.** The inbox makes a crash before the transaction commits, or
  any event redelivery, a clean no-op — this is what's left. Same at-least-once contract;
  `signal_flow_mail_service`'s `(newsId, userId)` Redis dedupe means this duplicate outbox row now produces
  at most a duplicate email, not a duplicate notification pipeline run.
- **The outbox poller runs on every instance, no row-claiming.** Two instances polling concurrently could
  both pick and publish the same `PENDING` row before either deletes it — a duplicate publish, not just
  wasted work like the reconciliation job's multi-instance overlap. **Trigger:** a second instance →
  `SELECT ... FOR UPDATE SKIP LOCKED` when claiming a page.
- **One `topic-news.generated` event holds a single Postgres transaction open for its whole fan-out.**
  `TopicNewsNotificationServiceImpl.notifySubscribers` wraps every keyset page's batch insert plus the
  final inbox marker in one `@Transactional` method; a topic with enough enabled subscribers turns this
  into a long-running transaction that holds row locks and pins the vacuum horizon for its whole duration.
  Left as-is 2026-09-25 — see `DECISIONS.md`. **Trigger:** a topic's subscriber count makes one run's
  transaction duration operationally noticeable → commit per keyset page instead of the whole event,
  accepting a narrower partial-fan-out-on-crash window, or cap subscribers processed per invocation.
