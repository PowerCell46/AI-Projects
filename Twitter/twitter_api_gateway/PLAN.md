# Twitter API Gateway — plan

The single front door of the Twitter clone. It owns users and all access rules. One deployable, not a
gateway plus a separate user service. It is built in three ordered phases:

1. **Auth + email confirmation.** Register, confirm, resend, login, logout, me.
2. **Profile.** View and edit a profile, and upload profile and cover pictures to MinIO.
3. **Follows.** Follow and unfollow, stored follower/following counters, and cursor-paged lists.

Every design call below was settled in the `/grill-me` interview of 2026-09-29 (record at the bottom).
The reference implementation for everything marked **"as SignalFlow"** is
`../../SignalFlow/signal_flow_api_gateway`. Port from there, don't reinvent.

**Hard rule for every step:** no step starts on a red or missing test, and each step ends green. Nothing in
phase *n+1* starts until phase *n*'s final gate passes.

---

## Global design

### Stack and layout

```
Twitter/
├── docker-compose.yml        Postgres + Kafka (phase 1), MinIO (phase 2)
├── .env.example
├── PLAN.md                   pointer to this file
├── .claude/skills/           java-code-style, java-junit, exploit-hunter, grill-me (copied from SignalFlow)
└── twitter_api_gateway/
    ├── PLAN.md               this file
    ├── CLAUDE.md             ported from SignalFlow's gateway, adapted (see "Standing rules")
    ├── DECISIONS.md          calls made during implementation
    ├── TESTING.md            hand-maintained HTTP scenario catalog
    └── EVENTS.md             Kafka contracts this service produces (for twitter_mail_service)
```

- **Build:** Java 25, Spring Boot **4.1.1**, Maven wrapper, and a multi-stage Dockerfile, all as SignalFlow.
  The Maven artifact and folder are `twitter_api_gateway`; the package is
  `com.peter_gerdzhikov.twitter_api_gateway`; the port is **8080**.
- **Starters:** `data-jpa`, `security`, `oauth2-resource-server`, `actuator`, `validation`, `webmvc`,
  `kafka`, plus `postgresql` and `lombok`. Test starters are `*-test` per module (Boot 4 naming, as
  SignalFlow), with `testcontainers-postgresql`, `testcontainers-kafka` and `awaitility`. Phase 2 adds
  `io.minio:minio` and `testcontainers-minio`.
- **No `spring-cloud-gateway-server-webmvc`.** There is nothing to route to yet. Add it with the first
  downstream service.
- **Packages:** same as SignalFlow's gateway: `configurations`, `controllers`, `DTOs/{request,response,event}`,
  `entities` (+`enums`), `exceptions`, `jobs`, `repositories`, `services/{interfaces,implementations}`,
  `utilities`.
- **Code rules, as SignalFlow:** controller → service → repository; every service is an interface plus an
  `Impl`; constructor injection only; endpoints under `/api/v1`; tests named snake_case
  `should_<behaviour>_when_<condition>`.

### Carried over from SignalFlow unchanged (Q20)

1. **Password:** 8–72 chars, at least one lowercase, one uppercase and one digit. BCrypt strength 12, and 4 in
   the test profile.
2. **Cookie:** `access_token`, `HttpOnly`, `SameSite=Strict`, `Path=/`, and `Secure` from `COOKIE_SECURE`
   (false locally).
3. **CSRF disabled.** `SameSite=Strict` already covers it.
4. **Logout:** public, clears the cookie, `204`.
5. **`JWT_SECRET` has no default.** The app refuses to start without it.
6. **Errors:** `ErrorResponseDTO {status, messages[], timestamp}` from the ported `GlobalExceptionHandler`,
   which never echoes framework or exception internals. `RestAuthenticationEntryPoint` and
   `RestAccessDeniedHandler` go through `ErrorResponseWriter`.
7. **Body cap:** `RequestBodySizeLimitFilter` plus `BodySizeLimitingRequestWrapper`. 8 KB by default, `413`,
   ahead of the security chain.
8. **Login timing:** an unknown identifier still burns one bcrypt `encode`.

### Authentication model

- **JWT (HS256, `NimbusJwtEncoder`/`NimbusJwtDecoder` over one secret), as SignalFlow.**
  - Claims: `sub` (user UUID), `username`, `email`, `iat`, `exp`. No `role` claim, because there are no roles
    (Q5).
  - The decoder validator requires a `sub` that parses as a UUID, plus `username`, `email` and `exp`.
- **TTL 1h** (`app.jwt.ttl`). No refresh token and no server-side revocation (Q19).
- **Authorization never reads the DB (Q18).** "Who am I" comes only from claims. Endpoints that read *resources*
  (profiles, follows) do hit the DB, because that's their job.
- **`CookieBearerTokenResolver` ignores the public matchers**, as SignalFlow.
- **Access rules:** public endpoints are `GET /actuator/health` and `POST /api/v1/auth/{register,login,logout,confirm,confirm/resend}`.
  Everything else is `authenticated()` (Q29).

### Time

- **A `Clock` bean** (`Clock.systemUTC()`) is injected wherever the code compares against "now": token
  expiry, the resend cooldown, the cleanup cutoff, and follow `createdAt`.
- **Tests** replace it with a mutable test clock (`@TestConfiguration`) and move it forward instead of waiting.
- **JWT `iat`/`exp` stay on system time**, as SignalFlow. Expiry tests mint already-expired tokens directly.

### Schema

- **`spring.jpa.hibernate.ddl-auto=update`** (not `create`), with no Flyway (Q22–Q23).
- **Every unique constraint and FK gets an explicit name** (`uk_users_email`, `uk_users_username_normalized`,
  …), so services can tell *which* constraint a `DataIntegrityViolationException` broke.
- **Cascades** use Hibernate `@OnDelete(action = OnDeleteAction.CASCADE)`, so the DDL gets `ON DELETE CASCADE`.

---

## Phase 1 — Auth + email confirmation ✅ **Done** (2026-09-29)

### Design

**Entities**

- `CommonEntity`: `id` (UUID, `@UuidGenerator(TIME)`), `createdAt`, `updatedAt`. As SignalFlow.
- `User` (`users`):
  - `username`: as typed, e.g. `PeterG`. 3–15 chars, `^[A-Za-z0-9_]{3,15}$`. Immutable (Q15).
  - `usernameNormalized`: lowercase copy, set in `@PrePersist`/`@PreUpdate`, with unique constraint
    `uk_users_username_normalized`. **All lookups and uniqueness checks use this column.**
  - `email`: lowercased in `@PrePersist`/`@PreUpdate`, as SignalFlow. Unique constraint `uk_users_email`.
  - `password`: bcrypt hash.
  - `enabled` (`boolean`, Lombok getter `isEnabled()`): **false on register, true on confirm**. It's the one
    flag for "confirmed" (Q4).
- `EmailConfirmationToken` (`email_confirmation_tokens`):
  - `user`: `@ManyToOne(LAZY)`, not null, `@OnDelete(CASCADE)`.
  - `tokenHash`: unique, not null. Base64url of SHA-256 over the raw token's bytes.
  - `issuedAt`: set from `Clock`. Used by the cooldown; not `@CreationTimestamp`, so tests control it.
  - `expiresAt`: `issuedAt + app.confirmation.ttl` (24h).
- `Outbox` (`outbox`), generic (Q24):
  - Columns: `topic`, `messageKey`, `payload` (TEXT, a JSON string), `status` (`OutboxStatus`: `PENDING` or
    `FAILED`) and `attempts` (int).
  - No FK to `users`. Queued events must survive user cleanup.
  - Index on `(status, createdAt)`.

**Confirmation token (Q6)**

- **Raw token:** 32 bytes from `SecureRandom`, base64url without padding (43 chars). It appears only in the
  email link, never stored or logged.
- **Stored:** SHA-256 hash only. Plain SHA-256 is enough, since the token already carries 256 bits of entropy.
- **Single-use:** the row is deleted on confirm.
- **Resend** deletes the user's older rows before inserting the new one.

**Event (Q9)**

- **Topic:** `user.confirmation-requested`, 3 partitions, created by a `KafkaTopicConfig` `NewTopic` bean.
- **Key:** `userId`.
- **Value:** JSON with no type headers:
  ```json
  { "eventId": "uuid", "userId": "uuid", "email": "…", "username": "…",
    "confirmationUrl": "<app.confirmation.link-base-url>?token=<raw>", "expiresAt": "ISO-8601 instant" }
  ```
  - `eventId` is a fresh UUID per event, and it's the mail service's dedupe key.
  - It's serialized once at enqueue time into `outbox.payload`, so the producer sends a **String** value
    (`StringSerializer`).
- **Emitted** on register and on every resend that isn't throttled.
- **The contract is written into `EVENTS.md`.**

**Outbox publisher (Q24)**

- **Job:** `OutboxPublisherJob` runs `@Scheduled(fixedDelayString = app.outbox.poll-fixed-delay-ms)` (3000) and
  delegates to `OutboxPublisherService`.
- **Each run:** load up to `app.outbox.batch-size` (500) `PENDING` rows, ordered by `createdAt`. For each row,
  `kafkaTemplate.send(topic, key, payload).get(app.outbox.send-timeout)`. Then:
  - **Success:** delete the row.
  - **Failure:** `attempts + 1`. At `app.outbox.max-attempts` (3), set `FAILED` and log a `warn`. The row is left
    for a human to inspect.
- **Producer:** `acks=all`, idempotence on, no type headers. As SignalFlow.

**Endpoints**

- **`POST /api/v1/auth/register`**
  - **Body:** `{username, email, password}`.
  - **`201`** returns `UserResponseDTO {id, username, email}`, **no cookie** (Q11).
  - **One transaction:**
    1. Pre-check email, then username, for friendly messages.
    2. Save the user (`enabled = false`).
    3. Save the token.
    4. Enqueue the outbox row.
  - **`409` messages:** "Email already registered." / "Username already taken.". A pre-check that loses a race
    ends in a `DataIntegrityViolationException`, mapped to the right message **by constraint name**.
  - **`400`** on validation failure.
- **`POST /api/v1/auth/confirm`**
  - **Body:** `{token}`, validated as 43 chars of the base64url alphabet.
  - **`204`** with no cookie (Q11).
  - **One transaction:**
    1. Hash the token.
    2. `findByTokenHash` with `PESSIMISTIC_WRITE`. A concurrent second confirm waits, then finds nothing.
    3. Check `expiresAt > clock.now()`.
    4. Set `enabled = true`.
    5. Delete the token.
  - **Unknown, used or expired token:** `400` "Invalid or expired confirmation token." Expired rows are left
    for the cascade or a resend.
- **`POST /api/v1/auth/confirm/resend`**
  - **Body:** `{email}`.
  - **`202` always** when the body is valid: unknown email, already confirmed, pending, or throttled (Q13).
    `400` only for a malformed body.
  - **Acts only when the user exists and `!enabled`,** in one transaction:
    1. Lock the user row (`lockById`, as SignalFlow), so two concurrent resends can't both pass the cooldown.
    2. If the newest token's `issuedAt > now − app.confirmation.resend-cooldown` (60s), no-op.
    3. Otherwise delete the old tokens, insert a new token and enqueue the outbox row.
- **`POST /api/v1/auth/login`**
  - **Body:** `{identifier, password}`. `identifier`: `@NotBlank`, max 254. It is treated as an email if it
    contains `@` (lowercased), otherwise as a username, looked up on `usernameNormalized` (Q17).
  - **`200`** sets the cookie and returns `UserResponseDTO`.
  - **Unknown identifier or wrong password:** `401` "Invalid credentials.", with the dummy bcrypt on the unknown
    path.
  - **Correct password but `!enabled`:** `403` "Please confirm your email first." (Q14). This is checked
    **after** the password match, so it never fires on a wrong password.
- **`POST /api/v1/auth/logout`:** `204`, clears the cookie. Public.
- **`GET /api/v1/auth/me`:** `200 {id, username, email}` from claims only. `401` without a valid cookie.

**Unconfirmed-user cleanup (Q12)**

- **Job:** `UnconfirmedUserCleanupJob`, with `@Scheduled(cron = app.users.unconfirmed-cleanup.cron,
  zone = …zone)`. Default `0 0 4 * * *` in `Europe/Sofia`.
- **Action:** a bulk delete of `users WHERE enabled = false AND createdAt < clock.now() − retention` (7 days).
  Tokens cascade; the outbox is untouched. Logs one INFO line with the count.

**Properties** (all env-overridable, listed in `.env.example`, grouped with comment headers as SignalFlow)

- `app.jwt.secret`, `app.jwt.ttl` (1h), `app.cookie.secure`, `app.security.bcrypt-strength` (12)
- `app.request.max-body-bytes` (8192)
- `app.confirmation.ttl` (24h), `app.confirmation.resend-cooldown` (60s), `app.confirmation.link-base-url`
  (`http://localhost/confirm`)
- `app.users.unconfirmed-cleanup.{cron,zone,retention}` (`0 0 4 * * *`, `Europe/Sofia`, `7d`)
- `app.outbox.{poll-fixed-delay-ms,batch-size,max-attempts,send-timeout}` (3000, 500, 3, 10s)
- `app.kafka.user-confirmation-requested.{name,partitions}` (`user.confirmation-requested`, 3)
- `spring.kafka.*` producer settings, and Kafka logging at WARN, as SignalFlow

**Test profile overrides:**
- `bcrypt-strength=4`
- a fixed JWT secret
- cleanup cron `-`
- outbox poll delay `86400000`, so tests call the publisher directly

### Scenarios (the `AuthControllerIntegrationTest` catalog, mirrored in `TESTING.md`)

- **Register**
  - `201`, body shape, and no `Set-Cookie`.
  - The DB has the user with `enabled = false`, normalized username and lowercased email; one token row (hash
    ≠ raw); and one `PENDING` outbox row whose payload has every contract field, with `confirmationUrl`
    ending in a token whose hash matches the stored one.
  - `409` for a duplicate email in any case, and for a duplicate username in any case (`PeterG` vs `peterg`).
  - `400` for each field rule: username length and characters, email format, the password rules.
  - `413` over 8 KB.
  - The password never appears in any response.
- **Confirm**
  - `204`; the user becomes enabled; the token row is gone.
  - Reusing the same token gives `400`.
  - An unknown token gives `400`. A malformed token gives `400`.
  - An expired token gives `400`: the clock moves past 24h, and the user stays disabled.
  - After a resend, the old token gives `400` and the new one `204`.
  - No `Set-Cookie`.
- **Resend**
  - `202` for an unknown email, with no outbox row.
  - `202` for a confirmed user, with no outbox row.
  - `202` for a pending user: one new token, old tokens deleted, one new outbox row.
  - A second resend within 60s gives `202` with no new row. After the clock moves 61s, it gives `202` with a
    new row.
  - A malformed body gives `400`.
- **Login**
  - Logging in by email, by username and by username in a different case each give `200` with a cookie
    (`HttpOnly`, `SameSite=Strict`, `Max-Age` = TTL).
  - A wrong password gives `401`, and an unknown email or username gives `401`, with the same body in both
    cases.
  - An unconfirmed user with the correct password gets `403`.
  - An unconfirmed user with a wrong password gets `401`, never `403`.
- **Logout:** `204` with a clearing cookie, with or without being logged in.
- **Me**
  - `200` with claims, and no DB read.
  - `401` with no cookie, a tampered signature, an expired token, a non-UUID `sub`, or a missing `username`
    claim.
- **Security:** `401` on every other authenticated route without a cookie; `GET /actuator/health` gives `200`.

**Non-HTTP**

- **Outbox publisher (Kafka Testcontainer)**
  - A `PENDING` row lands on the topic with key `userId` and a byte-identical payload, then the row is deleted.
  - A failing send (unit test, mocked `KafkaTemplate`) increments attempts, then marks the row `FAILED` at 3,
    and a `FAILED` row is never picked up again.
  - An end-to-end check: register, run the publisher, and the record is on the topic
    (Awaitility, filtered by `userId`).
- **Cleanup:** deletes unconfirmed users older than 7 days, and their tokens; keeps unconfirmed users younger
  than 7 days, and all confirmed users; leaves the outbox untouched.
- **Unit tests:**
  - token generate/hash
  - identifier routing (email vs username)
  - the ported `TokenService`, `CookieFactory`, `RequestBodySizeLimitFilter`, `BodySizeLimitingRequestWrapper`
    and `GlobalExceptionHandler` tests
- **Concurrency**
  - N parallel registers with the same email give exactly one `201` and N−1 `409`.
  - The same for username with mixed case.
  - N parallel confirms of one token give exactly one `204`.
  - N parallel resends give exactly one new token and one outbox row.

### Steps

1. **Scaffold.** ✅ **Done** (2026-09-29)
   - Root: `docker-compose.yml` (`postgres:18`, `apache/kafka:4.3.1` KRaft on 9094, healthchecks and volumes as
     SignalFlow), `.env.example`, `.gitignore`.
   - Service: pom, wrapper, Dockerfile, `application.properties` and `application-test.properties`, and the
     `Clock` bean.
   - Copy the skills into `Twitter/.claude/skills/`.
   - Write `CLAUDE.md` (SignalFlow's, adapted: no roles, no routing, the Standing rules below),
     `DECISIONS.md` and a `TESTING.md` skeleton.
   - Test support: `AbstractPostgresIntegrationTest` and `AbstractKafkaIntegrationTest` (singleton
     containers), the mutable test clock, and a `TestUsers` factory with unique usernames and emails per call.

   **Gate:** the context-loads smoke test is green; `mvn verify` is green; `mvn spring-boot:run` starts against
   compose, and `/actuator/health` returns `200`.
2. **Test catalog.** ✅ **Done** (2026-09-29) Every HTTP scenario above is written in `AuthControllerIntegrationTest` (grouped with
   `@Nested` per endpoint), **all `@Disabled`**, and listed in `TESTING.md`.

   **Gate:** it compiles, **and you have reviewed and approved the catalog**. No feature code before this.
3. **Entities and repositories.** ✅ **Done** (2026-09-29) `CommonEntity`, `User`, `EmailConfirmationToken`, `Outbox`, `OutboxStatus`,
   and their repositories (`findByEmail`, `findByUsernameNormalized`, `existsBy…`, `lockById`, token lookup
   with lock, the newest token per user, delete by user, the bulk unconfirmed delete, the outbox page query).

   **Gate:** repository integration tests are green: normalization, both unique constraints under
   case-insensitivity, the token cascade on user delete, and the outbox page ordering.
4. **Security core (ported).** ✅ **Done** (2026-09-29) `SecurityConfiguration` (no roles), `TokenService`, `CookieFactory`,
   `CookieBearerTokenResolver`, the entry point and access-denied handler, `ErrorResponseWriter`,
   `GlobalExceptionHandler`, and the body-size filter and wrapper, each with its unit tests.

   **Gate:** the unit tests are green; the **Logout**, **Me** and **Security** catalog groups are enabled and
   green.
5. **Register.** ✅ **Done** (2026-09-29) `ConfirmationTokenService` (generate, hash), `OutboxService.enqueue`, and
   `AuthService.register` with constraint-name mapping.

   **Gate:** the **Register** group is enabled and green; the unit tests are green.
6. **Outbox → Kafka.** ✅ **Done** (2026-09-29) Producer config, `KafkaTopicConfig`, `OutboxPublisherService`, `OutboxPublisherJob`, and
   `@EnableScheduling`. Write `EVENTS.md`.

   **Gate:** the publisher tests are green (Kafka and failure paths), and so is the register → record-on-topic
   test.
7. **Confirm and resend.** ✅ **Done** (2026-09-29)

   **Gate:** the **Confirm** and **Resend** groups are enabled and green. Expiry and cooldown are driven by the
   test clock, with no sleeps.
8. **Login.** ✅ **Done** (2026-09-29)

   **Gate:** the **Login** group is enabled and green; **no `@Disabled` is left in the auth catalog**.
9. **Unconfirmed-user cleanup job.** ✅ **Done** (2026-09-29)

   **Gate:** the cleanup tests are green.
10. **Hardening.** ✅ **Done** (2026-09-29) The concurrency suite; an `exploit-hunter` audit of phase 1, where each finding is fixed or
    logged under Accepted gaps; `DECISIONS.md` and `TESTING.md` reconciled.

    **Gate:** `mvn verify` green **3× in a row**.

---

## Phase 2 — Profile ✅ **Done** (2026-09-29)

### Design

**Entities**

- `DbFile` (`db_files`): `objectKey` (unique, a random UUID string, never the client's filename),
  `contentType`, `sizeBytes`.
- `User` gains:
  - `profilePicture` and `profileCoverPicture`: `@OneToOne(LAZY)`, nullable, FK to `db_files`
  - `bio`: ≤160
  - `location`: free text, ≤30 (Q30)
  - `birthdate`: `LocalDate`, `@Past`

  All of these are optional.
- **`User` becomes `@DynamicUpdate`.** A profile edit and a picture upload racing each other can't overwrite
  each other's columns.

**MinIO (Q32)**

- **Client:** the `io.minio:minio` SDK, one `MinioClient` bean.
- **Bucket:** one private bucket, `app.minio.bucket` (`twitter-media`), created on startup by an
  `ApplicationRunner` if it's missing.
- **Config:** `MINIO_URL`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` (no defaults), `MINIO_BUCKET`.
- **`ObjectStorageService`:** `put`, `get` (stream) and `delete`. Any MinIO failure becomes
  `StorageUnavailableException`, which answers `502` "Storage unavailable.".
- **`ImageSignatureValidator`:** checks the magic bytes and ignores the client's `Content-Type` and filename.
  - JPEG `FF D8 FF`
  - PNG `89 50 4E 47 0D 0A 1A 0A`
  - WebP `RIFF????WEBP`

  Anything else answers `415` "Unsupported image type.". The detected type is what gets stored as
  `contentType`.

**Upload flow (Q26–Q27)**

- **Endpoint:** `PUT /api/v1/users/me/{profile-picture|cover-picture}`, `multipart/form-data`, field `file`.
- **Limits:**
  - **File size:** max `app.upload.max-file-bytes` (5 MB) → `413`.
  - **Request body:** the body-size filter gets a **route-specific cap** for these two paths
    (`app.request.max-upload-body-bytes`, 5 MB plus multipart overhead). Every other route keeps 8 KB.
  - **Spring multipart limits** are set to match.
- **An empty file** answers `400`.
- **Order** (the DB never points at a missing object):
  1. Validate the signature and size.
  2. `put` the new object with a new UUID key.
  3. In a transaction, save the new `DbFile`, repoint the user, and delete the old `DbFile` row.
     - If this fails, delete the new object (best effort) and rethrow.
  4. **After commit**, delete the old object. If that fails, log a `warn` and still answer `200`; the object
     becomes an orphan (an accepted gap).
- **`200`** returns the updated `ProfileResponseDTO`.
- **`DELETE /api/v1/users/me/{profile-picture|cover-picture}`** clears the pointer and deletes the `DbFile` row
  in a transaction, then deletes the object after commit (best effort). It's `204` and idempotent: `204`
  when there's no picture too.

**Serving (Q28)**

- **`GET /api/v1/files/{id}`** streams the object with its stored `Content-Type`.
- **Headers:** `Cache-Control: private, max-age=31536000, immutable`. `private` because the route needs a login;
  `immutable` because a replaced picture always gets a new id. Plus `X-Content-Type-Options: nosniff`
  (Spring Security's default).
- **Errors:** an unknown id gives `404`; a non-UUID id gives `400`.

**Profile endpoints (Q31)**

- **`GET /api/v1/users/{username}`** returns `ProfileResponseDTO` with `id`, `username`, `bio`, `location`,
  `birthdate` (visible to all), `profilePictureUrl` and `coverPictureUrl` (`/api/v1/files/{id}` or `null`),
  and `createdAt`.
  - The lookup uses `usernameNormalized`.
  - **An unknown *or unconfirmed* user gives `404`** (Q33). Unconfirmed users don't exist to anyone.
- **`PUT /api/v1/users/me`** takes `{bio, location, birthdate}`, a **full replacement**: every field is sent;
  `null` or blank clears it. Strings are trimmed, and blank becomes `null`.
  - **`200`** with the profile; **`400`** on validation failure (bio > 160, location > 30, birthdate not in
    the past).

### Scenarios (`ProfileControllerIntegrationTest`, `FileControllerIntegrationTest`)

- **Get profile**
  - `200` with the shape above; case-insensitive lookup.
  - `404` for an unknown user, and `404` for an unconfirmed user.
  - `401` without a cookie.
- **Edit profile**
  - `200` sets all fields; `200` clears them with `null` and with blank.
  - `400` for each limit and for a future birthdate.
  - It doesn't touch the pictures.
- **Upload**
  - For both slots, `200` with JPEG, PNG and WebP: the object exists in MinIO, the `DbFile` row exists, and
    the URL is in the response.
  - A replace deletes the old object and row, and gives a new URL.
  - `415` for GIF, for text renamed `.png`, and for a truncated header.
  - `413` over 5 MB; `400` for an empty file or a missing `file` part.
  - `401` without a cookie.
- **Delete picture:** `204`; the object and row are gone and the URL is `null`. `204` when none exists.
- **Files**
  - `200` returns the exact bytes, with the stored `Content-Type` and the cache headers.
  - `404` for an unknown id; `400` for a malformed id; `401` without a cookie.
- **Unit tests**
  - The signature validator, byte by byte for each format and edge case.
  - The upload service's failure ordering with a mocked storage and repository:
    - A DB failure means the new object is deleted and no pointer changes.
    - A failure deleting the old object still gives `200`.
    - A storage `put` failure gives `502` with no DB change.
- **Concurrency:** `PUT /users/me` in parallel with a picture `PUT` for the same user: both changes survive.

### Steps

11. **MinIO infra.** ✅ **Done** (2026-09-29) A `minio` service in compose; the dependencies; `MinioClient` config and the bucket
    initializer; `AbstractMinioIntegrationTest`.

    **Gate:** the "bucket created on startup, idempotent on restart" test is green.
12. **Test catalog.** ✅ **Done** (2026-09-29) Both suites **all `@Disabled`**, plus `TESTING.md`.

    **Gate:** it compiles, **and you have approved the catalog**.
13. **Entities.** ✅ **Done** (2026-09-29) `DbFile`, the `User` profile fields, `@DynamicUpdate`.

    **Gate:** repository tests are green, and phase 1 is still green.
14. **Profile read and edit.** ✅ **Done** (2026-09-29)

    **Gate:** the **Get profile** and **Edit profile** groups are enabled and green.
15. **Storage building blocks.** ✅ **Done** (2026-09-29) `ObjectStorageService` (a MinIO integration test), `ImageSignatureValidator`
    (unit test), the route-specific upload body cap, and the multipart config.

    **Gate:** their tests are green.
16. **Picture upload and delete (both slots).** ✅ **Done** (2026-09-29)

    **Gate:** the **Upload** and **Delete picture** groups plus the failure-ordering unit tests are green.
17. **File serving.** ✅ **Done** (2026-09-29)

    **Gate:** the **Files** group is green; **no `@Disabled` is left in the phase 2 catalogs**.
18. **Hardening.** ✅ **Done** (2026-09-29, finding 1 in `exploit-report-2026-09-29-phase2.md` still open) The concurrency test; an `exploit-hunter` pass over upload and serving (polyglot files,
    spoofed types, path tricks in filenames, oversized multipart, fetching other users' file ids).

    **Gate:** `mvn verify` green **3× in a row**.

---

## Phase 3 — Follows

### Design

**Entity**

- `Follow` (`follows`):
  - `follower` and `following`: both `@ManyToOne(LAZY)` to `User`, not null, `@OnDelete(CASCADE)`.
  - `createdAt`: from `Clock`, **truncated to microseconds** so it round-trips through Postgres exactly (the
    cursor depends on that).
  - Constraints:
    - `uk_follows_pair` (unique `follower_id, following_id`)
    - `@Check follower_id <> following_id`
    - index `ix_follows_following_created (following_id, created_at, id)`
    - index `ix_follows_follower_created (follower_id, created_at, id)`
- **`User` gains `followersCount` and `followingCount`**: `long`, default 0, **`updatable = false`**, so JPA
  can never write a stale value. It also gets
  `@Check followers_count >= 0 AND following_count >= 0`, so a counter bug fails loudly instead of drifting.

**Counters (Q34–Q35), in one transaction per follow or unfollow**

- **Follow:** native `INSERT INTO follows … ON CONFLICT (follower_id, following_id) DO NOTHING`. The id is
  generated in Java and `createdAt` comes from `Clock`.
  - Returns `1` → increment `following_count` on the follower and `followers_count` on the target.
  - Returns `0` → no-op.
- **Unfollow:** native `DELETE … WHERE follower_id = ? AND following_id = ?`. Returns `1` → decrement both.
  Returns `0` → no-op.
- **Counter updates are single atomic statements**, `UPDATE users SET x = x ± 1 WHERE id = ?`, one per row,
  **always in a fixed order: the two user ids sorted with `UUID.compareTo`, smaller first**. Any total order
  works, as long as every transaction uses the same one, so mutual follows can't deadlock.
- **User cleanup never touches counters.** Unconfirmed users can't log in to follow and can't be followed
  (`404`), so they never have follow rows.

**Endpoints (Q33, Q36)**

- **`PUT /api/v1/users/{username}/follow`:** `204`, idempotent.
- **`DELETE /api/v1/users/{username}/follow`:** `204`, idempotent.
- **Both:**
  - `400` "You cannot follow yourself." (checked by id before any SQL)
  - `404` for an unknown or unconfirmed target
- **`ProfileResponseDTO` gains** `followersCount` and `followingCount` (read from the columns) and
  `followedByMe` (one `exists` query; `false` on your own profile).
- **`GET /api/v1/users/{username}/followers` and `GET /api/v1/users/{username}/following`**
  - Query params: `?cursor=&size=`. Default size 20, max 100; `size` outside 1–100 → `400`.
  - Newest first. Keyset: `(created_at, id) < (:c, :id) ORDER BY created_at DESC, id DESC LIMIT size + 1`.
    The extra row tells whether a next page exists.
  - **Response:** `{items: [{id, username, bio, profilePictureUrl, followedByMe}], nextCursor}`, where
    `nextCursor` is `null` on the last page. Every item's `followedByMe` comes from **one** `IN (…)` query.
  - **Cursor:** opaque base64url of `<createdAt epoch-micros>:<id>`, handled by `FollowCursorCodec`. A
    malformed cursor gives `400` "Invalid cursor.".
  - An unknown or unconfirmed user gives `404`.

### Scenarios (`FollowControllerIntegrationTest`, plus profile count scenarios)

- **Follow**
  - `204`: the row exists; the target's `followersCount` +1; the follower's `followingCount` +1.
  - A repeat gives `204` with the counts unchanged.
  - Following yourself gives `400`. An unknown target gives `404`, and an unconfirmed target gives `404`.
  - `401` without a cookie. A case-insensitive username works.
- **Unfollow:** `204` decrements both counts. A repeat, or unfollowing someone you never followed, gives `204`
  with the counts unchanged.
- **Profile:** the counts and `followedByMe` are correct from both sides, and `followedByMe` is `false` on
  your own profile.
- **Lists**
  - Order is newest first.
  - Paging covers everything exactly once, across pages, while new follows are inserted between page
    requests.
  - The last page has `nextCursor = null`; an empty list returns `items: []` with `nextCursor = null`.
  - `followedByMe` is correct per item.
  - `size` 0 or 101 gives `400`; a garbage cursor gives `400`; an unknown user gives `404`.
- **Repository:** `ON CONFLICT` returns 1 then 0; the delete returns 1 then 0; the self-follow `@Check`
  rejects; the negative-count `@Check` rejects; the cascade works; the keyset has no gaps and no duplicates
  at equal `createdAt`.
- **Unit tests:** cursor round-trip and malformed inputs; lock ordering; the service calls counters only on a
  `1` return.
- **Concurrency**
  - 50 distinct users follow one target in parallel: exactly 50 rows and `followersCount = 50`.
  - 20 parallel follows of the same pair: 1 row, count 1.
  - A ↔ B follow and follow-back in parallel, repeated 50×: no deadlock exception, counts correct.
  - A mixed follow and unfollow storm, then the invariant `followers_count = COUNT(*)` and
    `following_count = COUNT(*)` holds for every user touched.

### Steps

19. **Test catalog.** `FollowControllerIntegrationTest` plus the new profile scenarios, **all `@Disabled`**,
    and `TESTING.md`.

    **Gate:** it compiles, **and you have approved the catalog**.
20. **Entity and native queries.** `Follow`, the `User` counters and `@Check`s, and the `FollowRepository` and
    `UserRepository` counter statements.

    **Gate:** the repository scenarios are green, and phases 1–2 are still green.
21. **Follow and unfollow.**

    **Gate:** the **Follow** and **Unfollow** groups are green, and the lock-order unit test is green.
22. **Profile counts and `followedByMe`.**

    **Gate:** the **Profile** group is green.
23. **Lists and cursor.**

    **Gate:** the **Lists** group and the codec unit tests are green; **no `@Disabled` is left anywhere**.
24. **Hardening.** The concurrency suite and invariant check; an `exploit-hunter` pass over phase 3.

    **Gate:** `mvn verify` green **3× in a row**, and the full three-phase suite is green.

---

## Test strategy (all phases, Q37–Q38)

**Tests are the contract.** They are the proof that a feature works, and the safety net for every future
change made by you or an agent.

- **Layers**
  - Unit tests (Mockito, no Spring context).
  - Repository integration tests (real Postgres).
  - HTTP end-to-end tests (full context, real Postgres, Kafka and MinIO via Testcontainers, `RestTestClient` or
    `MockMvc` as SignalFlow).
  - Concurrency tests.
  - **No embedded fakes, ever.** A running Docker daemon is a prerequisite.
- **The catalog comes first.** Each phase's HTTP scenarios are written `@Disabled` and approved by you before
  any feature code. Each feature step enables its group.
- **Determinism**
  - No `Thread.sleep`. Async work is awaited with Awaitility, with a bounded timeout.
  - Time comes from the mutable test clock.
  - Scheduled jobs are disabled in the test profile and invoked directly.
  - Every test creates unique users (`TestUsers`); no test depends on another's data or on execution order.
  - Kafka assertions consume from the topic filtered by the test's own `userId`.
  - Concurrency tests start threads on a `CountDownLatch`, so they really run concurrently, and they
    assert final state rather than timing.
- **Phase exit:** `mvn verify` green 3× consecutively. A single flaky run is a failure to investigate, not to
  retry.

## Standing rules (go into `CLAUDE.md` in step 1)

- A behaviour change ships with its test change **in the same commit**.
- **Never delete, `@Disabled`, or weaken an assertion to make a build pass without explicit approval from you.**
- Adding, removing or changing any HTTP-layer scenario updates `TESTING.md` in the same change.
- Never report a suite that didn't run (for example, no Docker) as passing.
- Carried from SignalFlow: never implement unless explicitly told; no silent assumptions; `DECISIONS.md` for
  calls made during implementation; `java-code-style` before any `.java` edit, and `java-junit` before any
  test edit.

---

## Handoff to `twitter_mail_service` (separate plan and `/grill-me`, Q8)

This plan ends at "the event is on Kafka". The mail service must:
- Consume `user.confirmation-requested` per `EVENTS.md`.
- Dedupe on `eventId`, because the outbox is at-least-once.
- Render `confirmationUrl` and `expiresAt`.
- Have the email say **"If you didn't sign up, ignore this email"**, which mitigates the squatter gap below.

Until it ships, there's no click-the-link end-to-end test. The gateway tests cover confirm by extracting the
token from the outbox payload or Kafka record.

## Out of scope

- The mail service.
- SPA pages (including `/confirm`).
- Password reset, account deletion, username change, and display names (Q16: none).
- Roles and admin (Q5).
- Birthdate at signup and age checks (Q21).
- Tweets, timelines, notifications, blocks, mutes, private accounts.
- Anonymous profile viewing (Q29).
- Refresh tokens (Q19).
- Image resizing and thumbnails.
- Spring Cloud Gateway routing.

## Accepted gaps — revisit when the named trigger lands

- **One `enabled` flag means "confirmed".** There's no way to deactivate or ban separately. **Trigger:** the
  first need to ban an account.
- **No roles or admin.** **Trigger:** moderation features.
- **No rate limiting,** except the resend cooldown. Login brute force is capped only by bcrypt-12, and register
  is unthrottled. **Trigger:** abuse in the wild, or a second instance (the throttle would need shared state).
- **Register leaks whether an email or username exists** (explicit `409`s). Resend's `202` adds no second leak.
  Login's `403` reveals "unconfirmed" only to someone who holds the correct password (by design, Q14).
- **Squatting.** A pending account holds an email and handle for up to 7 days plus the cleanup run. If the
  real owner clicks the squatter's confirmation email, they confirm an account whose password the squatter
  knows. Partly mitigated by the email's "ignore if not you" wording. **Trigger:** password reset exists
  (the owner can then take the account over), or the first reported case.
- **Email plus a live confirmation token travel through Kafka in plaintext.** **Trigger:** Kafka shared or
  exposed beyond the private network → TLS/SASL.
- **Outbox is at-least-once.** A crash between the Kafka ack and the row delete publishes the row twice; the
  mail service dedupes on `eventId`.
- **No row-claiming in the outbox poller.** Two instances can publish the same `PENDING` row. **Trigger:** a
  second instance → `SELECT … FOR UPDATE SKIP LOCKED`.
- **`FAILED` outbox rows need manual handling;** there's no replay tool. **Trigger:** the first `FAILED` row
  that matters.
- **Scheduled jobs run on every instance.** Cleanup is harmless when duplicated. **Trigger:** a second
  instance → ShedLock or an advisory lock.
- **Dev compose keeps default credentials and plaintext, unauthenticated Kafka.** Ports are bound to
  `127.0.0.1` only. **Trigger:** any shared or deployed environment → real secrets, Kafka SASL/TLS.
- **`COOKIE_SECURE` defaults to `false`** so plain-HTTP local dev works. **Trigger:** first deployment → set it
  true in the environment, or flip the default and override it locally.
- **Log lines can carry attacker-chosen or personal text:** the unknown-path WARN (authenticated callers only)
  and the constraint-violation WARN (an email in the Postgres detail). **Trigger:** logs shipped to a shared
  system → sanitise or drop the value.
- **`ddl-auto=update`, no versioned migrations.** Schema drift is possible. **Trigger:** a second deployed
  environment, or the first destructive schema change → Flyway.
- **JWT 1h, no refresh token, no server-side revocation.** A stolen token lives up to 1h, and users are logged
  out hourly. **Trigger:** hourly logouts become annoying, or a need to kill sessions → refresh tokens stored
  in the DB.
- **Orphan objects in MinIO** when deleting an old object fails after commit. **Trigger:** noticeable storage
  growth → a sweep job deleting objects no `DbFile` references.
- **Images are stored and served as uploaded:** no resize, and **EXIF metadata, including GPS, is retained**.
  **Trigger:** before any public deployment (EXIF is a privacy issue), or when page weight matters →
  re-encode on upload.
- **Images are proxied through the gateway.** Immutable caching limits this to one fetch per browser.
  **Trigger:** image traffic dominates gateway load → presigned URLs or a CDN.
- **Any logged-in user can fetch any file id.** Ids are unguessable UUIDs and every file is a profile image.
  **Trigger:** the first private media type.
- **Hot-row contention on a popular account's counters.** Concurrent follows of one user serialize on its row
  lock. **Trigger:** measurable follow latency on a popular account → sharded or batched counters.
- **No counter reconciliation job.** Correctness rests on the transactional design, and the `@Check >= 0`
  catches only negative drift. **Trigger:** any observed drift → a recompute-from-`COUNT(*)` job.

---

## Interview record (`/grill-me`, 2026-09-29)

| # | Question | Answer |
|---|---|---|
| 1 | Gateway and user service: one deployable or two? | One service: the gateway owns users |
| 2 | Username/display name in stage one? | Superseded by the user's full data model (User, DbFile, Followers; MinIO for files) |
| 3 | Plan scope? | One plan, three ordered phases: auth → profile → follows |
| 4 | Confirmed vs enabled: one flag or two? | One `enabled` flag, true on confirm (as SignalFlow) |
| 5 | Roles? | None; no admin seeding |
| 6 | Confirmation token type? | Opaque random token, SHA-256 hash stored, single-use, 24h, old ones deleted on resend |
| 7 | Who sends the email? | Outbox → Kafka → a new `twitter_mail_service` |
| 8 | Mail service in this plan? | No, separate plan; this plan ends at Kafka |
| 9 | Event contract? | `user.confirmation-requested` with a gateway-built `confirmationUrl`, key `userId`, `eventId` for dedupe |
| 10 | Link target: SPA or API GET? | SPA route + `POST /auth/confirm`. Decided on mail-scanner prefetch burning single-use tokens |
| 11 | Does confirm log in? | No, `204`; register is also `201` without a cookie |
| 12 | Never-confirmed accounts? | Daily cleanup deletes unconfirmed accounts older than 7 days |
| 13 | Resend? | Always `202`; acts only for pending users; 60s cooldown per account |
| 14 | Login before confirming? | `403` "confirm your email", only with the correct password |
| 15 | Username rules? | `[A-Za-z0-9_]{3,15}`, case-insensitive unique, stored as typed, immutable |
| 16 | Display name? | No |
| 17 | Login identifier? | Email or username |
| 18 | JWT claims / no-DB-read rule? | `sub`, `username`, `email`, `iat`, `exp`; authorization never reads the DB |
| 19 | Session length? | 1h, no refresh token |
| 20 | SignalFlow defaults (password, cookie, CSRF, logout, secret, errors, body cap, timing)? | All copied as-is |
| 21 | Birthdate? | Optional, set on the profile page (phase 2), no age check |
| 22 | Schema management? | Hibernate `ddl-auto`, no Flyway |
| 23 | `ddl-auto` consequences? | `usernameNormalized` column, `@OnDelete(CASCADE)`, `update` not `create` |
| 24 | Outbox shape? | Generic table, 3s poller, 500/batch, 3 attempts then `FAILED`, delete on success |
| 25 | Names and layout? | `twitter_api_gateway`, root compose, plan inside the service folder |
| 26 | Upload path? | Through the service (multipart), validated, 5 MB; `PUT` + `DELETE` (after a `POST`/`PUT` discussion) |
| 27 | `DbFile` and ordering? | Separate table, UUID keys; upload → DB → delete old; orphans accepted |
| 28 | Serving pictures? | `GET /api/v1/files/{id}` streamed, immutable cache |
| 29 | Who can view profiles and pictures? | Logged-in users only |
| 30 | Location enum? | Free text ≤30; bio ≤160; birthdate in the past |
| 31 | Profile endpoints? | `GET /users/{username}`, `PUT /users/me` (full replace), picture `PUT`/`DELETE`, files `GET` |
| 32 | MinIO wiring? | Official SDK, one private auto-created bucket, magic-byte check, Testcontainers MinIO |
| 33 | Follow rules? | Idempotent `PUT`/`DELETE`, no self-follow, unconfirmed users invisible (`404`) |
| 34 | Counts: compute or store? | Stored counters, which must be collision-safe |
| 35 | How to keep counters safe? | Atomic SQL ±1, only when a row changed, fixed lock order, `updatable = false`, concurrency test |
| 36 | List paging? | Cursor (keyset), default 20, max 100 |
| 37 | Test strategy? | Four layers, deterministic rules, `mvn verify` 3× per phase; tests are core |
| 38 | Step structure? | `@Disabled` catalog first, approved by you, then enabled per feature step |

**Questions that needed re-asking:**
- #10: the user was unsure between two valid options; it was decided on the single deciding factor.
- #13: re-explained in plain terms, twice for the cooldown.
- #26: the user proposed `POST` + `PUT`; after one pushback they chose `PUT` + `DELETE`.
