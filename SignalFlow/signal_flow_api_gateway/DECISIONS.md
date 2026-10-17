# Decisions

Non-obvious calls made *during* implementation — the ones that would be hard to re-derive from the
code alone. Design settled up front lived in `PLAN.md` (deleted; see `git log -p -- PLAN.md`); conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Setup and platform

- **`app.jwt.secret` has no default.** `application.properties` reads `${JWT_SECRET}`, so the app refuses to
  start without it, and a secret under 32 bytes is rejected too (a shorter HS256 key can be brute-forced offline
  from one issued cookie). `mvn spring-boot:run` and `mvn test` need `JWT_SECRET` exported; `.env` is not read
  into the app process.
- **App datasource vars are `DATASOURCE_*`, separate from compose's `POSTGRES_*`**, defaulting to `localhost:5432`.
- **The Postgres volume mounts at `/var/lib/postgresql`, not `.../data`.** The 18+ image keeps data in a
  versioned subdirectory and refuses to start against the old mount path.
- **Boot is on 4.1.1, not 4.2.0-M1**, because `spring-cloud-gateway-server-webmvc` has no train for the milestone
  (Spring Cloud 2025.1.3 covers `<4.2.0-M1`). Chosen over proxying by hand. **Revert** to 4.2 GA once Spring Cloud
  2026.0 ships. The topic service stays on 4.2.0-M1.
- **Boot 4 relocations to remember** (verify 3.x-era APIs before relying on them): `@DataJpaTest` and
  `@AutoConfigureTestDatabase` moved to new `*-test` artifacts and packages; Jackson 3 is `tools.jackson.*`;
  `@AutoConfigureMockMvc` is `org.springframework.boot.webmvc.test.autoconfigure`; Spring 7 renamed
  `HttpStatus.PAYLOAD_TOO_LARGE` to `CONTENT_TOO_LARGE` (the old constant compiles but is a different enum entry).
- **`AuthControllerIntegrationTest` needs `@AutoConfigureMockMvc` next to `@AutoConfigureRestTestClient`.**
  Without a `MockMvc` bean, `RestTestClient` binds to the application context and skips the servlet filters, so
  Spring Security never runs and every 401 assertion silently becomes a 404.
- **`RestTestClient` has no `expectBodyList`**; read the body as an array instead.
- **Test profile (`application-test.properties`) shrinks what would slow tests**: bcrypt strength 4, subscription
  cap 3 (the e2e suite reads it back with `@Value`), reconciliation cron `-`, and an oversized outbox poll delay
  (`fixedDelayString` has no "never" sentinel like `cron=-`) so a live poller can't delete the rows a test asserts on.

## Auth

- **`User` lowercases its own email** in a `@PrePersist`/`@PreUpdate` callback, so the invariant holds for every
  write path. Email and topic/category names are lowercased with `Locale.ROOT`, so a Turkish or Azeri default
  locale can't turn `I` into `ı`.
- **`CookieFactory` and `CookieBearerTokenResolver` are plain classes in `/utilities`**, not a `Service`/`ServiceImpl`
  pair: no business logic, only formatting from config. The resolver isn't Spring-managed (`SecurityConfiguration`
  builds it with `new`).
- **The JWT decoder requires `sub`/`email`/`role`/`exp` explicitly.** `JwtValidators.createDefault()` still passes a
  token with no `exp`, so a `JwtClaimValidator` per claim is composed on top. `sub` must also parse as a UUID, so a
  malformed subject fails as a clean 401 at the decoder rather than a 500 in every controller.
- **`createdAt` is a JWT claim (ISO-8601 string), not a DB read in `/me`**, because authenticated requests read no
  database. A string, not epoch seconds, keeps Postgres' microsecond precision. It is not in the required-claims
  validator: missing it nulls one response field and bypasses nothing.
- **Logout is best-effort.** The JWT is never revoked server-side; its test only proves a browser that dropped the
  cleared cookie can't authenticate.
- **`CookieBearerTokenResolver` skips public matchers** (`PUBLIC_MATCHERS`, shared with `SecurityConfiguration`).
  `BearerTokenAuthenticationFilter` runs before `permitAll()`, so a stale `access_token` cookie would otherwise 401
  login, register and logout until the cookie jar was cleared.
- **Login burns a bcrypt `encode` on the unknown-email path**, so response time doesn't reveal whether an email is
  registered. Proven by a unit test (`encode` called, `matches` never), not a flaky wall-clock e2e assertion.
- **Login and register DTOs share the same constraints**: email `@Size(max = 254)` plus a `@Pattern` requiring a
  TLD (`@Email` accepts `user@localhost`), password `@Size(min = 8, max = 72)` plus at least one lower, upper and
  digit. A deliberate reversal of the NIST advice against composition rules; no symbol requirement.
- **Admin seeding is `DatabaseLoader` (a `CommandLineRunner`) delegating to `AuthService.ensureAdminExists`.**
  `ADMIN_EMAIL`/`ADMIN_PASSWORD` are optional: unset logs a `WARN` and does nothing. It is a separate method from
  `register`, which hard-codes `Role.USER`, and checks by role rather than email.
- **Login and register are rate limited per client address** (`AuthRateLimitFilter`, 20 per minute by default, 429
  with `Retry-After`), because both cost a bcrypt hash and are unauthenticated. Counters are in memory, so the
  limit holds per instance only. **Trigger:** more than one gateway instance (needs Redis or a proxy-level limit).
- **The client address is the `X-Forwarded-For` entry 1 from the right**: Caddy overwrites what a client sends and
  the frontend nginx appends Caddy's, so the left entries are client-controlled. If the proxy chain changes, change
  `CLIENT_ADDRESS_FORWARDED_FOR_INDEX_FROM_RIGHT`. With too few entries the socket address is used.
- **An admin deletes a user with a hard delete.** `Subscription.user` has `@OnDelete(CASCADE)`, and the user's
  `notification_outbox` rows (`user_id`, no foreign key) are deleted in the same transaction. Soft delete would keep
  the email taken. Only `USER` accounts can be deleted; an `ADMIN` target, including the caller, answers 403. A
  deleted user's token keeps working until it expires (accepted); subscribe then answers 401
  (`AuthenticatedUserNotFoundException`) instead of a neutral 409 from the foreign key.

## Error handling and request limits

- **`handleExceptionInternal` returns a fixed `"The request could not be processed."`** and logs the real message,
  so a framework exception added by a future Spring version never leaks. Useful ones (`405`, `415`, `404`) are
  overridden with curated text and pass the inherited `headers` through (keeps the `Allow` header on a 405).
- **`handleDataIntegrityViolation` is a neutral 409 fallback.** Each service translates its own constraint
  (`SubscriptionServiceImpl.subscribe`, `AuthServiceImpl.register`), so a second unique constraint can't produce the
  wrong message.
- **The raw body cap is a filter ahead of Spring Security** (`RequestBodySizeLimitFilter`, `HIGHEST_PRECEDENCE`),
  because register and login are `permitAll` and an oversized anonymous body must die before parsing or bcrypt. It
  answers 413 on `Content-Length`, and `BodySizeLimitingRequestWrapper` counts bytes for chunked bodies. Two paths,
  one 413 contract. Tomcat's `maxPostSize` and the multipart limit don't cover JSON bodies.
- **The topic routes get their own cap, 32 KB** (`app.request.max-topic-body-bytes`; default 8 KB elsewhere). It is
  sized for the topic service's worst case (4000 + 1000 + 100 characters, each escaped as 6 bytes, about 30.6 KB),
  chosen over raising the global cap, which would also widen the anonymous bodies.
- **On a forwarded route the oversized-body exception arrives wrapped in a `ResourceAccessException`**, so
  `handleUpstreamFailure` checks for `RequestBodyTooLargeException` first and answers the same 413. MockMvc always
  knows the content length, so a chunked request is only unit-tested on the observed exception chain.

## Subscriptions

- **The cap race is closed with a row lock on the user**: `subscribe` is `@Transactional` and starts with
  `UserRepository.lockById` (`PESSIMISTIC_WRITE` on an id projection, so the row is locked but never read).
  Chosen over a conditional `INSERT ... WHERE count < cap` to keep the cap in Java. `save` became `saveAndFlush`,
  because the Java-generated UUID defers the INSERT to commit, outside the `catch` that maps the 409.
  `SubscriptionServiceImplConcurrencyIntegrationTest` (real Postgres, 8 concurrent subscribes at cap 3) is the proof;
  mocks can't produce the stale count.
- **`subscribe` tells the unique violation apart by SQLState `23505`.** Any other violation (in practice the
  `user_id` foreign key) is rethrown and lands on the neutral 409 handler instead of "already subscribed".
- **`GET /api/v1/subscriptions` returns a plain newest-first list**, no paging and no envelope: the per-user cap
  bounds it. An empty result is `200` with `[]`.
- **`interestTopicId` is not validated against a real topic on subscribe.** A synchronous call to the topic service
  would add latency and a failure mode, and the nightly reconciliation deletes dangling subscriptions within about
  24 hours, invisible to users.
- **The e2e suite duplicates the auth suite's registration helper.** Extract at the third caller.

## Topic routing

- **Upstream timeouts are Boot's `spring.http.clients.*`**, not a gateway namespace (none exists in this version).
  They apply to every Boot-built HTTP client, which is fine as the gateway has no other outbound client.
- **The JDK `HttpClient` is pinned to HTTP/1.1** (a `ClientHttpRequestFactoryBuilderCustomizer`). The default h2c
  upgrade over plain `http://` made WireMock reset the stream. Chosen over Apache HttpClient, which would be a new
  dependency.
- **Each path prefix is declared once** as a constant in `InterestTopicRoutesConfiguration`, reused by the security
  rules, so route and access rule can't drift. `/**` alone also matches the bare path.
- **Every `ResourceAccessException` is 502 `"Upstream service unavailable."`, except a read timeout (504).** A
  connect timeout means unreachable, not slow. The real cause goes to a `warn` log, never the body.
- **Inbound `X-User-*` headers are stripped before forwarding**, ahead of any downstream needing caller identity,
  by a hand-written `removeHeadersWithPrefix` (the built-in removes one exact name).
- **WireMock in tests**: `wiremock-testcontainers-module` is an alpha built for Testcontainers 1.x but works on 2.x
  (fall back to a plain `GenericContainer` if a bump breaks it); the client is `wiremock-standalone`, because
  `org.wiremock:wiremock` pulls unshaded Jetty 11. Both are pinned, since Boot's BOM manages neither.
- **The routes suite is grouped by concern** (`Authorization` / `ForwardingFidelity` / `UpstreamFailures`), not
  `@Nested` per endpoint, and mints cookies with `TokenService` since there is no HTTP path to promote an ADMIN. The
  502 scenario gets its own context through a nested `@DynamicPropertySource` pointing at a closed port.

## Reconciliation and feed

- **Reconciliation pages with a native keyset query and follows Postgres' order, never Java's** (`uuid` sorts
  bytewise in Postgres but by signed longs in `UUID.compareTo`). The delete is a `@Modifying` bulk delete carrying
  its own `@Transactional` (one transaction per batch). A short batch ends the run.
- **`InterestTopicLookupService` is built on a `RestClient` bean** from Boot's builder, so it gets the timeouts and
  HTTP/1.1 customizer. Any failure becomes `InterestTopicLookupFailedException`, which reconciliation reads as
  "stop", never as "none exist".
- **Client DTOs are named from the call's point of view** (`...RequestDTO` is what the gateway sends,
  `...ResponseDTO` what it gets back), the reverse of every other DTO here.
- **`GET /api/v1/feed` is a real endpoint, not a route**, since only the gateway knows the caller's subscriptions.
  It sends their topic ids to the topic service with a mode (`SUBSCRIBED`→`INCLUDE`, `NOT_SUBSCRIBED`→`EXCLUDE`).
- **The feed uses a keyset cursor (`after` = last topic name)**, so toggling a card mid-list never skips or repeats
  one. Filter values are the enum's uppercase names; lowercase is a 400.
- **Feed counts come from the topic service**, not `countByUser_Id`, so `all = subscribed + notSubscribed` holds
  while a deleted topic's subscription awaits reconciliation.
- **A feed failure is `InterestTopicFeedUnavailableException` → 502**, separate from the lookup exception, and a
  timeout is 502 too, not the proxy's 504.

## Notification pipeline

- **The inbound topic's dead-letter topic is `topic-news.generated-dlt`**, Spring's default (`-dlt`), not `.DLT`.
- **`KafkaConsumerConfiguration` builds its own `ConsumerFactory<String, TopicNewsEventDTO>`.** The
  `ErrorHandlingDeserializer` needs a real delegate object, and passing it through `spring.kafka.consumer.*`
  properties silently never reached the consumer.
- **Hand-built Kafka factories must read `bootstrap.servers` from the `KafkaConnectionDetails` bean**, not
  `kafkaProperties.build*Properties()`, which returns only the static property text and bypasses `@ServiceConnection`.
  Missing it once connected a test to an unrelated broker on the machine with no error.
- **Fan-out reads subscribers through a keyset interface projection** (`EnabledSubscriberProjection`). A failed
  send is a plain `IllegalStateException`, so the listener's `DefaultErrorHandler` retries the record. One INFO
  summary per event, not per subscriber.
- **Both inbox and outbox writes go through one `JdbcTemplate`**, because `save()` on the assigned-id
  `TopicNewsInbox` is `merge()` (an extra `SELECT`, and a silent `UPDATE` on a duplicate instead of a key failure).
  Timestamps bind as `OffsetDateTime` (UTC) to avoid JVM-timezone ambiguity.
- **`NotificationOutbox` has a unique `(news_id, user_id)` and an index on `(status, created_at)`**, the poller's
  query shape. `TopicNewsInbox` uses the event's `newsId` as its primary key; that is the redelivery guard.
- **`NotificationOutboxPublisherJob` uses `fixedDelayString`** (a property-driven delay can't use `fixedDelay`).
  A publish failure is caught and logged, not rethrown, because retry is per row via `attempt_count`. There is no
  `@Transactional` on the batch, so no DB connection is held across N Kafka sends. `InboxCleanupJob` and the
  publisher each have their own `Service`/`Impl` pair so the job stays a one-line delegation; the cleanup cutoff
  is computed in the service.
- **Oversized or malformed records are skipped and marked processed**, never thrown, since retrying the same
  payload changes nothing. The guard (`skipOversizedRecord`) rejects `data` over `max-data-length` (20 000
  characters, well above the topic service's 2000-token completion limit), a `topicName` or category name over 255
  (the `varchar(255)` column), null or empty `categoryNames`, and a blank or over-16-character `schedule` or null
  `periodStart`. A resource-exhaustion guard until Kafka producers get identity checks.
- **`category_names` is `text[]`**, not a joined string (which would hit the 255 cap and push formatting into the
  gateway); the mail service formats it. The batch insert binds it with `setArray(createArrayOf("text", ...))`.
- **`schedule` is carried as an uninterpreted `String` plus a `LocalDate periodStart`**, so a new preset needs no
  gateway change. `notification_outbox.schedule` is `varchar(16)`.
- **The whole fan-out keyset walk stays in one `@Transactional` method**, so a crash commits everything or nothing,
  the point of the inbox/outbox. **Trigger:** one run's transaction duration becoming noticeable at a large
  subscriber count.
- **`TopicNewsNotificationServiceImplTest` is a `@DataJpaTest` on real Postgres**, not a mocked `JdbcTemplate`,
  because the SQL wiring is what breaks. `TopicNewsListenerIntegrationTest` cleans up in `@AfterEach` too, since a
  `@SpringBootTest` rolls nothing back and left rows broke a later class.
- **`TESTING.md` covers HTTP-layer suites only**, so repository, listener, job and service tests don't touch it.

## ddl-auto=update

The gateway runs `spring.jpa.hibernate.ddl-auto=update` (it was `create`, which wiped the database on every
start). `update` creates missing tables and columns but never alters or drops existing ones, so a changed
column type, a rename or a new unique constraint on a table that already holds rows is not applied. There are
still no versioned migrations: such a change needs a manual SQL change or a fresh volume (`docker compose down -v`).
The e2e stack is unaffected because it always starts from empty Postgres instances.
