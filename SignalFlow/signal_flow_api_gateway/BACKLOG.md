# Backlog

What is still open for the gateway: work deferred on purpose, and gaps accepted on purpose with the trigger
that should reopen each. Carried over from the closed-out `PLAN.md` (`git log -p -- PLAN.md`). Calls already
made are in `DECISIONS.md`; shortcuts that are easy to break are in `CLAUDE.md`.

Anything picked up from here: **no step starts on a red or missing test, and each step ends green.**

## Fix

Nothing open.

## Build (deferred by decision, not oversight)

- **The SPA's admin topic/category screens.** The read-only feed shipped (`GET /api/v1/feed`, subscribe and
  unsubscribe from a topic card). Admin create/edit/delete is unbuilt, though every endpoint is already routed
  through the gateway.
- **Email confirmation.** Register activates immediately, so anyone can register with an address they do not own.

## Accepted gaps (revisit when the named trigger lands)

- **Claims are stale for up to an hour, including for writes.** Disabling an account or changing a role takes
  effect only when the current token expires. A demoted ADMIN keeps topic and category create/edit/delete for the
  rest of the window; a disabled account keeps writing subscriptions. Direct consequence of "no DB read on an
  authenticated request". **Trigger:** needing to cut off an account immediately.
- **A deleted user's token keeps working until it expires** (up to `JWT_TTL`, 1 h). Reads succeed; subscribe
  answers 401. **Trigger:** same as above (check the user per request, or keep a revoked-token list).
- **No server-side logout revocation.** A stolen token lives until it expires (up to 1 h). Same trigger.
- **User deletion is minimal.** Queued emails are not cancelled, there is no user list or admin UI, admins cannot
  be deleted through the app, and the only audit trail is a log line. The user's `notification_outbox` rows are
  deleted with the account. **Trigger:** more than one person administering accounts.
- **The reconciliation job runs on every instance.** No scheduler lock, so N instances make N full passes at
  05:00. The deletes give the same result, so it only wastes lookups. **Trigger:** a second instance, then
  ShedLock or `pg_try_advisory_lock`.
- **The outbox poller runs on every instance, with no row claiming.** Two instances could both pick and publish
  the same `PENDING` row before either deletes it. **Trigger:** a second instance, then
  `SELECT ... FOR UPDATE SKIP LOCKED` when claiming a page.
- **`interestTopicId` is not validated against a real topic on subscribe** (decided 2026-09-25, see
  `DECISIONS.md`). The nightly reconciliation bounds the damage to about 24 h of junk rows, capped per user.
- **Register leaks whether an email is registered** (explicit `409`, chosen over a silent success).
- **No versioned migrations.** `ddl-auto=update` adds tables and columns but never alters them (see
  `DECISIONS.md`). **Trigger:** a second deployed environment, or a column change on a database that holds data.
- **Downstream trust model is unimplemented.** The topic service is forwarded to with credentials stripped and
  no identity, and trusts the gateway on network isolation alone. Client-supplied `X-User-*` headers are
  stripped before forwarding (fixed 2026-09-25); building the identity headers is still open.
  **Trigger:** the first downstream that needs caller identity.
- **Reconciliation trusts the topic service's answer completely.** If `INTEREST_TOPIC_SERVICE_URL` points at the
  wrong service, such as one with an empty database, it validly answers "none exist" and the 05:00 run
  hard-deletes every subscription with no undo. A guard would abort if more than about 50% of checked ids come
  back missing. Not built by decision. **Trigger:** a second deployed environment, or the first accidental
  mass delete.
- **User emails travel through Kafka in plaintext** (the `topic-news.notification-requested` payload). Fine on a
  private network. **Trigger:** Kafka shared or exposed beyond the internal network, then TLS/SASL, or send
  `userId` only and let the consumer resolve the email.
- **`topic-news.generated-dlt` records are only logged, with no replay tool.** **Trigger:** the first record
  that needs replaying.
- **A crash between a successful Kafka publish and deleting that outbox row duplicates that row on the next
  poll.** The inbox makes every other redelivery a no-op. The mail service's `(newsId, userId)` Redis dedupe
  turns this into at most a duplicate email.
- **One `topic-news.generated` event holds a single Postgres transaction open for its whole fan-out.**
  `TopicNewsNotificationServiceImpl.notifySubscribers` wraps every keyset page's batch insert plus the final
  inbox marker in one `@Transactional` method, so a topic with many subscribers holds row locks and pins the
  vacuum horizon for the whole run (left as-is 2026-09-25, see `DECISIONS.md`). **Trigger:** one run's duration
  becomes operationally noticeable, then commit per keyset page (a wider partial-fan-out window on a crash), or
  cap subscribers per invocation.

## Resolved since the plan was written

- **Rate limiting:** login and register are now limited per client address (`AuthRateLimitFilter`, see
  `CLAUDE.md`), which the plan had said would never be built.
