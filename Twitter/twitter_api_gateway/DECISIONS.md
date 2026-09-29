# Decisions

Non-obvious calls made *during* implementation — the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Step 1 — a minimal `SecurityConfiguration` ships before step 4

Step 1's gate needs `GET /actuator/health` to answer `200`, but the real `SecurityConfiguration` (JWT
resource server, cookie resolver, entry point) is step 4. Without any chain, Spring Security's default
locks health behind a login. So step 1 ships a stub: CSRF off, stateless, health public, everything else
`authenticated()`. Step 4 replaces it in place; the public/authenticated split already matches the plan's
final rule.

## Step 1 — one root `.env.example`, Postgres on 5432, Kafka on 9094

The plan puts `.env.example` at the Twitter root, so it holds the compose variables and the app variables
together. Postgres publishes the default 5432 and Kafka 9094, same as SignalFlow, so the app's property
defaults (`localhost:5432`, `localhost:9094`) work with no env. The catch: SignalFlow's compose and this
one can't run at the same time. Changing a host port means also setting `DATASOURCE_URL` /
`KAFKA_BOOTSTRAP_SERVERS`. Optional `DATASOURCE_*` lines are commented out in `.env.example` because an
exported empty value would override the property default with an empty string.

## Step 1 — the test clock stands still

`MutableClock` is frozen at the instant it was created (truncated to microseconds) and moves only through
`advance`, `setInstant` and `reset`. A ticking clock would make the resend-cooldown and expiry tests depend
on how long the test took; a frozen one makes "61 seconds later" exact. It is registered as a `@Primary`
bean via `TestClockConfiguration`, imported once on `AbstractPostgresIntegrationTest`, so every integration
test gets it. It is shared across a cached context, so a test that advances it calls `reset()` afterwards.

## Step 1 — `TestUsers` returns plain credentials, not entities

`User` and the register DTO don't exist until steps 3 and 5, so `TestUsers.unique()` returns a `TestUser`
(username, email, password) from an `AtomicLong` sequence. Later steps add persistence helpers next to it.

## Step 1 — skills copied as real directories

SignalFlow keeps `java-code-style` as a relative symlink to a sibling folder; copied as-is it dangled.
All four skills are plain directories under `Twitter/.claude/skills/`.

## Step 3 — bulk repository deletes flush and clear the persistence context

`deleteUnconfirmedCreatedBefore` and `deleteByUserId` are `@Modifying(flushAutomatically = true,
clearAutomatically = true)` bulk JPQL. Bulk deletes bypass the persistence context, so without the clear a
caller in the same transaction would still see deleted rows as managed entities. The user delete relies on
the DB `ON DELETE CASCADE` (from `@OnDelete`) to remove tokens.

## Step 3 — repository tests backdate rows with JDBC

`createdAt` is `@CreationTimestamp` and not updatable, so tests that need old rows (cleanup cutoff, outbox
ordering) `UPDATE ... SET created_at` through `JdbcTemplate` with fixed instants, then clear the persistence
context. No wall-clock reads and no sleeps.

## Step 3 — normalisation uses `Locale.ROOT`

`email` and `usernameNormalized` are lowercased with `Locale.ROOT`, so a Turkish default locale can't turn
`I` into a dotless `ı` and break uniqueness or lookups.

## Step 4 — token `iat`/`exp` and error-body timestamps use the system clock

`TokenServiceImpl` and `ErrorResponseWriter`/`GlobalExceptionHandler` call `Instant.now()` rather than the
`Clock` bean. The decoder validates `exp` against the system clock, so a token minted from a moved test
clock would come out already expired (the plan says JWT times stay on system time). An error body's
`timestamp` compares against nothing, so it follows the same choice. This is a deliberate exception to
CLAUDE.md's "never call `Instant.now()` in main code"; everything that *compares* against now still uses
`Clock`.

## Step 4 — a token grants no authorities

`JwtAuthenticationConverter` returns an empty authority list. There are no roles, so "authenticated" is the
only distinction the chain draws. The alternative, Spring's default converter, would derive `SCOPE_*`
authorities from a `scope` claim we never set.

## Step 4 — `AuthController` starts with logout and me only

Steps 5–8 add register, confirm, resend and login to it. `me` builds its body from the JWT claims in the
controller, since there is nothing to delegate to a service (no database read).

## Step 4 — `TestJwts` tampers the first signature character

Flipping the last base64url character of a 32-byte HS256 signature can leave the decoded bytes unchanged
(it holds padding bits), which would make a "tampered" token still verify. The first character always
changes the signature.

## Step 5 — `ConfirmationRequestService` is shared by register and resend

The plan lists `ConfirmationTokenService` (generate, hash) and `OutboxService.enqueue`, but "store a fresh
token and queue the event" is one unit that register (step 5) and resend (step 7) both need. It lives in
`ConfirmationRequestService.requestConfirmation(user)`, which joins the caller's transaction, so resend
only adds its cooldown check and the delete of old tokens.

## Step 5 — the token hash is SHA-256 over the UTF-8 bytes of the token string

The plan says "over the raw token's bytes". The 43-character string is what arrives in the confirm request,
so hashing it directly avoids a decode step and can't disagree with what the link carried.

## Step 5 — constraint names are constants on `User`

`User.EMAIL_CONSTRAINT` and `User.USERNAME_CONSTRAINT` feed both the `@UniqueConstraint` names and
`AuthServiceImpl`'s mapping of a lost race to the right 409. A rename can't silently break the mapping. A
violation of any other constraint is rethrown and falls to the generic 409 handler.

## Step 5 — event JSON has no field order

Jackson 3 sorts properties alphabetically by default, so the payload's key order isn't declaration order.
The contract is the set of field names; tests assert them in any order.

## Step 6 — the poll job's initial delay equals its poll delay

`@Scheduled(fixedDelay)` fires once immediately at startup by default. The test profile sets the delay to 24h
so the poller never drains rows mid-suite; without `initialDelay` it would still run once at context start.
In production the first poll is 3s after startup, which is harmless.

## Step 6 — `KafkaTopicConfiguration`, not `KafkaTopicConfig`

The plan's name; renamed to match `ClockConfiguration` and `SecurityConfiguration`. `@EnableScheduling` lives on
its own `SchedulingConfiguration` so `@DataJpaTest` slices don't pick it up.

## Step 6 — a failed row doesn't stop the batch, and an interrupt counts as a failed attempt

One bad row (or a timeout) records the failure and the loop moves on, so a poison row can't block newer ones.
An `InterruptedException` restores the interrupt flag, counts as an attempt, and the loop still proceeds; the
next send then fails fast.

## Step 7 — confirm and resend live in `EmailConfirmationService`, not `AuthService`

Keeps `AuthServiceImpl` (register, later login) free of the clock and cooldown dependencies. Resend reuses
`ConfirmationRequestService` from step 5.

## Step 7 — resend reads the user before locking it

`findByEmail`, then `lockById`, then the cooldown check. A confirm that commits between the read and the lock
can still cost one extra email to an already-confirmed user. That is harmless, and the cooldown still bounds it,
so the lock stays as the plan says instead of a second read.

## Step 7 — a token expiring exactly now is expired

The check is `expiresAt.isAfter(now)`, so the boundary instant is rejected. The cooldown mirrors it: a token
issued exactly one cooldown ago no longer throttles.

## Step 8 — the unknown-identifier path hashes the submitted password

`passwordEncoder.encode(password)` runs and is discarded, as the plan says, so the unknown path costs one bcrypt
like a wrong password. `login` has no `@Transactional`: it only reads.

## Step 8 — the controller mints the cookie, the service returns the user

`AuthService.login` returns the `User`; `AuthController` calls `TokenService.mint` and `CookieFactory.issue`.
The service stays free of HTTP and cookie concerns.

## Step 8 — password capped at 72 characters on login

`LoginRequestDTO.password` is `@Size(max = 72)`, matching register and bcrypt's input limit, so an oversized
password is a 400 rather than a bcrypt error.

## Step 9 — the job logs the count, the service only deletes

`UnconfirmedUserCleanupJob` writes the INFO line (also when the count is 0, once a day); the service returns the
count and stays log-free, like the outbox publisher pair. The cutoff comes from the injected `Clock`, so the
integration test backdates rows relative to the same clock instead of sleeping.

## Step 10 — the audit's password crash is fixed with a byte-size constraint

`@Size(max = 72)` counts characters and bcrypt takes 72 bytes, so a multi-byte password gave a 500. A custom
`@MaxUtf8Bytes(72)` sits beside `@Size` on register and login. `@Size` stays for the minimum and as a cheap
character bound.

## Step 10 — startup refuses a `JWT_SECRET` under 32 bytes

Nimbus rejects shorter HS256 keys only when signing, which turns a misconfiguration into a 500 on every login.
Failing at startup surfaces it before traffic does.

## Step 10 — concurrency tests run over HTTP with a start latch

`AuthConcurrencyIntegrationTest` releases 8 requests at once and asserts on status counts and row counts,
never on timing, so it needs no sleeps. It sits in `TESTING.md` under "Concurrency".

## Step 11 — MinIO runs from `alpine/minio`, pinned, as root

`minio/minio` is no longer pullable from Docker Hub and no upstream tag could be verified, so compose and
Testcontainers use `alpine/minio:RELEASE.2025-10-15T17-29-55Z` (declared a compatible substitute for
`minio/minio`). That image runs as an unprivileged `minio` user that can't write `/data`, so both run it as
root. The compose healthcheck is `wget` on `/minio/health/live` because the image has no `mc`.

## Step 11 — `okhttp-jvm` is a direct dependency

`io.minio:minio` pulls `okhttp` 5.x, whose `okhttp` artifact is an empty shell with `okhttp-jvm` at runtime
scope only. `MinioClient.builder()` exposes `okhttp3` types, so main code failed to compile without it.

## Step 11 — every full-context test extends `AbstractMinioIntegrationTest`

The bucket initializer is an `ApplicationRunner` that fails startup when MinIO is unreachable, so every
`@SpringBootTest` needs a MinIO container. `AbstractMinioIntegrationTest` extends the Kafka base and feeds
`app.minio.*` through `@DynamicPropertySource`. `@DataJpaTest` slices don't run runners and stay on the
Postgres base.

## Step 13 — each picture slot is unique per file, and the FKs have no `ON DELETE` action

`profilePicture` and `profileCoverPicture` are `@OneToOne`, so Hibernate makes each FK column unique: two users
can't share one `DbFile` row. Deleting a `DbFile` a user still points at is rejected by the FK. The upload flow
already repoints the user first and deletes the old row second, so neither rule bites it. The alternative,
`ON DELETE SET NULL`, would hide a flow that deleted in the wrong order.

## Step 13 — the `@DynamicUpdate` test writes the racing column over JDBC

Two Hibernate sessions can't interleave inside one `@DataJpaTest` transaction. The test loads the user, changes
`bio` with a JDBC `UPDATE` (the racing edit), then sets the picture on the still-managed entity and flushes. Without
`@DynamicUpdate` the flush would write the stale `null` bio back.

## Step 14 — `ProfileService` returns the response DTO, and a static `ProfileMapper` builds it

`AuthService` returns the entity, but the profile's picture URLs read the lazy `DbFile` associations, which must
happen inside the transaction. Building the DTO in the service keeps that read there. `ProfileMapper` is a
static utility because upload and delete (step 16) return the same shape.

## Step 14 — `GET /users/me` can't collide with a username

`GET /api/v1/users/{username}` would answer `me` as a lookup, but usernames need 3+ characters, so no user
is called `me` and the route just gives 404.

## Step 14 — `@Past` on a birthdate uses the system clock

Bean Validation's default clock provider, not the `Clock` bean, so `@Past` isn't moved by the test clock. The
future-birthdate test uses a date a year ahead, which no clock drift can flip. Today's date is also rejected,
since `@Past` excludes it.

## Step 14 — the length checks run on the raw string, before trimming

A bio of 160 characters plus surrounding spaces is a 400. Validating after the trim would mean a custom
validator or an extra setter on the DTO; the case is a client bug, not one worth accepting.

## Step 15 — the upload body cap matches `PUT` on the two exact picture paths only

`RequestBodySizeLimitFilter` runs before security, so it matches on `getRequestURI()`. Any other method, path
variant or trailing slash keeps the 8 KB cap; a mismatch fails small, never large. Spring's multipart
`max-file-size` and `max-request-size` are derived from `app.upload.max-file-bytes` and
`app.request.max-upload-body-bytes` by placeholder, and `UploadLimitsConfigurationTest` pins that.

## Step 15 — `ObjectStorageService.get` fails with 502 for a missing object

Every MinIO failure, `NoSuchKey` included, becomes `StorageUnavailableException`. The `DbFile` row is the
source of truth for existence (404 comes from the DB lookup in step 17), so a row whose object is gone is a
storage fault, not a client error. `delete` of a missing key is a no-op, since MinIO treats it as success.

## Step 15 — `ImageSignatureValidator` is a static utility returning the content type

It needs no Spring dependency, so it follows the utility rules: `detectContentType(byte[])` reads the first
`HEADER_BYTES` (12) and throws `UnsupportedImageTypeException` (415) for anything else, including a truncated
header. A WebP is `RIFF` at 0 and `WEBP` at 8; the size field between them is ignored.

## Step 16 — the DB change runs in a `TransactionTemplate`, not `@Transactional`

The new object must be stored before the transaction and the old one deleted after the commit, and a
`@Transactional` method would put both storage calls inside it. `ProfilePictureServiceImpl` wraps only the
repoint (and the clear on delete) in the template; unit tests use a real template over a mocked transaction manager.

## Step 16 — the 5 MB limit is checked in the service as well as by Spring

MockMvc never applies Spring's multipart limits, so the service compares `file.getSize()` with
`app.upload.max-file-bytes` and throws `MaxUploadSizeExceededException`, which the same 413 handler answers.
`PictureUploadLimitsIntegrationTest` proves the real limits over a real server; it builds the multipart body by
hand so the request has a `Content-Length` (a streamed body goes out chunked, and the server's 413 mid-upload
resets the connection under the client).

## Step 16 — concurrent uploads to one slot are not serialised

Probed in step 18: 8 parallel uploads to one slot all answered 200, but only one `DbFile` survived as the
pointer; the other four rows added (and their objects) are orphans, because each transaction read the same
old picture and the last pointer write won. Tracked as finding 1 in `exploit-report-2026-09-29-phase2.md`.

## Step 16 — routes are four explicit mappings, not `/me/{slot}`

`RequestBodySizeLimitFilter` matches the two upload paths exactly, and a path variable would accept any string.
`PictureSlot` carries the slot's getter and setter so the service runs one flow for both.

## Step 17 — file serving reads the row, then streams the object, with no transaction

`FileServiceImpl.open` needs only the row's key, type and size, so it skips `@Transactional`: a transaction held
open for the length of a download would pin a connection. A row whose object is missing answers 502 (see step 15).
Any logged-in user can read any file id: ids are random UUIDs and the pictures are public on the profile anyway.

