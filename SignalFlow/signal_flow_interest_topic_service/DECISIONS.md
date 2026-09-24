# Decisions

Non-obvious calls made *during* implementation — the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Step 1 — single instance assumed, no scheduler locking

Both the daily news job and the outbox poller run unguarded `@Scheduled` methods. A second instance of
this service would double-pay AI calls and could double-publish the same day's news. Accepted for this
phase — nothing in `PLAN.md`'s scope runs more than one instance. **Trigger to revisit:** a second
instance lands → add ShedLock or a `SELECT ... FOR UPDATE SKIP LOCKED` claim on the job's row set.

## Step 1 — deleting a topic hard-deletes its `TopicNews`, including unsent `PENDING` rows

`InterestTopic`'s cascade delete (`@OnDelete(CASCADE)` on `TopicNews.interestTopic`) drops all of a
topic's news rows, sent or not, and leaves the gateway's `subscriptions` pointing at a topic id that no
longer exists — the gateway never learns the topic is gone. Accepted: this phase has no cross-service
notification mechanism and no soft-delete requirement. **Trigger to revisit:** cleanup needs the gateway
to react → publish an `interest-topic.deleted` event it consumes.

## Step 1 — `postgres-topics` is a separate container from the gateway's `postgres`, own volume, own credentials

Mirrors the gateway's own database isolation choice (`DECISIONS.md` there, "Database isolation?" in
`PLAN.md`'s interview record #2): no shared schema, no shared credentials, each service's Postgres can be
restarted, migrated or wiped independently. `TOPICS_POSTGRES_USER`/`_PASSWORD`/`_DB` in the root
`.env`/`.env.example` are separate variables from the gateway's `POSTGRES_*`, never a committed default.

## Step 1 — this service's own datasource env vars (`DATASOURCE_*`) default to port 5433, separate from compose's `POSTGRES_PORT`

Same reasoning as the gateway's step 1 entry ("app datasource env vars are separate from compose's
`POSTGRES_*`"): there's no `app` service in the root compose yet to wire `DATASOURCE_URL` from
`TOPICS_POSTGRES_*` automatically. `application.properties` reads its own `DATASOURCE_URL` defaulting to
`jdbc:postgresql://localhost:5433/signal_flow_topics` — port 5433, not 5432, so a dev running both
services' Postgres containers locally (each with `ports:` uncommented) never collides with the gateway's
`postgres` on 5432.

## Step 1 — Postgres image pinned to `postgres:18`, matching the gateway

Same call as the gateway's own step 1 entry: latest stable major as of this phase, no compatibility
constraint forces an older line.

## Step 1 — compose's `kafka` service uses the plain `apache/kafka` image, pinned to `4.3.1`

`PLAN.md` names `apache/kafka` (not `-native`) for the compose broker — a long-running dev service where
JVM startup time doesn't matter the way it does for a test that starts and tears down a container per
run. `4.3.1` is the current stable release (verified against Docker Hub's tag list; no `-rc` suffix, most
recently published stable tag). Single-node KRaft, combined broker+controller roles, a fixed `CLUSTER_ID`
so the formatted storage volume stays valid across restarts. Advertised listener is `localhost:9094`
matching `application.properties`' default `KAFKA_BOOTSTRAP_SERVERS` — same pattern as `postgres`/
`postgres-topics`: `ports:` commented out by default, uncomment to reach it from the host. Moved from
`9092` to `9094` (compose, this default and the healthcheck) as part of the gateway's topic-news
notification fan-out phase.

## Step 1 — `AbstractIntegrationTest`'s Kafka container is `apache/kafka-native`, pinned to `4.3.1`, not `3.9.0`

`testcontainers-kafka` (resolved at `2.0.5` via the Spring Boot 4.2.0-M1 BOM, same version the gateway's
own `testcontainers-postgresql` resolves to) starts `KafkaContainer` by overriding the image's `CMD` with
a deferred wait-loop, then copies a generated script into the running container that exports
`KAFKA_ADVERTISED_LISTENERS` (computed from the container's actual mapped port) before invoking
`/etc/kafka/docker/run`. Against `apache/kafka-native:3.9.0` this reproducibly failed —
`kafka.tools.StorageTool` rejected the config with `advertised.listeners cannot use the nonroutable
meta-address 0.0.0.0`, even though the generated script's exported value was well-formed
(`PLAINTEXT://localhost:<port>,BROKER://<containerId>:9093`, verified by `docker exec`-ing the running
container mid-test and reading `/tmp/testcontainers_start.sh` directly). Root cause not fully chased down
(likely a script-layout mismatch between this Kafka release and what `testcontainers-kafka` 2.0.5's
`KafkaHelper` targets) — reproduced consistently across several runs, then confirmed fixed by bumping the
image tag to `4.3.1` (the same version `latest` currently resolves to on Docker Hub) with no other change.
`4.3.1` chosen over staying on `latest` for the same reason every other image here is pinned: a moving
tag would make a green suite today fail unpredictably later. **If a future Kafka bump reintroduces this,**
re-check with `docker exec <container> cat /tmp/testcontainers_start.sh` while the test is running before
assuming it's the same bug.

## Step 3 — `InterestTopic` gets two request DTOs, `Category` gets one

`CreateInterestTopicRequestDTO` requires `name`/`prompt`/`categoryId` (`@NotBlank`/`@NotNull`);
`UpdateInterestTopicRequestDTO` makes every field optional to match PATCH's "null = unchanged"
semantics — reusing one DTO for both would force either the create-time required fields to become
optional or the patch semantics to disappear. `Category`'s only mutable field is `name`, and both its
create and rename endpoints require it, so `CategoryRequestDTO` is shared by both.

## Step 3 — `GET /interest-topics` returns Spring Data's default `Page` JSON envelope, no custom wrapper DTO

`PLAN.md` doesn't specify a pagination envelope shape, and `{"content": [...], "page": {"size", "number",
"totalElements", "totalPages"}}` is a framework default rather than a hand-rolled one, so
`InterestTopicControllerIntegrationTest` asserts on that shape via `jsonPath` instead of introducing a
`PagedResponseDTO`. **Trigger to revisit:** if the controller (step 5) needs `PagedModel`/HATEOAS links
instead, this assumption changes with it.

**Step 5 correction:** this envelope is *not* automatic on Boot 4.2.0-M1. `spring-boot-data-commons`'
`DataWebAutoConfiguration` carries a fixed `@EnableSpringDataWebSupport` (no explicit
`pageSerializationMode`), which defaults to `PageSerializationMode.DIRECT` — Jackson just serializes
`PageImpl`'s raw fields (`pageable`, `sort`, `offset`, ...) via its getters. The `SpringDataJackson3Configuration$PageModule`
bean (`jackson3pageModule`, confirmed present via `ApplicationContext.getBeanDefinitionNames()`) reads
`SpringDataWebSettings` at serialize time and only switches to the `{content, page}` shape when that
settings bean's mode is `VIA_DTO` — and that settings bean is built from
`DataWebProperties.pageable.serializationMode`, i.e. it **is** driven by a property, just not by the
annotation's import selection. Fix: `application.properties` sets
`spring.data.web.pageable.serialization-mode=via-dto` explicitly. Confirmed via a scratch `RestTestClient`
call printing the raw response body before and after setting the property — the `page.size`/`page.number`
keys are absent without it. **Trigger to revisit:** a Boot bump that adds `SpringDataWebAutoConfiguration`
with `VIA_DTO` as its own default would make this property redundant, not wrong — safe to leave in place
either way.

## Step 6 — `TopicNewsGenerationJob` parses `app.news.zone` into its own `ZoneId`, separate from `@Scheduled`'s `zone` attribute

`@Scheduled(cron = "...", zone = "${app.news.zone:UTC}")`'s `zone` only controls when the cron trigger
fires — it isn't injected into the method body. Computing `newsDate` (the value stored on `TopicNews` and
checked by the skip-if-exists guard) still needs the same zone, so the job's constructor takes
`@Value("${app.news.zone:UTC}") String newsZone` and converts it once to a `ZoneId` field, read via
`LocalDate.now(newsZone)`. Same property, two independent reads — not a shared bean, since nothing else
needs it yet. **Trigger to revisit:** the outbox publisher (step 7) or another job needs the same zone —
extract a `ZoneId`/`Clock` bean then instead of duplicating the `@Value` read a third time.

## Step 6 — `NewsGenerationServiceImpl`'s AI-call failures are caught as `Exception`, not a narrower type

The mock stands in for a real AI provider (`PLAN.md`'s "not in this phase"), so `TopicNewsGenerationJob`
has no concrete exception type to narrow to yet — a real client would throw its own checked/unchecked
errors, timeouts, etc. Catching `Exception` around just the `generate(...)` call (not the surrounding
save) is intentional per `PLAN.md`'s "one topic failing (AI error, timeout, ...) is logged and skipped".
**Trigger to revisit:** a real `NewsGenerationService` implementation lands — narrow the catch to whatever
that client actually throws.

## Step 1 — `AbstractIntegrationTest` uses `org.testcontainers.postgresql.PostgreSQLContainer`, not the deprecated `org.testcontainers.containers.PostgreSQLContainer`

`testcontainers-postgresql` 2.0.5 ships both: the gateway's `AbstractPostgresIntegrationTest` still
imports the deprecated `org.testcontainers.containers` alias (it predates this rename and nobody's
revisited it), which now compiles with an unavoidable deprecation warning
(`-Xlint:deprecation` confirmed it). This service's version imports the non-deprecated
`org.testcontainers.postgresql.PostgreSQLContainer` instead — same behaviour, no warning, and a
concrete (non-generic) class, so the field is declared as `PostgreSQLContainer`, not `PostgreSQLContainer<?>`.
Not backported to the gateway — out of scope for this service's step 1.

## Step 7 — `TopicNewsEventDTO` gets its own `/DTOs/event` package, not `/request` or `/response`

`java-code-style`'s DTO rule only names two buckets (an inbound request body, an outbound HTTP response),
and this DTO is neither — it's the Kafka message payload, never touched by `DispatcherServlet`. Forcing
it into `/response` would misdescribe it (nothing ever returns it from a controller) and would read as
if the outbox publisher were an HTTP concern. A third, equally-named sibling package keeps the "say which
one it is in the name and the path" spirit of the rule instead of bending an existing bucket to fit.

## Step 7 — outbox batch query switches to `@Query` with `JOIN FETCH`, same method name

`TopicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc` used to be a plain
derived query. The event payload needs `topic.name` and `topic.category.name`, both `LAZY`, and the
publisher reads them *after* the repository call's own transaction has closed (see the next entry) — so
they must already be initialised, not lazy proxies, by the time `due.forEach` gets to them. Switched the
same method to a `@Query` JOIN-FETCHing `interestTopic` and its `category`; kept the exact method name and
signature so `TopicNewsRepositoryIntegrationTest`'s existing calls didn't need to change. Both joins are
`@ManyToOne` (to-one), so pairing `JOIN FETCH` with `Pageable` doesn't hit the classic collection-fetch/
pagination-in-memory problem — Hibernate can still paginate at the SQL level.

## Step 7 — `TopicNewsOutboxPublisher` has no `@Transactional` anywhere, same as `TopicNewsGenerationJob`

Each step (fetch-due-batch, the blocking Kafka `send().get(timeout)`, mark-sent-or-failed) runs as its own
repository call, relying on `SimpleJpaRepository`'s own per-call transaction rather than wrapping the
publisher's methods. Deliberate, not an oversight: the batch is fetched once as detached, join-fetched
entities; `saveAndFlush` after mutating one back to `SENT`/`FAILED`/retry state re-attaches it via merge
(the entity carries a real id, so this is a JPA merge, not a fresh insert). Wrapping any of this in the
publisher's own `@Transactional` would hit Spring AOP's self-invocation problem the moment one method
calls another on `this` (the proxy is bypassed, no new transaction opens) — and would also hold a
transaction open across the blocking network call, which `TopicNewsGenerationJob`'s own decision (AI call
outside any transaction) already ruled out for the same reason. Per PLAN, delivery is at-least-once and
the poller is single-instance (`DECISIONS.md`'s step 1 entry) — this design doesn't need cross-step
atomicity, only that each individual DB write commits.

## Step 7 — `generatedAt` on the Kafka event is `TopicNews.createdAt`, not the publish-time `Instant.now()`

The contract's `generatedAt` documents when the AI produced the news, which is `TopicNews.createdAt` (set
by `TopicNewsGenerationJob` at insert time), not when the outbox happened to get around to publishing it —
those two can diverge by minutes on a retried publish. Using `Instant.now()` at send time would silently
relabel a retried message with a later "generated" timestamp each time it's retried, which is wrong.

## Step 7 — every `@SpringBootTest` disables both `@Scheduled` jobs by default via `AbstractIntegrationTest`

`PLAN.md`'s Configuration table sketched a `src/test/resources/application-test.properties` for shortening
the outbox's timing in e2e tests. Building the actual e2e test surfaced a real bug in that plan: every
`@SpringBootTest` in this suite shares the *same* Testcontainers Postgres (`AbstractIntegrationTest`'s
static singleton), and Spring caches each distinct context (keyed by its property/profile set) for the
whole surefire run rather than tearing it down between test classes. Once the outbox publisher existed,
every one of those cached contexts carried its own live, scheduled `TopicNewsOutboxPublisher` polling the
*shared* table — so a context left over from an unrelated test (e.g. `TopicNewsGenerationJobIntegrationTest`,
production defaults, 10s poll) kept racing whichever other test's rows were currently due, throwing
`ObjectOptimisticLockingFailureException` when it tried to mark a row another context had already deleted
or changed. Confirmed by running the new e2e test in isolation (`-Dtest=...`): it passed cleanly with no
other context alive to race it, and failed inside the full suite with exactly that exception.

Fix: `AbstractIntegrationTest` now carries `@TestPropertySource(properties = {"app.news.cron=-",
"app.outbox.poll-interval=PT24H"})`, disabling both by default for every test that extends it (the cron
`"-"` value is Spring's documented way to disable a cron trigger entirely; the outbox has no such value
for `fixedDelayString`, so a 24h delay stands in). `TopicNewsOutboxPublisherIntegrationTest` re-enables
just its own with a local `@TestPropertySource(properties = {"app.outbox.poll-interval=PT1S",
"app.outbox.base-backoff=PT1S"})` — a subclass's inlined `@TestPropertySource` properties override the
same keys inherited from a superclass, so this doesn't need a profile or a second properties file. A
`src/test/resources/application-test.properties` (profile-scoped) was tried first and reverted: it wasn't
the cause of the race (that file layers correctly, doesn't shadow anything), but once the base-class-level
disable was needed anyway to fix the race, keeping a second file *and* mechanism for the same two
properties was redundant, not additive — dropped in favour of one mechanism, on the leaf test only.
**Do not** add a plain `src/test/resources/application.properties`: it sits at the exact same classpath
location as the main `src/main/resources/application.properties`, and Spring Boot loads only the first one
found on the classpath — it does not merge the two. Adding one (even briefly, while chasing this) silently
shadowed datasource/JPA/Kafka config for every test until reverted; confirmed via `target/test-classes`
still holding a stale copy after deleting the source, which `mvn clean` cleared.

## Step 7 — `AbstractIntegrationTest.KAFKA`'s bootstrap address isn't the `spring.kafka.bootstrap-servers` property

`@ServiceConnection` wires the running container into Boot's Kafka autoconfiguration through a
`KafkaConnectionDetails` bean, not by setting the `spring.kafka.bootstrap-servers` property in the
environment — so a test class reading `@Value("${spring.kafka.bootstrap-servers}")` for its own,
manually-built `KafkaConsumer` gets back the static property default (`localhost:9094`), not the
container's real mapped port, and that consumer silently fails to connect. Added
`AbstractIntegrationTest.kafkaBootstrapServers()` (reads `KAFKA.getBootstrapServers()` directly) for any
test that needs a real Kafka client of its own, instead of `@Value`.

## Step 8 — 2026-09-22 — `exploit-hunter` audit findings, all patched

Full report: `exploit-report-2026-09-22.md`. Three findings, all patched rather than deferred:

- **No request body size limit (Medium).** Nothing capped raw JSON body size before Jackson parsed
  it into memory, unlike the sibling gateway service's `RequestBodySizeLimitFilter`. Ported that
  exact filter/wrapper/exception pattern verbatim (`configurations/RequestBodySizeLimitFilter`,
  `utilities/BodySizeLimitingRequestWrapper`, `utilities/ErrorResponseWriter`,
  `exceptions/RequestBodyTooLargeException`), mapped to 413 in `GlobalExceptionHandler`, capped via
  `app.request.max-body-bytes` (`MAX_REQUEST_BODY_BYTES:8192`).
- **`CategoryRequestDTO.name` had no length cap (Low).** Only `InterestTopic`'s DTOs carried
  `@Size(max = 100)`; an oversized `Category.name` fell through to a generic 409 from
  `DataIntegrityViolationException` instead of a 400. Added `@Size(max = 100)` to
  `CategoryRequestDTO.name` and mirrored `InterestTopic.name`'s entity-level
  `@Size(max = 100)` + `@Column(length = 100)` onto `Category.name`, which used to rely on JPA's
  implicit 255-character default.
- **Log forging via unescaped CR/LF in user-supplied names (Low).** A name containing `\n`/`\r`
  could inject a fabricated log line. Added `utilities/LogSanitizer` (strips `[\r\n]`) and applied it
  to the three log lines that interpolate a user-supplied name directly
  (`CategoryServiceImpl.create`/`.rename`, `InterestTopicServiceImpl.create`) — the other log lines in
  this codebase only ever log UUIDs or fixed values, not user input, so were left alone.

## Step 7 — `TopicNewsOutboxPublisher.publishDue()` isolates each row's failure, mirroring `TopicNewsGenerationJob`

The first cut let one row's unexpected exception (anything past the deliberate send-failure handling —
e.g. a `saveAndFlush` failing because the row was concurrently deleted) escape `due.forEach(...)`,
aborting the rest of that batch. `publishDue()` now calls each row through a `publishOneSafely` wrapper
that catches and logs anything unexpected, so one bad row can't block its neighbours in the same poll —
same "one failing, the run continues" shape `TopicNewsGenerationJob` already uses per topic.

## 2026-09-23 — Existence endpoint and per-route body cap (gateway reconciliation phase, step 1)

- **`POST /internal/v1/interest-topics/existing` gets its own controller** (`InternalInterestTopicController`)
  rather than a second mapping on `InterestTopicController`, whose class-level `/api/v1/interest-topics`
  would otherwise have to be split per method. The service short-circuits an empty `ids` list instead of
  running `IN ()`. Duplicate ids come back once, since the query selects existing rows. No `@Size` on
  `ids`: the 8 KB body cap already bounds a request to about 200 UUIDs.
- **Two body caps, mirroring the gateway.** `/api/v1/categories/**` and `/api/v1/interest-topics/**` get
  `app.request.max-topic-body-bytes` (32 KB, same as the gateway), everything else keeps
  `app.request.max-body-bytes` (8 KB). Matched with Spring's `PathPattern` on the request URI, since this
  service has no Spring Security for the gateway's `PathPatternRequestMatcher`. Closes the half of gateway
  exploit report 2026-09-23 #3 that the gateway alone couldn't fix.

## 2026-09-23 — Feed endpoint for the gateway's dashboard, and `prompt` leaves every response

- **`POST /internal/v1/interest-topics/feed` pages with a keyset cursor on `name`, not an offset.** `name`
  is unique, so `name > :after ORDER BY name` is exact. An offset would skip a card whenever the gateway's
  user unsubscribes mid-list in a filtered view. The query fetches `size + 1` rows to know whether more
  follow, so there's no count query per page. A null `after` becomes `""`, which sorts before every stored
  name, so no null parameter is bound.
- **The service sees only ids and a mode (`ALL`/`INCLUDE`/`EXCLUDE`)**, never "subscriptions". An empty id
  set short-circuits in Java instead of sending `IN ()`/`NOT IN ()`. `total` is `count()` and `matching`
  reuses `findExistingIds`, so the gateway's counts ignore subscriptions to deleted topics that are still
  awaiting reconciliation.
- **The result travels as a Spring Data `Slice`**, not a custom holder. The controller turns `hasNext()`
  into `nextCursor` (the last item's name).
- **`prompt` is gone from `InterestTopicResponseDTO`**, so no endpoint returns it, admin writes included.
  It's internal, input to the AI only. Admins still set it via POST/PATCH. Mapping moved to
  `utilities/InterestTopicResponseMapper`, shared by both controllers.

## OpenRouter phase step 1 — `spring-boot-starter-restclient` had to be added explicitly

Unlike the gateway, which gets `RestClient.Builder` autoconfigured transitively through
`spring-cloud-starter-gateway-server-webmvc`, this service's `spring-boot-starter-webmvc` alone doesn't pull
it in on 4.2.0-M1: `OpenRouterClientConfiguration` failed to compile with `JdkClientHttpRequestFactoryBuilder`
and `ClientHttpRequestFactoryBuilderCustomizer` unresolved until `spring-boot-starter-restclient` (present in
the 4.2.0-M1 BOM under that exact artifactId) was added as its own dependency.

## OpenRouter phase step 6 (2026-09-24) — exploit-hunter finding, patched

Full report: `exploit-report-2026-09-24-openrouter-phase.md`. One finding, patched:

- **Log forging via unescaped CR/LF in OpenRouter's `error.message` (Low).** `NewsGenerationServiceImpl
  .providerMessage` embedded the provider's error message verbatim into `NewsGenerationFailedException`,
  which the job then logs with `log.error(..., e)` - the same category as the earlier user-supplied-name
  finding, just reached through an external field instead of a request field. Patched by running the
  extracted message through the existing `utilities/LogSanitizer.sanitize` before it reaches the
  exception, mirroring the earlier fix rather than introducing a second sanitizer.

## OpenRouter phase step 7 — four smaller calls worth recording together

- **The HTTP/1.1 pin on the OpenRouter client** (`OpenRouterClientConfiguration.http1OnlyOpenRouterClient`)
  is copied verbatim from the gateway's own pin on its upstream client, for the same reason: the JDK
  `HttpClient` otherwise attempts an h2c upgrade over plain `http://`, and WireMock's Jetty answers that
  with a reset stream. Real OpenRouter is `https://`, so the pin is inert against it in prod, but it's
  needed for `AbstractOpenRouterIntegrationTest`'s WireMock container regardless.
- **The dummy key and unreachable base URL in `AbstractIntegrationTest`'s defaults** exist so a test that
  never extends `AbstractOpenRouterIntegrationTest` can't reach the real OpenRouter and spend money if it
  accidentally exercises `NewsGenerationServiceImpl` - `http://localhost:1` fails a connection attempt
  immediately rather than timing out, and the 1s test read timeout (also overridden there) keeps a test
  that does reach a slow stub fast.
- **`DTOs/openrouter/` is a third DTO folder next to `request/`/`response/`**, same precedent as the
  existing `DTOs/event/`: OpenRouter's wire contract is neither this service's own inbound request shape
  nor its own outbound response shape, so forcing it into either package would blur that distinction.
- **The system prompt lives as a `private static final String` text block in `NewsGenerationServiceImpl`**,
  not in a properties file or a template resource, since it's fixed application logic (the format and
  item-count rules), not environment-specific configuration, and it's read alongside the code that uses
  it.

## OpenRouter phase step 5 (2026-09-24) — manual smoke check, passed

Ran the full pipeline against a real OpenRouter key with a credit limit set: gateway + mail service +
this service, all three `mvn spring-boot:run` against `docker compose`'s Postgres/Kafka/Redis/Mailpit.
Machine already had unrelated containers bound to the default `5432` and `9094` host ports, so this run
used `5434`/`9095` instead via env var overrides on each app (never edited the checked-in `.env` values,
only exported overrides for the run, then reverted `docker-compose.yml`'s temporarily-uncommented ports
back to their original commented state afterward) - the ports themselves aren't load-bearing, just
whatever's free on the machine running the check. One real topic (`rust programming language`), one
subscriber (the seeded admin): a genuine `topic_news` row landed with clean HTML and three working
`https` source links (`blog.rust-lang.org`, `rustfoundation.org`, `releases.rs`), and Mailpit received one
email whose rendered body matched - allowlisted tags only, both `rel="noopener noreferrer"` tokens present
on every link. No code changes came out of this step; it exists to catch what the WireMock-based suite
structurally can't (a real provider's actual response shape).
