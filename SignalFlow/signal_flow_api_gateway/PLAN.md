# SignalFlow API Gateway — what's left

The **auth** (2026-09-21), **subscriptions** (2026-09-22), **interest-topic routing** (2026-09-23) and
**subscription reconciliation** (2026-09-23) phases have shipped and are green. Their design, step-by-step
gates and `/grill-me` interview records are no longer carried here. Read them from git history
(`git log -p -- PLAN.md`). Calls made during implementation live in `DECISIONS.md`, the e2e catalog in
`TESTING.md`, and the audits in `exploit-report-2026-09-22-subscriptions.md`,
`exploit-report-2026-09-23-topic-routing.md` and `exploit-report-2026-09-23-reconciliation.md`. Each report's
status note records which findings were fixed. The rest are open by decision and appear under *Accepted
gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Next phase — topic-news notification fan-out

Consume `topic-news.generated` (produced by `signal_flow_interest_topic_service`'s
`TopicNewsOutboxPublisher`) and, for every **enabled** subscriber of the event's `interestTopicId`, publish
one message to `topic-news.notification-requested` for a future email service to act on. Replaces the
by-topic fan-out read path under *Build* below: it stays inside the gateway, so no service-to-service HTTP
auth is needed.

### Design

**Delivery — at-least-once.** The offset is committed only after all N sends for a record are acked. A
failure mid-fan-out retries the whole record, so earlier subscribers get a duplicate: a duplicate is
acceptable, a skipped subscriber is not. The email consumer dedupes on `(newsId, userId)`, which it needs
anyway since upstream is at-least-once too. No Kafka transactions, no gateway outbox.

**Inbound** — `DTOs/event/TopicNewsEventDTO`, a copy of the topic service's contract (no shared lib):
`newsId` (UUID), `interestTopicId` (UUID), `topicName`, `categoryName`, `newsDate` (LocalDate), `data`,
`generatedAt` (Instant). Key `interestTopicId`, JSON via Jackson 3, no type headers.

**Outbound** — `DTOs/event/TopicNewsNotificationEventDTO`: all 7 inbound fields + `userId` (UUID, the
stable dedupe key — email can change) + `emailAddress`.
- Topic `topic-news.notification-requested`, created by the gateway's own `NewTopic` bean
  (`KafkaTopicConfig`): 3 partitions, replication 1, both configurable.
- Key **`userId`**: per-user ordering, and one large topic's fan-out spreads across all partitions.
- Producer mirrors the topic service: `StringSerializer` key, `JacksonJsonSerializer` value, `acks=all`,
  `enable.idempotence=true`, `spring.json.add.type.headers=false`.

**Subscriber lookup.** Keyset batches of **500** (configurable), selecting only `user.id` + `user.email`
via a JPQL projection over `Subscription` joined to `User`, filtered on `interestTopicId` and
`user.enabled = true`. No entity loading, so `Subscription.user` stays `LAZY` (its `// TODO` is removed as
resolved). Disabled users are skipped. Zero subscribers → no sends, record committed.

**Consumer.**
- Group `signal-flow-api-gateway`, `auto-offset-reset=earliest` (no news missed on first start),
  concurrency **3** (one per inbound partition, configurable).
- Value deserializer wrapped in `ErrorHandlingDeserializer`, so bad JSON doesn't loop.
- `DefaultErrorHandler`: 3 retries with exponential backoff (~1s, 2s, 4s), then
  `DeadLetterPublishingRecoverer` → **`topic-news.generated.DLT`**, logged at ERROR. Deserialization
  failures skip retries and go straight to the DLT. No replay tooling.

**Layout.**
- `/listeners/TopicNewsListener`: thin `@KafkaListener`; delegates, holds no logic.
- `TopicNewsNotificationService` (`/services/interfaces`) + `TopicNewsNotificationServiceImpl`
  (`/services/implementations`): batch-load subscribers, send one message each, block on each send's ack
  (`send(...).get(timeout)`) so a failure surfaces to the error handler.
- `/DTOs/event/`: both event DTOs.
- `CLAUDE.md`'s package list gains `/listeners` and `/DTOs/event`.

**Local Kafka — `9094` everywhere.** Gateway default `${KAFKA_BOOTSTRAP_SERVERS:localhost:9094}`. Root
`docker-compose.yml`'s `kafka` listener, advertised listener, healthcheck and commented port mapping
move from 9092 to 9094, matching the topic service's existing default. The topic service's `DECISIONS.md`
9092 mention is corrected to match.

### Steps

1. **Infra + docs.** Pom: `spring-boot-starter-kafka`, `spring-boot-starter-kafka-test`,
   `testcontainers-kafka`. 9092 → 9094 (compose + topic service `DECISIONS.md`). Kafka properties
   (producer, consumer, topic names, batch size, concurrency). `KafkaTopicConfig`. A test base with a
   singleton `apache/kafka-native:4.3.1` `@ServiceConnection` container next to Postgres (4.3.1 per the
   topic service's `DECISIONS.md`). `CLAUDE.md` package list.
   **Gate:** a context-loads test on the Kafka base is green.
2. **Repository.** Keyset batch query of enabled subscribers' `(userId, email)` for one `interestTopicId`.
   **Gate:** `SubscriptionRepositoryIntegrationTest` covers: only id + email returned, disabled users
   excluded, other topics excluded, paging correct across more than one batch.
3. **DTOs + service.** Both event DTOs, `TopicNewsNotificationService` + `Impl`.
   **Gate:** unit test with mocked `KafkaTemplate`: one send per enabled subscriber, key = `userId`, all 9
   fields mapped; multiple batches walked; zero subscribers → no sends; a failed send propagates.
4. **Listener + error handling.** `TopicNewsListener`, consumer config (group, `earliest`, concurrency 3),
   `ErrorHandlingDeserializer`, `DefaultErrorHandler` + DLT recoverer. Remove the TODO on
   `Subscription.user`.
   **Gate:** e2e: a `topic-news.generated` record in real Kafka → one `topic-news.notification-requested`
   record per enabled subscriber with the right key and payload; malformed JSON lands on
   `topic-news.generated.DLT`.
5. **Docs.** `PLAN.md`: close the by-topic fan-out item, add the accepted gaps below. `DECISIONS.md`: the
   calls from this interview. `TESTING.md` untouched (it covers HTTP suites only).
   **Gate:** full `mvn verify` green.
6. **Audit.** `exploit-hunter` on the finished phase.
   **Gate:** report written; each finding patched (dated `DECISIONS.md` entry) or logged under *Accepted gaps*.

### Accepted gaps this phase adds (move to *Accepted gaps* in step 5)

- **User emails travel through Kafka in plaintext.** Fine on a private network. **Trigger:** Kafka is shared
  or exposed beyond the internal network → TLS/SASL, or send `userId` only and let the consumer resolve
  the email.
- **DLT'd records are only logged, no replay tool.** **Trigger:** the first record that needs replaying.
- **Duplicates on a mid-fan-out retry.** By design (at-least-once); the email consumer must dedupe on
  `(newsId, userId)`.

### `/grill-me` record (2026-09-24)

| # | Question | Answer |
|---|---|---|
| 1 | Delivery guarantee for the N-way fan-out? | At-least-once; duplicates beat skips; consumer dedupes |
| 2 | Extra fields beyond the event + email? | `userId` (stable dedupe key) |
| 3 | Notify disabled users? | No, skip them |
| 4 | Outbound topic name? | `topic-news.notification-requested` |
| 5 | Outbound message key? | `userId` |
| 6 | Who creates the outbound topic? | The gateway, `NewTopic` bean, 3 partitions |
| 7 | Load subscribers how? | Keyset batches of 500, id + email projection |
| 8 | Persistent failure? | 3 retries with backoff, then `topic-news.generated.DLT` |
| 9 | Code layout? | `/listeners`, service interface + impl, `/DTOs/event`; update `CLAUDE.md` |
| 10 | First-start offset? | `earliest` |
| 11 | Local bootstrap port? | `9094` everywhere, replacing all 9092 |
| 12 | Tests? | Repository IT, service unit, e2e fan-out, DLT e2e |
| 13 | Listener concurrency? | 3 |
| 14 | `Subscription.user` TODO? | Stays `LAZY`, TODO removed |
| 15 | Docs? | Close by-topic item; add plaintext-email + no-DLT-replay gaps; `DECISIONS.md` entries |

---

## Fix — real defects, ranked

1. **A `null` in the topic service's `existingIds` aborts the reconciliation with an uncaught NPE**
   (`exploit-report-2026-09-23-reconciliation.md` #1, Low). It fails safe, since nothing is wrongly deleted,
   but it skips the stop-and-warn path. Fix: the client throws `InterestTopicLookupFailedException` on a null
   element. It must not filter the nulls out, because that would delete subscriptions.
2. **`app.subscriptions.reconciliation.batch-size` above ~208 silently disables the job**, because every lookup
   then gets 413 from the topic service's 8 KB internal cap (same report, #2, Low). Fix: reject a batch size
   over 200 at startup, and optionally log a 4xx-caused stop at ERROR.

(Long non-Latin topic prompts getting 413 from the topic service, exploit report 2026-09-23 #3, was fixed
2026-09-23 alongside the reconciliation phase's existence endpoint.)

## Build — deferred by decision, not oversight

- **The by-topic fan-out read path.** "All subscribers of topic X" is what the daily notification job
  wants, but exposing it means service-to-service auth, and this gateway only knows how to authenticate a
  human holding a `SameSite=Strict` cookie. A phase of its own; the index it needs already exists (the
  unique constraint leads with `interest_topic_id` for exactly this reason). Subscriber counts stay
  unexposed until then, so nothing leaks yet.
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
