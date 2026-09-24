# SignalFlow API Gateway — what's left

The **auth** (2026-09-21), **subscriptions** (2026-09-22), **interest-topic routing** (2026-09-23),
**subscription reconciliation** (2026-09-23) and **topic-news notification fan-out** (2026-09-24) phases
have shipped and are green. Their design, step-by-step gates and `/grill-me` interview records are no longer
carried here. Read them from git history (`git log -p -- PLAN.md`). Calls made during implementation live in
`DECISIONS.md`, the e2e catalog in `TESTING.md`, and the audits in `exploit-report-2026-09-22-subscriptions.md`,
`exploit-report-2026-09-23-topic-routing.md`, `exploit-report-2026-09-23-reconciliation.md` and
`exploit-report-2026-09-24-topic-news-fanout.md`. Each report's status note records which findings were
fixed. The rest are open by decision and appear under *Accepted gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Fix — real defects, ranked

Nothing open. The reconciliation report's two findings were fixed 2026-09-24:
`InterestTopicLookupServiceImpl.findExistingIds` now throws `InterestTopicLookupFailedException` when
`existingIds` contains a null element instead of letting `Set.copyOf` NPE (nulls are never filtered out,
since that would delete subscriptions), and `SubscriptionReconciliationServiceImpl`'s constructor rejects a
batch size over 200 with an `IllegalArgumentException` instead of silently disabling the job. The report's
optional "log a 4xx-caused stop at ERROR" half of the second fix was left undone — the current handler can't
already distinguish a 4xx from any other lookup failure without deeper changes to
`InterestTopicLookupFailedException`'s cause chain, and that felt like its own decision rather than a
drive-by addition. (Long non-Latin topic prompts getting 413 from the topic service, exploit report
2026-09-23 #3, was fixed 2026-09-23 alongside the reconciliation phase's existence endpoint.)

## Build — deferred by decision, not oversight

- ~~**The by-topic fan-out read path.**~~ **Closed 2026-09-24** by the topic-news notification fan-out
  phase: the fan-out stayed inside the gateway (a Kafka consumer, not an HTTP read path), so the
  service-to-service auth problem this item was blocked on never had to be solved.
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
- ~~**Any UUID is subscribable.**~~ **Closed 2026-09-23** by the subscription reconciliation phase: subscribe
  still accepts any UUID (bounded by the per-user cap, held under concurrency by a `SELECT ... FOR UPDATE` on
  the caller's user row), but a bogus id lives only until the next 05:00 run.
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
- **Duplicate topic-news notifications on a mid-fan-out retry.** By design (at-least-once); the future email
  consumer must dedupe on `(newsId, userId)`.
- **A popular topic's fan-out can outrun `max.poll.interval.ms` (default 5 min), triggering a rebalance
  mid-run and a full duplicate re-send of everyone already notified** (`exploit-report-2026-09-24-topic-news-fanout.md`
  #1, Medium). `notifySubscribers` walks every enabled subscriber synchronously on the listener thread with
  no overall time budget; the offset commits only when it returns. Same at-least-once contract as the gap
  above covers the resulting duplicates, but repeated rebalances could compound them badly for a large
  enough topic. **Trigger:** a topic's subscriber count or send latency makes one run credibly approach 5
  minutes → bound the walk's total time, raise `max.poll.interval.ms` deliberately, or send asynchronously.
- **No cap on a `topic-news.generated` record's size before it fans out to every enabled subscriber**
  (`exploit-report-2026-09-24-topic-news-fanout.md` #2, Low). `TopicNewsEventDTO` has no field-length
  validation, and Kafka's own per-record cap (~1 MB default) is the only limit, so one inbound record can
  become gigabytes of outbound traffic across a popular topic's subscribers. Bounded by the same trust
  assumption as the "downstream trust model" item above (only the topic service can reach this topic
  today). **Trigger:** that item gets built, or the first oversized record actually shows up.
