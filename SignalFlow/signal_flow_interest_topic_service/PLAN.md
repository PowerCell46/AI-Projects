# SignalFlow Interest Topic Service — first phase plan

Each step below is self-contained: what it builds, what it decides, and the gate that says it's done.
**No step starts on a red or missing test, and each step ends green.**

---

## Context

Owns **interest topics**, their **categories**, and the **daily news** generated for each topic. Once a
day a job asks an AI model for the latest news on every topic (using the topic's `prompt`), stores it as
`TopicNews`, and an outbox poller publishes it to Kafka.

**Not this service's job:**
- **Subscriptions.** The gateway owns them (`subscriptions(user_id, interest_topic_id)`). This service never
  learns who is subscribed; the notification side joins the Kafka event on `interestTopicId`.
- **Authorization.** The gateway owns all access rules and will be the only caller. No Spring Security here.

**Not in this phase:** a real AI provider (a mock stands in behind `NewsGenerationService`), a read
endpoint for news, the downstream consumer of the Kafka event.

Stack mirrors `../signal_flow_api_gateway`: Spring Boot 4.2.0-M1, JDK 25, JPA + Postgres,
Testcontainers (never embedded fakes), layered `controller → service → repository`, services as
interface + `Impl`, package layout under `com.peter_gerdzhikov.signal_flow_interest_topic_service`
(`/configurations`, `/controllers`, `/DTOs/request`, `/DTOs/response`, `/entities`, `/exceptions`,
`/repositories`, `/services/interfaces`, `/services/implementations`, `/utilities`, plus `/jobs`).

---

## Data model

**`CommonEntity`** (`@MappedSuperclass`) — `UUID id` (`@UuidGenerator(TIME)`), `Instant createdAt`,
`Instant updatedAt`. Copied from the gateway.

**`Category extends CommonEntity`**
- `name` — not null, unique, **stored lowercased** (`@PrePersist`/`@PreUpdate`, like the gateway's
  `User.email`).

**`InterestTopic extends CommonEntity`**
- `name` — not null, `@Size(max = 100)`, globally unique, stored lowercased.
- `description` — nullable, `@Size(max = 1000)`, `TEXT`.
- `prompt` — not null, `@Size(max = 4000)`, `TEXT`. Sent **verbatim** to the AI; no service-side template.
- `category` — `@ManyToOne(fetch = LAZY, optional = false)`.

**`TopicNews extends CommonEntity`** — the news row *is* the outbox row.
- `interestTopic` — `@ManyToOne(fetch = LAZY, optional = false)`, `@OnDelete(CASCADE)`.
- `newsDate` — `LocalDate` in the job's zone. **Unique `(interest_topic_id, news_date)`.**
- `data` — `TEXT`, not null.
- `status` — `PENDING` → `SENT`, or `FAILED` after `max-attempts`.
- `attempts` — int, incremented per failed publish.
- `nextAttemptAt` — `Instant`; exponential backoff from `base-backoff` (1m, 2m, 4m, 8m).
- `lastError` — `TEXT`, nullable.
- `sentAt` — `Instant`, nullable.
- `@Index(status, next_attempt_at)` for the poller.

Schema via `ddl-auto=update` — no Flyway.

---

## Endpoints

All under `/api/v1`. Errors use the gateway's `ErrorResponseDTO` (`status`, `messages`, `timestamp`)
verbatim.

| Method | Path | Behaviour |
|---|---|---|
| POST | `/interest-topics` | 201 + body; 400 invalid; 404 unknown `categoryId`; 409 duplicate name |
| GET | `/interest-topics` | paged, default size 20, max 100, sorted by `name`; optional `?categoryId=` |
| PATCH | `/interest-topics/{id}` | partial (null = unchanged); 404 missing topic/category; 409 duplicate name |
| DELETE | `/interest-topics/{id}` | 204; cascades to its `TopicNews`; 404 missing |
| POST | `/categories` | 201 + body; 409 duplicate |
| GET | `/categories` | unpaged, sorted by `name` |
| PATCH | `/categories/{id}` | rename; 404 missing; 409 duplicate |
| DELETE | `/categories/{id}` | 204; 404 missing; **409 if referenced by any topic** |

Topic response embeds `categoryId` and `categoryName`, and never carries `prompt` (internal, sent only to the AI). No single-topic GET.

Added 2026-09-23 for the gateway's subscription reconciliation, outside `/api/v1` so no gateway route
forwards a client to it:

| Method | Path | Behaviour |
|---|---|---|
| POST | `/internal/v1/interest-topics/existing` | `{"ids": [...]}` → 200 `{"existingIds": [...]}`; 400 missing/malformed `ids`; 413 over 8 KB |
| POST | `/internal/v1/interest-topics/feed` | `{"ids", "mode": ALL\|INCLUDE\|EXCLUDE, "after", "size": 1–100}` → 200 `{"items", "nextCursor", "total", "matching"}`; 400 invalid body (added for the gateway's dashboard feed) |

---

## News generation job

- `@Scheduled(cron = "${app.news.cron:0 0 6 * * *}", zone = "${app.news.zone:UTC}")`.
- Pages through topics. Per topic: skip if a row for today's `newsDate` exists → call
  `NewsGenerationService` **outside any transaction** → save `TopicNews(PENDING)` in its own short
  transaction.
- One topic failing (AI error, timeout, unique-constraint race) is logged and skipped; the run continues.
- AI failures are **not retried** within the day. The outbox retries only the Kafka publish.
- `NewsGenerationService` has a single mock implementation this phase returning canned text per topic.

## Outbox publisher

- `@Scheduled(fixedDelayString = "${app.outbox.poll-interval:PT10S}")`; takes up to `batch-size` rows where
  `status = PENDING AND next_attempt_at <= now`. **The poller is the only publisher.**
- Sends synchronously (`send(...).get(timeout)`), then marks `SENT` + `sentAt`. On failure: `attempts++`,
  `lastError`, `nextAttemptAt = now + base-backoff × 2^(attempts-1)`; at `max-attempts` → `FAILED` + ERROR log.
- Delivery is **at-least-once** — consumers dedupe on `newsId`.

**Kafka contract**
- Topic `topic-news.generated`, created by a `NewTopic` bean (3 partitions, replication 1 in dev).
- Key `interestTopicId`. Producer `acks=all`, `enable.idempotence=true`.
- Value (JSON, Jackson 3):
```json
{
  "newsId": "uuid",
  "interestTopicId": "uuid",
  "topicName": "rust",
  "categoryName": "programming",
  "newsDate": "2026-09-22",
  "data": "...",
  "generatedAt": "2026-09-22T06:00:03.123Z"
}
```

---

## Configuration

| Property | Default |
|---|---|
| `server.port` | `${PORT:8081}` |
| `spring.datasource.*` | `DATASOURCE_URL/USERNAME/PASSWORD`, URL default `jdbc:postgresql://localhost:5433/signal_flow_topics` |
| `spring.kafka.bootstrap-servers` | `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` |
| `app.request.max-body-bytes` / `app.request.max-topic-body-bytes` | `8192` / `32768` (topic and category routes) |
| `app.news.cron` / `app.news.zone` | `0 0 6 * * *` / `UTC` |
| `app.outbox.poll-interval` | `PT10S` |
| `app.outbox.batch-size` | `50` |
| `app.outbox.max-attempts` | `5` |
| `app.outbox.base-backoff` | `PT1M` |
| `app.kafka.topic-news.partitions` | `3` |

`src/test/resources/application-test.properties` shortens poll interval and backoff so e2e tests don't wait.

Root `../docker-compose.yml` gains `postgres-topics` (own volume, own `TOPICS_POSTGRES_*` from `.env`, never
a committed credential) and a single-node KRaft `apache/kafka` broker. Ports commented out, like `postgres`.

---

## Steps

### Step 1 — Docs + infrastructure — **Done**
**Build:** `CLAUDE.md` (adapted from the gateway's), `DECISIONS.md` (seeded with the decisions flagged below),
`TESTING.md`. Compose: `postgres-topics` + `kafka`; `.env.example` updated. Pom: `spring-boot-testcontainers`,
`testcontainers-junit-jupiter`, `testcontainers-postgresql`, `testcontainers-kafka`, validation starter.
`support/AbstractIntegrationTest` with singleton Postgres + Kafka `@ServiceConnection` containers. Add a line
to the **gateway's** `PLAN.md`: enforce `ADMIN` on topic/category writes and authenticated on
`GET /interest-topics` when forwarding lands.
**Gate:** a context-loads test extending `AbstractIntegrationTest` is green. ✅

### Step 2 — Entities + repositories — **Done**
**Build:** `CommonEntity`, `Category`, `InterestTopic`, `TopicNews`, `NewsStatus`, their repositories
(including the outbox batch query and `existsByCategoryId`).
**Gate:** `@DataJpaTest` suites green — lowercasing on insert/update, both name uniques,
`(topic, news_date)` unique, cascade delete of news, outbox query returns only due `PENDING` rows. ✅

### Step 3 — E2E catalog — **Done**
**Build:** `CategoryControllerIntegrationTest`, `InterestTopicControllerIntegrationTest` — every scenario from
the endpoint table written, each `@Test` carrying `@Disabled("Enabled in step N")`. `ErrorResponseDTO` and
request/response DTOs as needed to compile. `TESTING.md` catalog filled in.
**Gate:** compiles; suites run all-skipped. ✅

### Step 4 — Categories — **Done**
**Build:** `CategoryService` + `Impl`, `CategoryController`, `GlobalExceptionHandler`, domain exceptions.
**Gate:** category block enabled and green; `CategoryServiceImplTest` green. ✅

### Step 5 — Interest topics — **Done**
**Build:** `InterestTopicService` + `Impl`, `InterestTopicController`, paging cap + `categoryId` filter.
**Gate:** topic block enabled and green; `InterestTopicServiceImplTest` green. ✅

### Step 6 — News generation — **Done**
**Build:** `NewsGenerationService` + mock `Impl`, `TopicNewsGenerationJob`, `@EnableScheduling`.
**Gate:** unit tests — per-topic failure isolation, skip-if-exists, unique-race swallowed; integration
test — one run yields exactly one `PENDING` row per topic, a second run yields none. ✅

### Step 7 — Outbox publisher — **Done**
**Build:** `TopicNewsEventDTO`, producer config, `NewTopic` bean, `TopicNewsOutboxPublisher`.
**Gate:** unit tests with mocked `KafkaTemplate` — success marks `SENT`, failure backs off exponentially,
5th failure marks `FAILED`; e2e — a real consumer receives the event with the right key and payload. ✅

### Step 8 — Audit — **Done**
**Build:** run `exploit-hunter` against the finished phase; record findings.
**Gate:** report written; each finding either patched (dated `DECISIONS.md` entry) or logged under Known gaps. ✅
Report: `exploit-report-2026-09-22.md`. 3 findings (1 Medium, 2 Low), all patched — see `DECISIONS.md`'s
step 8 entry.

---

## Known gaps (accepted, not oversights)

- ~~**No authentication or authorization.**~~ **Closed 2026-09-23:** the gateway now forwards all eight
  endpoints and enforces the roles (reads need a login, writes need ADMIN), stripping the caller's cookie and
  `Authorization` header before forwarding. This service still has no security of its own and trusts every
  caller, so it must stay reachable only from the gateway (internal Docker network).
- **Single instance assumed.** No scheduler locking; two instances would double-pay AI calls and could
  double-publish. **Trigger:** a second instance → ShedLock or `SKIP LOCKED`. *(DECISIONS.md)*
- **Deleting a topic drops its news, including unsent `PENDING` rows.** *(DECISIONS.md)* ~~It also orphans
  the gateway's subscriptions to it.~~ **Closed 2026-09-23:** the gateway's daily reconciliation job deletes
  subscriptions to topics this service no longer has, so an orphan lives until the next 05:00 run.
- ~~**Nothing lets the gateway confirm a topic exists.**~~ **Closed 2026-09-23:**
  `POST /internal/v1/interest-topics/existing` answers which of up to ~200 ids exist. It sits outside
  `/api/v1`, so no gateway route forwards a client to it.
- **`ddl-auto=update`, no versioned migrations.** **Trigger:** a second deployed environment or the first
  destructive schema change.
- **News rows kept forever.** **Trigger:** table size becomes a concern.
- **AI failures lose that day's news for the topic.** **Trigger:** the real provider proves flaky.

---

## Interview record

| # | Question | Answer |
|---|---|---|
| 1 | Does this service hold subscribers? | No — the gateway owns subscriptions; the `subscribers` field was a mistake |
| 2 | Database isolation? | Separate Postgres **container** (`postgres-topics`), own volume |
| 3 | Category identity? | `extends CommonEntity`; required on a topic |
| 4 | Case-insensitive uniqueness? | Store lowercased; `ddl-auto=update`, no Flyway |
| 5 | Topic field constraints? | `name` globally unique + lowercased; `prompt` sent verbatim, no template |
| 6 | AI provider? | Mock for now behind `NewsGenerationService` |
| 7 | TopicNews shape / outbox? | `extends CommonEntity`; 5 attempts, exponential backoff, then `FAILED`; no read endpoint; keep forever |
| 8 | Daily job semantics? | Configurable cron/zone; AI call outside tx; per-topic isolation; unique per day; no same-day AI retry |
| 9 | Multiple instances? | Single instance, no locking — record in DECISIONS.md |
| 10 | Kafka contract? | `topic-news.generated`, key `interestTopicId`, JSON with `newsId`, idempotent producer |
| 11 | Kafka infra / publish path? | KRaft compose broker, Testcontainers; only the poller publishes |
| 12 | Auth here? | None — the gateway is the only caller |
| 13 | Endpoint surface? | As tabled; no single-topic GET; categories unpaged |
| 14 | Topic delete semantics? | Hard delete, cascade news — record in DECISIONS.md |
| 15 | Runtime config? | Port 8081, gateway-style env vars, `app.*` properties, fast test profile |
| 16 | Phase breakdown? | Steps 1–8 above; also note route rules in the gateway's `PLAN.md` |
