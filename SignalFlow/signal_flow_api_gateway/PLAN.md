# SignalFlow API Gateway — what's left

The **auth** (2026-09-21), **subscriptions** (2026-09-22), **interest-topic routing** (2026-09-23),
**subscription reconciliation** (2026-09-23), **topic-news notification fan-out** (2026-09-24) and
**topic-news notification inbox/outbox** (2026-09-24) phases have shipped and are green. Their design,
step-by-step gates and `/grill-me` interview records are no longer carried here. Read them from git history
(`git log -p -- PLAN.md`). Calls made during implementation live in `DECISIONS.md`, the e2e catalog in
`TESTING.md`, and the audits in `exploit-report-2026-09-22-subscriptions.md`,
`exploit-report-2026-09-23-topic-routing.md`, `exploit-report-2026-09-23-reconciliation.md`,
`exploit-report-2026-09-24-topic-news-fanout.md` and `exploit-report-2026-09-24-notification-inbox-outbox.md`.
Each report's status note records which findings were fixed. The rest are open by decision and appear under
*Accepted gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Fix — real defects, ranked

Nothing open. See `exploit-report-2026-09-23-reconciliation.md` for the findings fixed 2026-09-24 and the
one piece left undone by decision, and `exploit-report-2026-09-23-topic-routing.md` #3 for the 413 fix.

## Build — deferred by decision, not oversight

- **The SPA's admin topic/category screens.** The user-facing read-only list shipped as the feed page
  (`GET /api/v1/feed`, subscribe/unsubscribe wired from a topic card). Admin create/edit/delete is still
  unbuilt. Unblocked, since all the endpoints are routed through the gateway.
- **Email confirmation.** Register activates immediately today, so anyone can register with an address
  they do not own.

## Accepted gaps — revisit when the named trigger lands

- **No rate limiting anywhere.** Login brute-force is capped only by bcrypt-12 (~4 guesses/sec/core), and
  register is `permitAll` with no throttle at all — which the subscription cap now amplifies: one
  anonymous signup yields a user row *plus* up to `max-per-user` subscription rows. Doing it properly
  wants shared state (Redis) once there is more than one instance. Topic routing adds unthrottled
  `GET /api/v1/interest-topics?size=100` for any user, each a downstream DB query that can hold a thread for up
  to the 10s read timeout. **Trigger:** a second instance, or abuse in the wild.
- **Claims are stale for up to an hour, including for writes.** Disabling an account or changing a role
  takes effect only when the current token expires, and since the subscriptions phase that window covers
  writes, not just the read-only `/me`. Since topic routing, that also means a demoted ADMIN keeps topic and
  category create/edit/delete for the rest of the window. Direct consequence of "no DB read on an authenticated request."
  **Trigger:** needing to cut off an account immediately.
- **No server-side logout revocation.** A stolen token lives until it expires (≤1h). Same trigger.
- **The reconciliation job runs on every instance.** No scheduler lock, so N instances make N full passes at
  05:00. Harmless, since the deletes give the same result either way; only wasted lookups. Same shape as the
  topic service's single-instance scheduler gap. **Trigger:** a second instance → ShedLock or a
  `pg_try_advisory_lock` guard.
- **The service's `catch (DataIntegrityViolationException)` assumes the unique constraint.** A
  subscription insert can also break the `user_id` FK, and both surface as the same exception — a user
  whose row had been deleted would be told "already subscribed." Unreachable today: no user-delete path
  exists. **Trigger:** the first one that does; discriminate on SQLState `23505` vs `23503` then.
- **Register leaks whether an email is registered** (explicit `409`, chosen over a silent success).
- **`ddl-auto=update`, no versioned migrations.** Schema drift between environments is possible.
  **Trigger:** a second deployed environment, or the first destructive schema change.
- **Downstream trust model** (identity headers over a private network) is unimplemented and unverified.
  The topic service is forwarded to with credentials stripped and no identity, because it consumes none; it
  trusts the gateway on network isolation alone. Every other client header is forwarded verbatim, including
  `X-User-Id`/`X-User-Role` (exploit report 2026-09-23 #2), so when identity headers land the gateway must
  strip the whole reserved prefix on the way in before adding its own. **Trigger:** the first downstream that
  needs caller identity.
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
  duplicates that one row on the next poll.** Narrowed 2026-09-24 from the wider fan-out-phase duplication
  gap: the inbox now makes a crash before the transaction commits, or any event redelivery, a clean no-op —
  this is what's left. Same at-least-once contract; `signal_flow_mail_service` shipped the
  `(newsId, userId)` dedupe this gap was waiting on (2026-09-24, its own Redis inbox), so this duplicate
  outbox row now produces at most a duplicate email, not a duplicate notification pipeline run.
- **The outbox poller runs on every instance, no row-claiming.** Two instances polling concurrently could
  both pick and publish the same `PENDING` row before either deletes it — a duplicate publish, not just
  wasted work like the reconciliation job's multi-instance overlap. **Trigger:** a second instance →
  `SELECT ... FOR UPDATE SKIP LOCKED` when claiming a page.
- **Escalated to Medium 2026-09-24** by the notification inbox/outbox phase — **no cap on a
  `topic-news.generated` record's size before it fans out to every enabled subscriber**
  (`exploit-report-2026-09-24-topic-news-fanout.md` #2, originally Low;
  `exploit-report-2026-09-24-notification-inbox-outbox.md` #3). `TopicNewsEventDTO` still has no
  field-length validation, and Kafka's own per-record cap (~1 MB default) is still the only limit — but
  where each fanned-out copy used to be transient Kafka traffic, every copy is now a durable
  `notification_outbox` row (`data` is an uncapped `TEXT` column) that persists until published or, on
  `FAILED`, indefinitely. A ~1 MB record fanned out to 10,000 enabled subscribers is now ~10 GB of durable
  Postgres storage from one event, not gigabytes of ephemeral throughput. Bounded by the same trust
  assumption as the "downstream trust model" item above (only the topic service can reach this topic
  today). **Trigger:** that item gets built, a length cap is added to `data` before the outbox insert, or
  the first oversized record actually shows up.
- **One `topic-news.generated` event holds a single Postgres transaction open for its whole fan-out**
  (`exploit-report-2026-09-24-notification-inbox-outbox.md` #1, Medium).
  `TopicNewsNotificationServiceImpl.notifySubscribers` wraps every keyset page's batch insert plus the
  final inbox marker in one `@Transactional` method; a topic with enough enabled subscribers (organic, or
  inflated by the "no rate limiting" gap above) turns this into a long-running transaction that holds row
  locks and pins the vacuum horizon for its whole duration. **Trigger:** a topic's subscriber count makes
  one run's transaction duration operationally noticeable → commit per keyset page instead of the whole
  event, accepting a narrower partial-fan-out-on-crash window, or cap subscribers processed per invocation.
- **No length validation on `topicName`/`categoryName` before the outbox insert**
  (`exploit-report-2026-09-24-notification-inbox-outbox.md` #2, Medium). Both default to Hibernate's
  `varchar(255)` on `NotificationOutbox`, and `TopicNewsEventDTO`'s hand-synced-with-the-topic-service
  contract has no validation enforced at any point on the Kafka consume path. A value over 255 characters
  fails the insert, rolls back the whole transaction, exhausts the listener's 3 retries, and lands the
  record in `topic-news.generated-dlt` having notified zero subscribers, with no operator-visible signal
  beyond a buried constraint-violation log. **Trigger:** a version drift between the two services' DTOs, or
  the first oversized value actually shows up → validate or truncate defensively before the insert.
