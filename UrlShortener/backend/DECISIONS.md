# Decisions

Non-obvious calls made *during* implementation — the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Step 1 — Redis Testcontainer comes from `com.redis`, not `org.testcontainers`

Testcontainers 2.x (2.0.5, managed by the Boot 4.2.0-M1 BOM) ships no Redis module — the Boot BOM
instead manages `com.redis:testcontainers-redis` (2.2.4). Chosen over a bare `GenericContainer`
pinned to a `redis:` image tag so the version stays BOM-managed and `@ServiceConnection` works.
`spring-boot-testcontainers` was added in the same step for that annotation.

## Step 1 — Hutool pinned to 5.8.x with an explicit version

`cn.hutool:hutool-core` is not BOM-managed, so it carries a `${hutool.version}` property (5.8.47).
Stayed on 5.x rather than Hutool 6: `PLAN.md` names `cn.hutool.core.lang.Snowflake`, and 6.x moved
the groupId to `org.dromara.hutool` and repackaged that class.

## Step 1 — Config prefix `url-shortener.*`, cache TTL default 1h

App-owned properties (`base-url`, `cache.ttl`, `snowflake.worker-id`/`datacenter-id`) live under
`url-shortener.*` — matches the artifactId, no collision with `spring.*`. TTL defaults to 1h
(smaller Redis footprint over maximum hit rate), env-overridable via `CACHE_TTL`.

## Step 2 — the submitted URL is stored verbatim; no normalisation

Dropped `PLAN.md`'s planned rules (lowercase scheme+host, drop default port, drop fragment) and the
safer RFC-equivalence-only fallback. Normalisation only buys dedup, but any dedup rule can also collapse
two genuinely different URLs onto one key (e.g. stripping the fragment breaks hash-route URLs).

Trim + control-character rejection are **not** normalisation and stay mandatory (step 5) — an unstripped
`\r\n` in a `Location` header is response splitting. Validated via `java.net.URI`, not regex.

## Step 2 — `@Version` on `CommonEntity`

Spring Data decides "is this new" from the id, and ours is always set by the application, so every
`save()` was a replace: `@CreatedDate` never fired and `createdAt` stayed null. A null `@Version` marks
the entity as new instead, so the first save is a real insert.

Chosen over `Persistable<String>` with a transient `isNew` flag (more boilerplate, and a replace still
silently clobbers an existing document) and over dropping `createdAt` altogether. The side benefit
decided it: saving a fresh object whose `_id` already exists now fails with `DuplicateKeyException`
rather than replacing the stored mapping, so a short code that has already been handed out can't be
orphaned by a racing create.

## Step 2 — one Mongo container per suite, started from a static initialiser

`AbstractMongoIntegrationTest` holds a `static final MongoDBContainer` started in a static block and
annotated `@ServiceConnection`. Chosen over `@Testcontainers` + `@Container`, which failed with
"MongoDBContainer should be started first" — with `@DataMongoTest` the Spring context was built before
the Testcontainers extension had started the container. A static initialiser runs on class load, so the
container is always up first, and every test class in the run shares the one instance.

## Step 4 — catch `DataAccessException`, not a bare `Exception`

`RedisShortUrlCache.get`/`put` catch Spring Data's `DataAccessException` — every exception a
`RedisTemplate` operation throws (connection failure, timeout, serialization) is wrapped into that
hierarchy. Catching the specific superclass instead of `Exception` keeps a real bug in the cache code
(a `NullPointerException`, say) from being silently swallowed and misreported as "Redis is down."

## Step 4 — the "Redis unreachable" test builds its own `LettuceConnectionFactory`, doesn't stop the container

`AbstractRedisIntegrationTest` starts one Redis container for the whole suite; stopping it to simulate an
outage would break every other test class sharing it. Instead the outage test points a throwaway
`LettuceConnectionFactory` at a closed local port (short command timeout, no pooling) and wires a second
`RedisShortUrlCache` to it — the shared container is never touched.

## Step 5 — `RestTestClient`, not `TestRestTemplate`, for the E2E test

`PLAN.md` named `TestRestTemplate`, but on 4.2.0-M1 it's `@Deprecated(forRemoval = true)` in favour of
`RestTestClient` (Spring Framework 7). Used on a plain `@SpringBootTest` (`MOCK` web env — binds to the
`WebApplicationContext` directly, no live port needed); still exercises the full stack.

## Step 5/8 — `InternalHostGuard` resolves DNS for every submitted hostname

Resolves via `InetAddress.getAllByName(host)` and checks every returned address against
loopback/link-local/private ranges; unresolvable hosts fail closed. Chosen over a resolver abstraction
(mockable DNS) — that would promote this from a `/utilities` static method to a `/services` pair for one
method's sake, so `InternalHostGuardTest` accepts resolving real hostnames instead.
 
Step 5 originally shipped a literal-string match only (no resolution) as in-scope for that step's
checklist; step 8's exploit-hunter audit found it trivially bypassed by any hostname resolving to an
internal address (e.g. `127.0.0.1.nip.io`, no DNS rebinding needed), which is what moved it to full
resolution.

## Step 8 — accepted: codes are enumerable via the Snowflake/Base62 scheme

The audit found that with worker/datacenter id fixed by config, a code's only unknown per
millisecond is a 12-bit sequence counter — cheap to brute-force, exposing every shortened URL
created near a known timestamp. Not fixed: doing so would mean changing the code-generation
scheme decided in `PLAN.md` step 3 (Snowflake → Base62, no custom aliases), which needs its own
design pass rather than a reactive patch. Accepted for now.

## Step 8 — fixed: `InternalHostGuard` IPv4-compatible IPv6 and octal-octet bypasses

A second exploit-hunter pass found two literal-encoding bypasses of `isInternalAddress`: the
deprecated IPv4-compatible IPv6 form (`::127.0.0.1`, `::10.0.0.1`, `::169.254.169.254`) resolves to
an `Inet6Address` that none of `isLoopbackAddress`/`isLinkLocalAddress`/`isSiteLocalAddress`/
`isAnyLocalAddress`/the unique-local check catch — unlike the IPv4-*mapped* form (`::ffff:a.b.c.d`),
which Java collapses to a plain `Inet4Address` on its own. Fixed by extracting the embedded IPv4
bytes from any 16-byte address whose first 12 bytes are zero and recursing the same range checks on
it.

Separately, a dotted-decimal octet with a leading zero (`0177.0.0.1`) is parsed as decimal by Java
but as octal by browsers (WHATWG URL spec), so the guard and a victim's browser can resolve the same
literal to two different addresses. Not fixable by checking Java's resolved address (there's nothing
wrong with what Java resolves to) — the ambiguity itself is the bug, so any host whose labels are all
digits and at least one has a leading zero is now rejected outright (fail closed) before resolution,
rather than resolved and checked.

## Step 8 — accepted: no rate limiting on `POST /api/v1/urls`

The audit found the unauthenticated create endpoint has no request cap, compounding the
enumeration risk above and allowing unbounded Mongo document creation. Not fixed: adding one
needs a design pass first (library, per-IP scope given no auth, in-memory vs. Redis-backed for
multi-instance). Accepted for now.

## Step 6 — redirect resolution lives on `ShortUrlService`, not a new interface

`resolveOriginalUrl` was added to the existing `ShortUrlService`/`ShortUrlServiceImpl` rather than a
separate service. Both methods operate on the same entity and repository; splitting them would mean two
services injecting the same `ShortUrlRepository` for no isolation benefit.

## Step 6 — new `AbstractMongoAndRedisIntegrationTest`, not multiple inheritance

The redirect E2E test needs both a live Mongo and a live Redis in the same context, but the existing
Mongo/Redis test base classes are each a class (no multiple inheritance in Java). Added a third class
holding both `@ServiceConnection` static fields, rather than restructuring the existing two into
interfaces — untested territory for how Spring's Testcontainers field-scanning handles that.

## Step 7 — error body shape and `GlobalExceptionHandler extends ResponseEntityExceptionHandler`

`ErrorResponseDTO(status, messages: List<String>, timestamp: long)` is the one shape every error response
uses. `messages` is a list, not a joined string — one field can fail more than one constraint
(`@NotBlank` + `@ShortenableUrl` both firing on a blank `url`), and a list avoids forcing the frontend to
string-split; per-field `{field, message}` pairs were considered and dropped as unneeded structure. No
`error`/`path` field — `status` + `messages` is enough for now.

`GlobalExceptionHandler extends ResponseEntityExceptionHandler` rather than a bare
`@RestControllerAdvice`: Spring MVC's built-in exceptions already route through that class's internal
dispatch, so overriding its protected `handleMethodArgumentNotValid`/`handleHttpMessageNotReadable` hooks
is the supported extension point. `ShortUrlNotFoundException` (404) and a catch-all `Exception` handler
(500) are added as ordinary `@ExceptionHandler`s on top. `RedirectController` now throws
`ShortUrlNotFoundException` instead of building its own 404, so it gets the same error body as every
other endpoint.

## Step 9 — added `spring-boot-starter-actuator` for the app container's healthcheck

`docker-compose.yml`'s `app` healthcheck needs an HTTP endpoint; nothing exposed one. Default exposure
(`health` only) needs no extra `management.*` config. Chosen over polling a business endpoint (e.g.
`GET /{code}` with a bogus code) so the healthcheck isn't coupled to redirect behaviour — Mongo/Redis
health indicators also come free once actuator's on the classpath.

## Step 10 — springdoc-openapi 3.1.1 for the Boot 4.2.0-M1 milestone

springdoc's compatibility matrix only lists Boot 4.0.x ↔ springdoc 3.0.x, nothing past that. Verified
directly instead of assuming: `mvn clean install` stayed green and a manual run served both
`/swagger-ui/index.html` and `/v3/api-docs` correctly. No `OpenApiConfiguration` added — default
title/spec is enough for now.

## Post-plan — `prod` Spring profile with no `localhost` fallbacks

`READINESS_REPORT.md` flagged that every property in `application.properties` has a
`localhost`/default fallback, so a deploy that forgets `MONGODB_URI`/`REDIS_HOST`/`BASE_URL` boots
silently against `localhost` instead of failing fast. Added `application-prod.properties`
re-declaring those three with no default (`${MONGODB_URI}`, not `${MONGODB_URI:localhost...}`), and
`docker-compose.yml`'s `app` service now sets `SPRING_PROFILES_ACTIVE=prod`. Verified: `./mvnw
spring-boot:run` with the profile active and the vars unset fails at context startup instead of
connecting anywhere; with them set (via the real compose stack) it boots and serves normally.

While verifying this end-to-end (see the frontend's matching entry in its own decisions/CLAUDE
context) it surfaced an unrelated pre-existing bug: the frontend Docker image was baking in its
local-dev `.env` (`VITE_BASE_API_URL=http://localhost:8080`) because the frontend `.dockerignore`
didn't exclude `.env`, so the deployed bundle called the backend by an absolute dev URL instead of
the intended same-origin `/api/...` path. Fixed by adding `.env` to
`url-shortener-frontend/.dockerignore`.
