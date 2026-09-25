# Decisions

Non-obvious calls made *during* implementation — the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Step 1 — `app.jwt.secret` ships a committed dev-only default

Resolves `PLAN.md`'s open question #2. A short, obviously-fake default
(`dev-only-secret-do-not-use-in-prod`) is committed so `mvn spring-boot:run` works with no `.env`
setup. It is not a usable secret outside dev — a fixed, publicly-known HS256 key signs nothing
safely — so it satisfies "no secret is ever committed" (step 1's other rule) while keeping local
onboarding frictionless. Prod must set `JWT_SECRET` or authentication is trivially forgeable.

## Step 1 — `docker-compose.yml` lives at the SignalFlow root, Postgres only

Mirrors `UrlShortener`'s root-level compose file, one level above the backend module. No `app` or
`frontend` service yet — neither exists this phase (forwarding and the SPA are later phases) — so the
compose file only brings up Postgres. `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB` come from
`.env` (see `.env.example`), never a committed default, matching the Mongo/Redis credential pattern in
`UrlShortener/docker-compose.yml`.

## Step 1 — app datasource env vars (`DATASOURCE_*`) are separate from compose's `POSTGRES_*`

There's no `app` service in this compose yet to build a `DATASOURCE_URL` from `POSTGRES_*` the way
`UrlShortener`'s `app` service builds `MONGODB_URI` from `MONGO_ROOT_*`. `application.properties`
instead reads its own `DATASOURCE_URL`/`DATASOURCE_USERNAME`/`DATASOURCE_PASSWORD`, defaulting to
`localhost:5432` with the same values `.env.example` suggests for `POSTGRES_*` — so a dev who copies
`.env.example` to `.env` unchanged gets a working `docker compose up postgres` + `mvn
spring-boot:run` with no further wiring. When the `app` service is added (alongside forwarding), this
should collapse to one set of vars the way UrlShortener does.

## Step 2 — `User` lowercases its own email via `@PrePersist`/`@PreUpdate`

Both readings of PLAN.md's "unique, stored lowercased" were valid: enforce it on the entity, or leave
it to `AuthService.register` (step 5, not yet built) and treat step 2's repo test as a trivial
round-trip. Asked; chose entity-level. A `lowercaseEmail()` lifecycle callback on `User` normalizes the
field on every insert/update regardless of caller, so the invariant holds even if a future write path
forgets to lower it, and the repository test in `UserRepositoryIntegrationTest` has a real assertion
behind it (mixed-case in, lowercase out) instead of a no-op round-trip. Step 5's "lowercase the email"
note becomes a description of the outcome, not a second place doing the work.

## Step 2 — Boot 4.2.0-M1 split `@DataJpaTest`/`@AutoConfigureTestDatabase` into new artifacts and packages

Neither class lives under `org.springframework.boot.test.autoconfigure.*` (the 3.x location) anymore.
`@DataJpaTest` moved to `spring-boot-data-jpa-test` →
`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`; `@AutoConfigureTestDatabase` moved
to `spring-boot-jdbc-test` → `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase`.
Both ship transitively via `spring-boot-starter-data-jpa-test`, already in the pom — no dependency
change needed, just the new import paths. Confirms `CLAUDE.md`'s warning to verify 3.x-era APIs before
relying on them.

## Step 4 — `spring-boot-starter-webmvc` pulls Jackson 3, package renamed to `tools.jackson.*`

Another Boot 4-era relocation, same category as step 2's `@DataJpaTest` move: Jackson 3 (pulled in
transitively as `tools.jackson.core:jackson-databind`) renamed its groupId and root package from
`com.fasterxml.jackson.*` to `tools.jackson.*`. `ObjectMapper` is `tools.jackson.databind.ObjectMapper`.
Confirmed by inspecting the resolved jar directly (`unzip -l`) rather than trusting the 2.x import.
Affects `RestAuthenticationEntryPoint`, `RestAccessDeniedHandler`, `ErrorResponseWriter`.

## Step 4 — `CookieFactory` is a plain `@Component`, not a `Service`/`ServiceImpl` pair

PLAN.md names it "the cookie factory," not "the cookie service" — read literally against `CLAUDE.md`'s
service rule ("an interface `FooService` + `FooServiceImpl`"), a class not named `...Service` isn't bound
by that pattern. `CookieFactory` holds no business logic, only `ResponseCookie` formatting from
config (`app.jwt.ttl`, `app.cookie.secure`), so it stays a single concrete class in `/utilities`,
constructor-injected (no field/setter injection, so still within the DI hard rule). Same reasoning for
`CookieBearerTokenResolver`, which needs no config at all and isn't Spring-managed - `SecurityConfiguration`
instantiates it directly with `new`.

## Step 4 — `JwtDecoder`'s validator explicitly requires `sub`/`email`/`role`/`exp`, not just Spring's default

`NimbusJwtDecoder.withSecretKey(...).build()` sets no validator by default - no `exp` check at all unless
one is wired in. `JwtValidators.createDefault()` adds `iat`/`exp`/`nbf` timestamp checks, but a *missing*
`exp` still passes it (no expiry present, nothing to compare). PLAN.md step 4's gate explicitly calls for
a "missing claims" unit test, and the claims contract (`sub`, `email`, `role`, `iat`, `exp`) is pinned in
step 4's own "Decided here" section, so `jwtDecoder` composes `JwtValidators.createDefault()` with a
`JwtClaimValidator` per required claim via `DelegatingOAuth2TokenValidator`. Verified in
`TokenServiceImplTest.should_reject_a_token_missing_required_claims`.

## Step 4 — `TokenService`/`TokenServiceImpl` minted now, ahead of `AuthService` (step 5)

PLAN.md's step 4 gate needs a testable mint/parse round-trip before `AuthService` exists. Splitting
minting into its own service now (`TokenService.mint(User)`) means step 5/6's `AuthService` just calls
it rather than duplicating JWT-building logic, and `TokenServiceImplTest` can unit-test mint/expired/
wrong-signature/tampered/missing-claims without a controller or a Postgres user to hang them off.

## Step 4 — Docker port conflict confirmed pre-existing, not caused by this step

`SignalFlowApiGatewayApplicationTests.contextLoads` (`@SpringBootTest`, no Testcontainers, connects to
`localhost:5432` per its default datasource) fails in this environment: host port 5432 is already bound
by an unrelated Postgres container from another project. `../docker-compose.yml`'s `ports:` mapping is
already commented out for exactly this reason. Verified by temporarily uncommenting it and reproducing
Docker's "port is already allocated" error, then reverting. Not a step 4 regression - every Testcontainers
test (`UserRepositoryIntegrationTest`, `AuthControllerIntegrationTest`, and the new unit tests) is green;
only this one host-dependent smoke test is blocked, and only in this environment.

## Step 1 — Postgres image pinned to `postgres:18`

Latest stable major as of this phase; no compatibility constraint forces an older line the way
`mongo:8.2` was pinned in UrlShortener (SERVER-121912). Revisit if a later dependency needs otherwise.

## Step 3 — `ErrorResponseDTO` added even though it's not in step 3's DTO list

`PLAN.md`'s step 3 "Build" section only names `RegisterRequestDTO`/`LoginRequestDTO`/`UserResponseDTO`,
but the scenario table pins the error contract to "UrlShortener's `ErrorResponseDTO` verbatim" — and the
disabled test methods need a concrete type to compile their assertions against. Copied verbatim from
`UrlShortener`'s `DTOs/response/ErrorResponseDTO` (`status`, `messages`, `timestamp`). `GlobalExceptionHandler`
itself still lands in step 4 — this only adds the response shape it will produce.

## Step 3 — all four `@Nested` blocks are `@Disabled` in the catalog, not just Login/Logout/Me

`PLAN.md`'s "Everything past register carries `@Disabled`" reads ambiguously in isolation, but step 5's
gate ("Remove the Register block's `@Disableds`; green") only makes sense if Register's tests are
already disabled going into step 5. `AuthController`/`AuthService` don't exist yet in step 3, so Register
tests would fail, not just the later blocks. Each `@Test` carries its own `@Disabled("Enabled in PLAN.md
step N")` rather than one class-level annotation, matching the plural "`@Disableds`" wording in the step
5–8 gates.

## Step 3 — `Me`'s signature/expiry negative cases mint real Nimbus JWTs, not garbage strings

`should_return_401_with_a_token_signed_by_a_different_secret` and `..._with_an_expired_token` need to be
distinguishable from `should_return_401_with_a_garbage_cookie` once step 8's decoder exists — a garbage
string only exercises the parse-failure path. `PLAN.md` step 4 already pins the exact claims (`sub`,
`email`, `role`, `iat`, `exp`) and the HS256/shared-secret scheme, so there's nothing left to guess: a
small `mintToken(secret, issuedAt, expiresAt)` helper in the test uses `com.nimbusds.jwt.SignedJWT`
directly (already on the classpath via `spring-boot-starter-oauth2-resource-server`) to build one. This
duplicates what will likely become step 4's `NimbusJwtEncoder` usage, but that bean doesn't exist yet
in step 3 — revisit at step 8 and switch to the app's own encoder if that removes the duplication.

## Step 7 — `AuthControllerIntegrationTest` needs `@AutoConfigureMockMvc` alongside `@AutoConfigureRestTestClient`

Discovered while enabling the `Logout` block: a garbage cookie and no cookie at all produced the *same*
404 on `GET /api/v1/auth/me`, not 401 - meaning Spring Security's filter chain never ran. Root cause:
`RestTestClientTestAutoConfiguration.getBuilder()` falls back to `RestTestClient.bindToApplicationContext(...)`
whenever no `MockMvc` bean and no live server exist in the context, and that path does not register any
servlet `Filter` beans (including `springSecurityFilterChain`) the way Boot's `@AutoConfigureMockMvc`
does. `UrlShortener` never hit this because it has no security layer to miss. Fix: add
`org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` (Boot 4.2's relocated package)
next to `@AutoConfigureRestTestClient` on `AuthControllerIntegrationTest`, so `RestTestClientTestAutoConfiguration`
finds that pre-built, filter-carrying `MockMvc` bean (`hasBean(applicationContext, MockMvc.class)`) and
binds to it instead. Verified with a throwaway probe test: same garbage cookie now returns 401 with the
annotation present, 404 without it. Without this, none of the suite's 401 assertions (this step's, and
step 8's) actually exercise Spring Security - they'd pass or fail on routing alone.

## Step 7 — `should_prevent_the_old_cookie_from_authenticating_me` rewritten to drop the cookie, not resend it

The scenario as written in step 3 resent the exact raw JWT captured at login on the post-logout `/me`
call and expected 401. That can never pass: `PLAN.md` step 4 and the Known gaps section both commit to
best-effort logout with **no server-side revocation** - an unexpired token authenticates regardless of
logout, by design. Asked; user chose testing the cookie-jar path over removing the scenario or adding
revocation. Renamed to `should_prevent_a_browser_that_dropped_the_cleared_cookie_from_authenticating_me`
and reworked to not resend any cookie on the `/me` call - modelling a real browser, which stops sending
`access_token` once it receives the `Max-Age=0` clearing cookie. This still needs the `@AutoConfigureMockMvc`
fix above to hit the 401 path instead of a 404 from the unmapped `/me` route (step 8 isn't built yet).

## Step 8 — `createdAt` added as a new JWT claim, not read from the DB in `/me`

Step 4 pinned the claims contract to `sub`/`email`/`role`/`iat`/`exp` — no `createdAt`. But
`UserResponseDTO` (the `/me` response shape, pinned in step 3) carries `createdAt`, and CLAUDE.md's hard
rule is "no DB read on an authenticated request — authorization comes from the JWT claims alone." Step
8's own build note ("read the authenticated principal's claims, return `UserResponseDTO`") only makes
sense if the token carries everything the DTO needs. `TokenServiceImpl.mint` now also stamps
`createdAt` (`user.getCreatedAt().toString()`, ISO-8601) and `AuthController.me` reads it back via
`jwt.getClaimAsInstant("createdAt")`. Stored as a string, not epoch seconds — epoch seconds truncate to
whole seconds and `should_return_the_callers_id_email_role_and_created_at` asserts exact equality with
the DB value (which carries microsecond precision from Postgres); `Instant.toString()`/`Instant.parse()`
round-trips without losing precision, and Spring Security's `ClaimConversionService` already falls back
to `Instant.parse()` for a string claim it can't read as an epoch number. Not added to the required-claims
validator: a missing `createdAt` doesn't enable an auth bypass the way a missing `role`/`sub` would — it
would just null out one response field — and the only party able to mint a validly-signed token is this
gateway itself.

## Step 9 — Audit findings provisionally logged as Known gaps, not fixed inline

`exploit-hunter` found three real, demonstrable issues (`exploit-report-2026-09-22.md`): a login timing
side-channel that leaks whether an email is registered despite the generic 401 body (undermines step 6's
explicit anti-enumeration goal), a missing `@Size` cap on `LoginRequestDTO` letting an anonymous client
force oversized input into bcrypt, and a check-then-act race in `register` (`existsByEmail` then `save()`)
that can surface as an unhandled `500` instead of the documented `409` under concurrent identical
registrations. All three are cheap, well-scoped fixes, but CLAUDE.md's autonomy rule ("never implement
unless explicitly told") and the `exploit-hunter` skill's own report-only stance both say not to patch
without asking first — this only becomes a "fold into `DECISIONS.md`" entry once a fix actually lands.
Logged into PLAN.md's Known gaps for now so step 9's gate (audit run, findings recorded) is satisfied
either way; move each one from Known gaps to its own dated entry here if/when fixed.

## Post-step-9 — PLAN.md's five open questions resolved

1. **Cookie name stays `access_token`.** The neutral-name alternative was only ever a marginal benefit
   (PLAN.md's own wording); not worth the churn.
2. **`app.jwt.secret` default removed — supersedes the step 1 entry above.** `application.properties`
   now reads `${JWT_SECRET}` with no fallback, so the app refuses to start without it set in the
   environment. This is a deliberate reversal of step 1's "committed dev-only default" call: asked
   directly, chosen for consistency with step 1's own rule ("no secret is ever committed") over dev
   convenience. Consequence: `mvn spring-boot:run` and `mvn test` both need `JWT_SECRET` exported in the
   shell first — `.env` is not read into the app process (see the "app datasource env vars" entry below
   for why — same limitation applies here, no auto-loading was added).
3. **Domain confirmed: `ADMIN`/`USER` stays sufficient.** First real detail on what a "signal" is —
   users subscribe to `interestTopics`, created only by admins; each day a notification goes out to
   every subscriber of a topic. Nothing in that shape needs a third role yet.
4. **Admin seeding: `DatabaseLoader implements CommandLineRunner`.** Runs once on startup, checks
   whether an admin already exists, creates one if not. Replaces the "manual SQL, no runbook" gap.
   Register-created users are unaffected — still always `USER`.
5. **Prod `Caddyfile` reuses UrlShortener's `{SERVER_IP}.nip.io` approach.** No domain purchase needed;
   consistent with the sibling project.

## Post-step-9 — `DatabaseLoader` built

Item 4 above, implemented. `configurations/DatabaseLoader` (constructor-injected `AuthService` +
`@Value("${app.admin.email}")`/`@Value("${app.admin.password}")`, matching `TokenServiceImpl`'s style
for mixing a bean with config values rather than `@RequiredArgsConstructor`) runs on every startup.
`app.admin.email`/`app.admin.password` default to empty strings (`${ADMIN_EMAIL:}`/`${ADMIN_PASSWORD:}`)
— **both optional**, unlike `app.jwt.secret`: leaving them unset is a valid, common case (most local runs
don't need an admin yet), so the loader logs a `WARN` and no-ops rather than failing startup. Business
logic (the existence check via a new `UserRepository.existsByRole`, hashing, and the save) lives in
`AuthService.ensureAdminExists` — not in `DatabaseLoader` itself — matching the controller/service split:
the loader only reads config and delegates, same as a thin controller. Not merged into `AuthService
.register`: that method hard-codes `Role.USER` and is tied to the register HTTP contract (auto-login,
duplicate-email 409); admin seeding needed different exists-check semantics (by role, not email) and
different failure behaviour (silent no-op, not an exception) so it stayed a separate method rather than
a parameterized one. Covered by `AuthServiceImplTest.EnsureAdminExists` and `DatabaseLoaderTest`.

## Post-step-9 — all three exploit-hunter findings patched

Asked one by one; all three chosen to patch now over staying as Known gaps
(`exploit-report-2026-09-22.md`).

1. **Login timing oracle (#1).** `AuthServiceImpl.login` now calls `passwordEncoder.encode(request
   .getPassword())` on the unknown-email path before throwing `InvalidCredentialsException`, burning the
   same bcrypt cost as the known-email path's `matches()` call. Simpler than a precomputed dummy-hash
   field (no `@PostConstruct`, no extra state on a singleton bean) — `encode()` and `matches()` cost the
   same for a given bcrypt strength regardless of which string is hashed. Covered by
   `AuthServiceImplTest.Login.should_encode_a_dummy_password_and_throw_when_the_email_is_unknown` (mocks
   `PasswordEncoder`, asserts `encode` is called and `matches` never is) — a unit test, not a wall-clock
   assertion in the e2e suite, because timing thresholds in an HTTP-layer test are inherently flaky.
2. **No size cap on `LoginRequestDTO` (#2).** Added `@Size(max = 254)` on `email` and `@Size(min = 8, max
   = 72)` on `password`, matching `RegisterRequestDTO` exactly. `min = 8` is safe for login specifically
   because register already enforces the same floor — no existing real password can be shorter. Covered
   by two new `AuthControllerIntegrationTest.Login` scenarios (`should_return_400_for_a_password_under_8
   _characters`, `should_return_400_for_a_password_over_72_characters`); `TESTING.md` updated in the same
   change per `CLAUDE.md`.
3. **Duplicate-email registration race (#3).** `GlobalExceptionHandler` gained a
   `DataIntegrityViolationException` → `409` handler, reusing `DuplicateEmailException`'s message (now a
   public `MESSAGE` constant, so both paths present an identical error body to the client). Covered by a
   new unit test (`GlobalExceptionHandlerTest`) asserting the mapping directly, rather than an e2e test
   that fires two concurrent identical registrations — that would only *sometimes* land both requests
   inside the `existsByEmail`-then-`save()` window, making the test flaky for no extra coverage: the
   change is entirely in the exception mapping, which the unit test exercises deterministically.
   `AuthServiceImpl.register` itself is unchanged; the race and its window still exist, only the response
   code for the losing request changed from `500` to `409`.

## Post-step-9 — `@Pattern` regex added to `RegisterRequestDTO`/`LoginRequestDTO`, reversing PLAN.md's #18

`PLAN.md`'s interview decision #18 explicitly chose no composition rules and no email regex beyond
`@Email`, for reasons laid out only in conversation (not previously written down): composition rules
push users toward predictable patterns without raising real entropy (NIST SP 800-63B), and a custom
email regex on top of `@Email` risks false-rejecting valid addresses. Asked directly whether to reverse
that call; user chose to add both anyway. Landed as:

- **Password** — `@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$")` alongside the existing
  `@Size(min = 8, max = 72)`, requiring at least one lowercase letter, one uppercase letter, and one
  digit. No symbol requirement (asked; user chose this over adding one).
- **Email** — `@Pattern(regexp = "^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")` alongside `@Email` (register) or
  alone (login, which never carried `@Email` — see the exploit-hunter size-cap fix above). Catches what
  `@Email` alone doesn't: Hibernate Validator's `@Email` accepts a domain with no top-level domain at
  all (`user@localhost` passes `@Email`, fails this `@Pattern`).

Applied identically to both DTOs, matching the existing "`LoginRequestDTO` mirrors `RegisterRequestDTO`'s
constraints" pattern from the size-cap fix. Covered by four new scenarios per endpoint in
`AuthControllerIntegrationTest` (missing uppercase/lowercase/digit, no-TLD email); `TESTING.md` updated
in the same change. `AuthControllerIntegrationTest.PASSWORD` changed from `password123` to `Password123`
so the happy-path tests still satisfy the new composition rule; the `wrong-password` literal in
`should_return_a_generic_401_for_wrong_password` became `WrongPassword123` for the same reason - it only
needs to differ from `PASSWORD`, not violate the new format rule and return 400 instead of the intended
401.

## Subscriptions step 2 — the cap is a property, and the test profile lowers it to 3

`app.subscriptions.max-per-user=${MAX_SUBSCRIPTIONS_PER_USER:100}` in `application.properties`, with
`src/test/resources/application-test.properties` overriding it to `3`. Same precedent as
`app.security.bcrypt-strength=4` there: the e2e limit scenario fills the cap in three requests instead
of a hundred, keeping the suite fast without weakening what it proves.
`SubscriptionControllerIntegrationTest` reads the value back with `@Value` and loops to it rather than
hard-coding `3`, so the scenario stays correct at any cap. The shipped default is untouched at 100.

## Subscriptions step 3 — `handleDataIntegrityViolation` went neutral, superseding post-step-9 entry #3

That earlier entry recorded the global handler answering *every* integrity violation with
`DuplicateEmailException.MESSAGE`. With a second unique constraint in play (subscriptions), that would
have told a losing double-subscribe "An account with this email already exists." The message is now the
neutral *"The request conflicts with existing data."* and each service translates its own constraint:
`SubscriptionServiceImpl.subscribe` and, newly, `AuthServiceImpl.register` both wrap their `save` in
`catch (DataIntegrityViolationException) → <domain exception>`. Register's 409 body is byte-identical to
before (the whole `AuthControllerIntegrationTest` suite is still green); the global handler is now a
genuine fallback rather than a second place encoding the email contract.

## Subscriptions step 4 — the e2e suite duplicates the auth suite's registration helper

`SubscriptionControllerIntegrationTest.registerAndGetCookie` repeats what
`AuthControllerIntegrationTest` does inline, rather than extracting a shared helper into `support/`.
Deliberate, per `PLAN.md`'s interview #9: refactoring a green suite to serve a new one risks the suite
that already passes, and two callers is not yet a pattern. Extract at the third caller.

## Subscriptions step 5 — the blind `catch (DataIntegrityViolationException)` assumes the unique constraint

`SubscriptionServiceImpl` translates *any* integrity violation on `save` into
`DuplicateSubscriptionException`, but an insert can also break the `user_id` FK (SQLState 23503 rather
than 23505) — a user whose row had been deleted mid-token would be told "already subscribed". Left
undiscriminated because no user-delete path exists anywhere in the codebase, so the branch is
unreachable; discriminating on SQLState now would be untestable code guarding an impossible state.
**Revisit the moment a user-delete path lands.** Same shape applies to `AuthServiceImpl.register`, where
`users` has exactly one constraint and the assumption is safe.

## Subscriptions step 5 — audit findings logged as Known gaps, not patched

`exploit-report-2026-09-22-subscriptions.md` (named with a `-subscriptions` suffix because the auth-phase
audit already used today's date). Seven findings, none Critical or High: the cap's check-then-act
overrun, the unvalidated topic id, registration spam amplified 100× by the cap, writes from a disabled
account inside the stale-claims window, a non-UUID `sub` producing a 500, the blind catch above, and no
raw body-size cap. All five new ones are in `PLAN.md`'s subscriptions Known gaps. Not patched, matching
the post-step-9 precedent: `exploit-hunter` is report-only and CLAUDE.md's autonomy rule says to ask
before implementing. Move any that gets fixed out of Known gaps and into a dated entry here.

## Post-subscriptions — the cap race closed with a row lock on the user, not a conditional insert

`PLAN.md`'s first Fix item. `SubscriptionServiceImpl.subscribe` is now `@Transactional` and opens with
`UserRepository.lockById` — `@Lock(PESSIMISTIC_WRITE)` over `SELECT u.id FROM User u WHERE u.id = :id`,
i.e. a `SELECT ... FOR UPDATE` that serialises one user's concurrent subscribes for the rest of the
transaction. Chosen over the `INSERT ... WHERE (SELECT count(*) …) < :cap` alternative: the cap stays
expressed in Java where the exception types live, instead of becoming an affected-rows check against
SQL. The id projection (not `findById`) keeps the phase's "an authenticated request never reads the
database for its own identity" invariant — the row is locked, never hydrated. Contention is per-user, so
nothing cross-user serialises.

Two consequences worth recording:

1. **`save` became `saveAndFlush`.** `CommonEntity` generates its UUID id in Java (`@UuidGenerator`), so
   `persist` issues no immediate INSERT — under the new transaction the unique-constraint violation
   would have surfaced at *commit*, outside the `catch (DataIntegrityViolationException)`, turning the
   documented 409 into a 500. Flushing inside the transaction keeps the translation where it was.
2. **The proof is an integration test, not a unit test.** `SubscriptionServiceImplConcurrencyIntegrationTest`
   (`@SpringBootTest`, real Postgres) fires 8 concurrent subscribes to distinct topics at a cap of 3 and
   asserts exactly 3 land. Mocks cannot produce the stale count that caused the bug. Verified by probe:
   with the `lockById` call commented out the test fails (more than 3 accepted), with it present it
   passes — unlike the same-topic scenario, which the unique constraint already covered either way.

## Post-subscriptions — a non-UUID `sub` is rejected by the decoder, not by the controllers

`PLAN.md`'s second Fix item. The `sub` validator in `SecurityConfiguration.requiredClaimsValidator` went
from `Objects::nonNull` to `this::isUserId`, which also requires `UUID.fromString` to parse. Fixed at the
decoder rather than by defensive parsing in `AuthController`/`SubscriptionController`: one place instead
of every caller of `jwt.getSubject()`, and a malformed subject then fails as a clean 401 through the
existing entry point instead of an `IllegalArgumentException` → 500. Covered by
`TokenServiceImplTest.should_reject_a_token_whose_subject_is_not_a_uuid`; that class's `mintRawToken`
helper lost its `includeSubject` boolean flag for an explicit `subject` string (`null` omits it), since
the new case needs a *present but unparsable* subject.

## Post-subscriptions — `GET /api/v1/subscriptions` built now that the SPA has landed

`PLAN.md` gated this endpoint on the SPA existing; `../frontend/` now does (Vite + TS, calling
`/api/v1/auth/*` so far), so the trigger is satisfied. Returns a plain `List<SubscriptionResponseDTO>`
— the same shape `POST` already returns — ordered newest-first by `createdAt`, with no pagination and no
envelope: the per-user cap (`app.subscriptions.max-per-user`, 100) bounds the list by construction, so
paging would be machinery for a page that can never fill. An empty result is `200` with `[]`, never
`404`. The caller comes from the JWT `sub` via the existing `callerId(jwt)`, so the endpoint cannot be
pointed at another user. Four e2e scenarios in `SubscriptionControllerIntegrationTest.ListSubscriptions`,
`TESTING.md` updated in the same change.

Note for the next reader: `RestTestClient` (Boot 4's servlet client) has no `expectBodyList`, unlike
`WebTestClient` — the test helper reads the body as `SubscriptionResponseDTO[]` instead.

## Post-subscriptions — the raw body cap is a filter ahead of the security chain, with a counting stream behind it

`PLAN.md`'s last Fix item. `RequestBodySizeLimitFilter` (`@Order(HIGHEST_PRECEDENCE)`, so ahead of
Spring Security's `-100`) answers `413` when `Content-Length` exceeds `app.request.max-body-bytes`
(default 8 KB — the largest legitimate body is register's 254-char email plus 72-char password). Placed
before authentication deliberately: register and login are `permitAll`, so an oversized anonymous body
must die before any parsing or bcrypt work.

`Content-Length` alone is not enough — a chunked request declares none — so the filter also wraps the
request in `BodySizeLimitingRequestWrapper`, whose stream counts bytes as they are read and throws
`RequestBodyTooLargeException` past the cap. That path lands in MVC, so `GlobalExceptionHandler` maps the
exception to the same `413` and the same `ErrorResponseDTO` body the filter writes directly. Two paths,
one contract.

Rejected: Tomcat's `maxPostSize` (governs form encoding only, not `application/json`) and
`spring.servlet.multipart.max-request-size` (multipart only). Neither covers the JSON bodies this gateway
actually takes.

Also worth recording: **Spring 7 renamed `HttpStatus.PAYLOAD_TOO_LARGE` to `CONTENT_TOO_LARGE`.** The old
constant still compiles but is a *different* enum entry, so an `isEqualTo(PAYLOAD_TOO_LARGE)` assertion
fails against a response carrying `CONTENT_TOO_LARGE`. Same category as the Boot 4 relocations above.

## Step 1 — named volume mounts at `/var/lib/postgresql`, not `/var/lib/postgresql/data`

The 18+ `postgres` image refuses to start against a volume mounted at the old `/var/lib/postgresql/data`
path — it now stores data in a version-specific subdirectory for `pg_ctlcluster` compatibility and
treats that path as an "unused mount" containing stray data, erroring instead of initializing. Mount
the volume one level up, at `/var/lib/postgresql`; the image creates the versioned subdirectory itself.
Verified: `contextLoads` green against compose Postgres with this mount (step 1's gate).

## `handleExceptionInternal` returns a fixed message, never the exception's

`ResponseEntityExceptionHandler`'s catch-all previously echoed `e.getMessage()` for every Spring MVC
exception not explicitly overridden — an unbounded leak surface on the one service that shouldn't have
one, since any framework exception added by a future Spring version reaches it unreviewed. It now returns
a fixed `"The request could not be processed."` and logs the real message at `warn`.

The exceptions whose messages are genuinely useful to a client are overridden individually with curated
strings (`405`, `415`, `404`), so the fixed fallback costs nothing on the paths that matter. Each override
passes the inherited `headers` through — that's what preserves the `Allow` header RFC 9110 requires on a
`405`, which a hand-rolled `@ExceptionHandler` would silently drop.

Probe logging for scanner traffic was considered and **not** added here: `anyRequest().authenticated()`
means an unauthenticated probe is answered `401` by `RestAuthenticationEntryPoint` and never reaches
`NoResourceFoundException`, so logging in the advice would cover almost nothing. The entry point is where
that belongs.

## Topic routing step 1 — Boot 4.2.0-M1 → 4.1.1 for Spring Cloud 2025.1.3

`spring-cloud-gateway-server-webmvc` has no train for 4.2.0-M1: 2025.1.3 covers `>=4.0.0 and <4.2.0-M1`,
and 2026.0 is only a SNAPSHOT. Chosen over staying on the milestone and proxying by hand (a `RestClient`
controller) because gateway-webmvc was already the planned mechanism. Nothing in the code changed: the 4.0
relocations that `DataJpaTest` & co. depend on are identical at 4.1.1, and the full suite (112 tests) stayed
green with no source edit. The topic service stays on 4.2.0-M1. **Revert** to 4.2 GA once 2026.0 ships.

## Topic routing step 1 — upstream timeouts are Boot's `spring.http.clients.*`, not a gateway namespace

`PLAN.md` assumed `spring.cloud.gateway.server.webmvc.http-client.*`. That namespace doesn't exist in 5.0.3:
its metadata has no timeout keys, and `GatewayServerMvcAutoConfiguration` builds its proxy from Boot's
auto-configured `RestClient.Builder`. So the timeouts are `spring.http.clients.connect-timeout` and
`spring.http.clients.read-timeout` from `spring-boot-http-client`. They apply to every Boot-built HTTP client,
not just the gateway. That matches the planned "global" scope, since the gateway has no other outbound client.
Step 4's 504 scenario proves they actually bite.

## Topic routing step 2 — WireMock client is `wiremock-standalone`, module on an alpha built for Testcontainers 1.x

`wiremock-testcontainers-module` has only alpha releases (latest `1.0-alpha-15`), compiled against
Testcontainers 1.20.6, while Boot 4.1.1 manages 2.0.5. It works against 2.0.5 (the scaffold boots and talks
to the container). If a Testcontainers bump breaks it, fall back to a plain `GenericContainer` on the same
image. The Java client for stubbing and verification is `wiremock-standalone`, not `org.wiremock:wiremock`.
The latter pulls unshaded Jetty 11, which Boot's Jetty 12 version management would override. Both are pinned
in the pom, since Boot's BOM manages neither. The container image is pinned to `wiremock/wiremock:3.13.2` to
match the client.

## Topic routing step 2 — `app.interest-topic-service.url` added ahead of the routes

The route target property (`${INTEREST_TOPIC_SERVICE_URL:http://localhost:8081}`) went into
`application.properties` in step 2, not step 3. That way the scaffold's boot check proves the
`@DynamicPropertySource` override actually replaces the default. Nothing reads it until the routes land.

## Topic routing step 3 — the upstream JDK client is pinned to HTTP/1.1

Every forward first failed with a 500 (`RST_STREAM` / `EOF reached while reading`). The JDK `HttpClient`
that gateway-webmvc uses defaults to HTTP/2 and attempts an h2c upgrade over plain `http://`. WireMock's
Jetty accepts the upgrade and then resets the stream. The fix is a
`ClientHttpRequestFactoryBuilderCustomizer<JdkClientHttpRequestFactoryBuilder>` that sets
`HttpClient.Version.HTTP_1_1`, chosen over switching to Apache HttpClient, which would be a new dependency
just to get HTTP/1.1. The real downstream (Tomcat) would probably ignore the upgrade header, but pinning makes
the hop deterministic, and HTTP/2 buys nothing on a private network. Like the timeouts, this applies to every
Boot-built imperative HTTP client, and today the gateway is the only one.

## Topic routing step 3 — routes suite grouped by concern, not `@Nested` per endpoint

`PLAN.md` said "`@Nested` per endpoint", but the agreed scenarios are cross-cutting: the authorization matrix
is parameterized over the endpoints, and fidelity checks are properties of forwarding, not of one endpoint.
So the suite is `Authorization` / `ForwardingFidelity` (plus `UpstreamFailures` in step 4), with an
`Endpoint` enum and `@EnumSource` subsets selecting the endpoints. Cookies are minted with `TokenService`
from an in-memory `User` rather than via register/login, because authorization reads only JWT claims and
promoting a user to ADMIN has no HTTP path.

## Topic routing step 3 — both path matchers use `/**` only

`/api/v1/categories/**` already matches `/api/v1/categories` under `PathPattern`, in both the route predicate
and Spring Security's matcher. So each prefix is declared once, as a shared constant in
`InterestTopicRoutesConfiguration`, and `SecurityConfiguration` reuses it. That way the route and the access
rule can't drift apart. The two list endpoints in the suite prove the bare path is covered.

## Topic routing step 4 — every `ResourceAccessException` is 502 except a read timeout (504)

The design named "connection refused or unknown host → 502". The handler is broader: any transport failure
from the proxy (refused, unknown host, reset, connect timeout) is 502 `"Upstream service unavailable."`, and
only an `HttpTimeoutException` that isn't an `HttpConnectTimeoutException` is 504. A connect timeout means the
upstream is unreachable, not slow, so it belongs with the refused connection. The catch on
`ResourceAccessException` is safe because the gateway proxy is the app's only outbound client. The real cause,
host included, goes to the log at `warn`, never to the body.

## Topic routing step 4 — the 502 scenario gets its own context through a nested `@DynamicPropertySource`

The route URL is fixed when the context starts, so "connection refused" needs a context whose route points at
a closed port. `UnreachableUpstream` declares its own `@DynamicPropertySource`, which overrides the base
class's WireMock URL and gives that one nested class a separate cached context. That keeps it in the single
suite `PLAN.md` asked for, rather than a second test class. The port comes from a `ServerSocket(0)` that is
immediately closed. The test profile's read timeout is `1s`, so the 504 scenario doesn't wait out the 10s
default.

## Post-topic-routing — a chunked body over the cap answers 413 from the upstream-failure handler

Fixes exploit report 2026-09-23 #1. On a forwarded route, the body is only read while the proxy streams it
upstream, so `BodySizeLimitingRequestWrapper`'s `RequestBodyTooLargeException` arrives wrapped in a
`ResourceAccessException`. `handleUpstreamFailure` now checks `e.contains(RequestBodyTooLargeException.class)`
first and answers the same 413 as the controller routes, without the upstream-blaming `warn`. It's covered by a
unit test on the observed chain (`ResourceAccessException` → `IOException` → `RequestBodyTooLargeException`),
not by a routes-suite scenario. MockMvc always knows the content length, so a chunked request never reaches
the wrapper there, and a real-port test class just for this was judged not worth a second context. It was
verified once against a real Tomcat port with a throwaway probe: 40 KB chunked → 413, nothing forwarded.

## Post-topic-routing — the topic routes get their own body cap, 32 KB

Fixes exploit report 2026-09-23 #3. `RequestBodySizeLimitFilter` now picks its limit per request.
`app.request.max-topic-body-bytes` (default 32768) applies to paths matching `CATEGORIES_PATH` /
`INTEREST_TOPICS_PATH` (the same constants the routes and security rules use), and `app.request.max-body-bytes`
(8 KB) applies everywhere else. It's sized for the worst case of the topic service's character limits:
4000 + 1000 + 100 characters, each JSON-escaped as `\uXXXX` at 6 bytes, is ~30.6 KB. This was chosen over
raising the global cap, which would also have widened the anonymous register/login bodies. The filter runs
before the firewall, so a crafted path like `/api/v1/interest-topics/../auth/register` gets the 32 KB cap, but
the firewall then rejects it, so the most it gains is a 32 KB read.

## 2026-09-23 — Subscription reconciliation (steps 2–4)

- **Keyset query is native SQL, and paging follows the database's order, never Java's.** Postgres orders
  `uuid` bytewise, `UUID.compareTo` by signed longs, so the two disagree. The service only ever passes the
  last id a page returned as the next `after`, starting from the nil UUID, which sorts first in Postgres.
- **The delete is a `@Modifying` JPQL bulk delete**, not a derived `deleteBy...`, which would load every
  row first. It carries its own `@Transactional`, which gives the plan's one transaction per batch.
- **A short batch ends the run** without one more, necessarily empty, page query.
- **The client is `InterestTopicLookupService`, built on a `RestClient` bean** from Boot's
  `RestClient.Builder` (`InterestTopicServiceClientConfiguration`), so it picks up `spring.http.clients.*`
  and the HTTP/1.1 customizer the proxy uses. Every `RestClientException` (non-2xx, timeout, refused
  connection, unreadable body) and a body without `existingIds` becomes `InterestTopicLookupFailedException`,
  which the reconciliation reads as "stop", never as "none exist".
- **The client DTOs keep the `RequestDTO`/`ResponseDTO` naming from the call's point of view**:
  `ExistingInterestTopicsRequestDTO` is what the gateway sends, `ExistingInterestTopicsResponseDTO` what it
  gets back. That reverses the direction of every other DTO here, and each class's JavaDoc says so.
- **The test profile disables the cron** (`app.subscriptions.reconciliation.cron=-`); the tests call the
  service directly.

## 2026-09-23 — `GET /api/v1/feed` (dashboard feed)

- **A real endpoint, not a route.** Only the gateway knows a user's subscriptions, so it loads their topic
  ids and sends them to the topic service's `POST /internal/v1/interest-topics/feed` with a mode
  (`SUBSCRIBED`→`INCLUDE`, `NOT_SUBSCRIBED`→`EXCLUDE`). It lives at `/api/v1/feed`, outside
  `/api/v1/interest-topics/**`, so it can't collide with the forwarding route or its ADMIN rule.
- **Keyset cursor (`after` = last topic name), not page numbers**, so toggling a card mid-list never skips
  or repeats one on "Next". Filter values are the enum's uppercase names; lowercase is a 400.
- **Counts come from the topic service** (`total`, and `matching` = subscribed ids that still exist), not
  from `countByUser_Id`. That keeps `all = subscribed + notSubscribed` true while a deleted topic's
  subscription awaits the 05:00 reconciliation.
- **Own item DTO (`FeedTopicResponseDTO`) with `subscribed`**, so the SPA needs no second call. It carries
  no `prompt` or `createdAt`. The topic service's shapes are mirrored as `InterestTopicResponseDTO` /
  `InterestTopicFeedResponseDTO` and named from the call's point of view, like the existence lookup's.
- **Failure is `InterestTopicFeedUnavailableException` → 502**, kept separate from
  `InterestTopicLookupFailedException`, whose message and "stop the run" meaning belong to reconciliation.
  A timeout is 502 here too, not the proxy's 504: one outbound call, one status.
- **Invalid `size`/`after` fall to `handleExceptionInternal`'s fixed 400 message** (Spring's built-in method
  validation), with no dedicated override.

## 2026-09-24 — Topic-news notification fan-out (step 1)

- **Property names for the new topic mirror the topic service's own pattern**: `app.kafka.notification-requested.name` /
  `.partitions`, alongside the existing `app.kafka.topic-news.name` naming the inbound topic. The subscriber
  keyset batch size is `app.subscriptions.notification-fanout.batch-size` (default 500), grouped under the
  existing `app.subscriptions.*` prefix since it reads the `Subscription`/`User` tables, not a Kafka concept.
  Listener concurrency uses Boot's own `spring.kafka.listener.concurrency`, not a custom key, since no
  `@KafkaListener` needs a concurrency different from the container factory default yet.
- **The step 1 gate reuses `SignalFlowApiGatewayApplicationTests`** (now extending the new
  `AbstractKafkaIntegrationTest` instead of `AbstractPostgresIntegrationTest`) rather than adding a second,
  near-duplicate context-loads test — one smoke test already proves the whole context, `KafkaTopicConfig`'s
  `NewTopic` bean included, comes up against a real broker.
- **`AbstractKafkaIntegrationTest` extends `AbstractPostgresIntegrationTest`**, adding Kafka the same way
  `AbstractInterestTopicServiceIntegrationTest` adds WireMock: one inherited Postgres, tests opt into Kafka
  only when they need it.
- **The topic service's `DECISIONS.md` had two stale `localhost:9092` mentions**, not the one `PLAN.md`
  named — its own `application.properties` default was already `9094` before this phase, so a second Step 7
  entry (the `AbstractIntegrationTest.KAFKA` bootstrap-address note) was quoting a value that hadn't matched
  the code for a while. Both are now corrected to `9094`.

## 2026-09-24 — Topic-news notification fan-out (step 2)

- **The subscriber keyset query is a Spring Data interface projection (`EnabledSubscriberProjection`,
  in `/repositories`), not a DTO or native SQL.** `PLAN.md`'s design calls for "a JPQL projection", and an
  interface projection is JPA's idiomatic way to select a handful of columns without loading the entity -
  `/DTOs/response` didn't fit since this is never an HTTP payload. `s.user.id > :after` and
  `ORDER BY s.user.id ASC` still compile to a plain SQL `>`/`ORDER BY` on the `uuid` column, so Postgres's
  byte-order keyset guarantee holds exactly as it does for `findDistinctInterestTopicIdsAfter`'s native query.

## 2026-09-24 — Topic-news notification fan-out (step 3)

- **A failed send is wrapped in a plain `IllegalStateException`, not a new domain exception type.** Nothing
  catches it by name - it only needs to leave `notifySubscribers` unchecked so the future `@KafkaListener`'s
  `DefaultErrorHandler` sees the record as failed and retries the whole thing, so a dedicated exception class
  would add a type nothing discriminates on.
- **`TopicNewsNotificationServiceImpl` logs one INFO summary per event (count sent), not one line per
  subscriber.** A single topic-news record can fan out to hundreds of subscribers; a per-send log line at
  that volume would drown everything else. Per-send failure detail isn't logged here either, since the send
  exception already carries the subscriber id and propagates to whatever logs it next.
- **Short-batch-ends-the-run mirrors `SubscriptionReconciliationServiceImpl`**: a page smaller than the
  configured batch size skips the otherwise-guaranteed-empty next query, same optimization, same shape.

## 2026-09-24 — Topic-news notification fan-out (step 4)

- **The dead-letter topic is `topic-news.generated-dlt`, not `topic-news.generated.DLT`.** The original
  design (this file's step 1 design note, `PLAN.md`'s design section) assumed `.DLT`, but
  `DeadLetterPublishingRecoverer`'s actual default destination resolver appends `-dlt` (lower-case, hyphen) -
  confirmed by reading its source and by a test that failed against the assumed name. Kept Spring's default
  rather than overriding it with a custom destination resolver, per an explicit call: less code, and nothing
  outside this phase depends on the exact name yet.
- **`KafkaConsumerConfiguration` needs its own `ConsumerFactory<String, TopicNewsEventDTO>` and
  `ConcurrentKafkaListenerContainerFactory`, not the properties-only `spring.kafka.consumer.*` path.** An
  `ErrorHandlingDeserializer` wrapping a `JacksonJsonDeserializer` bound to `TopicNewsEventDTO` needs a real
  object (type headers are off, so there's no header to infer the target type from); passing that in through
  `spring.kafka.consumer.properties.spring.deserializer.value.delegate.class` etc. silently never reaches
  the consumer's built config (confirmed by its absence from the consumer's own logged `ConsumerConfig`
  dump, even though the analogous producer-side property does show up) - not chased further since building
  the factory directly sidesteps it entirely. Declared with concrete generics
  (`<String, TopicNewsEventDTO>`), not Boot's own `<Object, Object>` convention, since going through
  `ConcurrentKafkaListenerContainerFactoryConfigurer.configure(...)` (which requires exactly
  `<Object, Object>`) would have forced unchecked casts back the other way for no benefit here.
- **Both hand-built factories (`KafkaConsumerConfiguration`'s consumer factory and
  `KafkaErrorHandlingConfiguration`'s dead-letter producer factory) must read `bootstrap.servers` from the
  `KafkaConnectionDetails` bean, not from `kafkaProperties.build*Properties()` alone.** That method only
  ever returns the static `spring.kafka.bootstrap-servers` property text; `@ServiceConnection` overrides the
  address for Boot's own auto-configured factories through `KafkaConnectionDetails`, which a hand-built
  factory bypasses unless it consults that bean itself. Caught because the static default (`localhost:9094`)
  happened to reach an unrelated, real Kafka broker already running on the test machine, so the app
  connected successfully but to the wrong cluster - the listener never saw any record the test produced to
  the real Testcontainers broker, with no error anywhere to point at the mismatch. Same fix applies
  everywhere else a `ConsumerFactory`/`ProducerFactory` is hand-built from `KafkaProperties` instead of
  obtained from Boot's own auto-configuration.
- **`KafkaConsumerConfiguration`'s test gate pre-creates `topic-news.generated`** (1 partition, via a raw
  `AdminClient` in a static `@BeforeAll`, before the Spring context exists) instead of relying on Kafka's
  auto-create. In production the topic service owns this topic's creation via its own `NewTopic` bean; a
  gateway-only e2e test has nothing to create it for real, and leaving it to auto-create makes the fan-out
  assertions depend on whatever partition count the broker defaults to.

## 2026-09-24 — Topic-news notification inbox/outbox (step 1)

- **`NotificationOutbox` extends `CommonEntity`** (surrogate `@UuidGenerator` id, `createdAt`/`updatedAt`),
  matching the plan's `created_at` column and every other entity's id strategy. `TopicNewsInbox` does not:
  its primary key is `newsId` itself (the event's own id), so it declares a plain `@Id UUID newsId` with no
  generator - that's the whole point of using the event id as the collision key for a redelivered event.
- **`NotificationOutbox` carries a unique constraint on `(news_id, user_id)`** and an index on
  `(status, created_at)`, the exact shape the poller queries by. Not load-bearing for correctness (the
  inbox row is what actually prevents a duplicate fan-out), but it documents the table's grain the same way
  `Subscription`'s `(interest_topic_id, user_id)` constraint does, and the poller's hot query needs the index.
- **An assigned-id entity's `JpaRepository.save()` is `merge()`, not `persist()`, and that matters here.**
  `TopicNewsInbox.newsId` is set before `save()` is ever called, so Spring Data's default `isNew()` check
  (id present -> not new) always routes through `entityManager.merge()`. Merge does a `SELECT` by id first,
  and if a row already exists it's a silent `UPDATE`, not a duplicate-key failure - so relying on the unique
  constraint to reject a second insert doesn't hold, and a `save()`-based insert always costs an extra
  round trip. Step 2's transactional insert of the inbox marker uses a raw `JdbcTemplate.update()` instead,
  same as the outbox batch insert, sidestepping this entirely; the repository's `save()`/`findById()` are
  only exercised by tests and by nothing else in production code.

## 2026-09-24 — Topic-news notification inbox/outbox (step 2)

- **Both writes go through the same `JdbcTemplate`, not the outbox through JDBC and the inbox through the
  repository.** Consistency over the mixed approach: one write mechanism for the whole transaction, and it
  sidesteps step 1's `save()`-is-`merge()` surprise for the assigned-id `TopicNewsInbox` entirely.
- **Timestamps are bound as `OffsetDateTime` (UTC), not `java.sql.Timestamp`.** All four timestamp-shaped
  columns (`created_at`, `updated_at`, `generated_at` on the outbox row) are `timestamptz`; JDBC 4.2 formally
  maps `OffsetDateTime` to `TIMESTAMP WITH TIME ZONE`, avoiding any JVM-default-timezone ambiguity a
  `java.sql.Timestamp` binding would carry. `news_date` (`date`) binds `LocalDate` directly, same reasoning.
- **`TopicNewsNotificationServiceImplTest` became a `@DataJpaTest` (real Postgres, `@Transactional` rollback
  per test), not a Mockito unit test mocking `JdbcTemplate`.** The batch insert's exact column/parameter
  wiring is the thing most likely to break, and a Mockito mock of `JdbcTemplate.batchUpdate(...)` would only
  prove the call happened, not that the SQL is correct - the plan's "assert DB state" wording is read
  literally. `@Transactional` on the service method itself has no effect in this test, since the service is
  constructed with `new`, not resolved through the Spring context - the surrounding `@DataJpaTest` transaction
  is what makes both the JPA repository calls and the raw JDBC calls (same `DataSource` bean) participate in
  one rollback-per-test unit.
- **`TopicNewsListenerIntegrationTest` gained an `@AfterEach` cleanup** (all four tables), not just the
  existing `@BeforeEach` one. It's a `@SpringBootTest`, so nothing rolls its writes back; its last test's
  rows previously stayed committed in the shared Testcontainers Postgres for the rest of the `mvn test` run
  (one JVM, one container, reused across every test class) - found when a later `@DataJpaTest` in the same
  run collided on `bob@example.com`, already inserted (and left behind) by this test.

## 2026-09-24 — Topic-news notification inbox/outbox (step 3)

- **`NotificationOutboxPublisherJob` gets its own `NotificationOutboxPublisherService`/`Impl` pair**, not
  logic inlined in the job, matching `SubscriptionReconciliationJob`/`Service`'s split - the job stays a
  one-line `@Scheduled` delegation, the service is unit-testable without a scheduler.
- **`fixedDelayString`, not `fixedDelay` with an `@Value` int.** `@Scheduled`'s `fixedDelay` attribute
  only accepts a compile-time long constant; a property-driven interval needs the SpEL-resolved
  `fixedDelayString = "${...}"` form instead. First `fixedDelay`-family job in the codebase - every prior
  `@Scheduled` use is `cron`.
- **A publish failure is caught and logged (WARN), not rethrown.** Unlike the old synchronous listener-side
  `send()`, which deliberately threw so Kafka's `DefaultErrorHandler` would retry the whole listener
  invocation, one bad row here must not abort the rest of the batch - the design is per-row retry via
  `attempt_count`, not per-poll retry via a rethrown exception.
- **No `@Transactional` on `publishPendingNotifications()`.** Each row's Kafka send and repository call
  (`delete` or `save`) is independent; wrapping the whole batch in one transaction would hold a DB
  connection open across N synchronous Kafka round trips for no atomicity benefit, since at-least-once
  duplication is already an accepted property of this pipeline.
- **Test profile neutralizes the new poller with an oversized `fixed-delay-ms` (86400000)**, not a way to
  turn it off entirely - `fixedDelayString` has no "never" sentinel the way `cron=-` does. Needed because
  `TopicNewsListenerIntegrationTest` runs in a full `@SpringBootTest` context where a live poller could
  publish and delete the very outbox rows the test asserts on.

## 2026-09-24 — Topic-news notification inbox/outbox (step 4)

- **`InboxCleanupJob` also gets its own `InboxCleanupService`/`Impl` pair**, same reasoning as step 3's
  publisher job/service split - keeps the `@Scheduled` class a one-line delegation.
- **The cutoff is computed in the service (`Instant.now().minus(retentionDays, DAYS)`), not passed in from
  the job.** The job has no other reason to know what "retention" means as a concept; keeping the
  calculation next to the repository call it feeds keeps the job genuinely trivial.

## 2026-09-24 — Topic-news notification inbox/outbox (step 5, docs)

- **`TESTING.md` is untouched by this phase.** Its own scope line already excludes non-HTTP DB/listener
  tests (`UserRepositoryIntegrationTest`/`SubscriptionRepositoryIntegrationTest` are named as the existing
  precedent), and none of `TopicNewsInboxRepositoryIntegrationTest`, `NotificationOutboxRepositoryIntegrationTest`,
  `TopicNewsNotificationServiceImplTest`, `TopicNewsListenerIntegrationTest`,
  `NotificationOutboxPublisherServiceImplTest`, `NotificationOutboxPublisherJobTest`,
  `InboxCleanupServiceImplTest` or `InboxCleanupJobTest` is a `*ControllerIntegrationTest`/`*RoutesIntegrationTest`
  - `CLAUDE.md`'s "update `TESTING.md` in the same change" rule doesn't apply to any of them.
- **The two accepted gaps this phase set out to narrow (`PLAN.md`'s design section) came out one narrowed,
  one closed, not both narrowed.** The rebalance-risk gap turned out to close outright: moving the Kafka
  publish off the listener thread entirely (not just batching it) removes the mechanism, not just shrinks
  its window. Recorded as **Closed** rather than **Narrowed** to match the "Any UUID is subscribable"/"the
  by-topic fan-out read path" precedent for gaps a later phase fully resolves.

## 2026-09-25 — Exploit report follow-up

- **Left `exploit-report-2026-09-22-subscriptions.md` #1 as-is - not fixed, and not tracked as a
  separate accepted gap.** `interestTopicId` is still never validated against a real topic on subscribe.
  Considered a synchronous call to the topic service on every subscribe (new latency, plus a new failure
  mode with no existing HTTP mapping for `InterestTopicLookupFailedException`) but decided it buys
  nothing: the nightly `SubscriptionReconciliationJob` already hard-deletes any subscription pointing at a
  nonexistent topic within ~24h, capped at `max-per-user` rows per account in the meantime, and the feed
  endpoint already composes against whatever the topic service actually reports, so a bogus subscription
  never surfaces as a broken feed item. Real-time validation would only shrink a 24h window that's already
  invisible to users - not worth the added failure mode.
- **Rate limiting will not be implemented**, on registration, login, or anywhere else. `PLAN.md`'s "No
  rate limiting anywhere" accepted gap is no longer waiting on its stated trigger (a second instance, or
  abuse in the wild) — it's a standing decision. Closes
  `exploit-report-2026-09-22-subscriptions.md` #2 (unthrottled registration multiplies into unbounded
  subscription rows) as won't-fix rather than deferred: that finding's amplification is entirely a
  consequence of the missing rate limit, and there's no narrower fix worth doing ahead of it.
- **A disabled account keeps writing until its token expires (≤1h) — accepted, not fixed.** Closes
  `exploit-report-2026-09-22-subscriptions.md` #3. Distinct reasoning from the rate-limiting call above:
  `isEnabled` exists to let an operator disable an account, not to bound throughput, so this isn't the
  same trade-off — it's specifically the "no DB read on an authenticated request" rule (`CLAUDE.md`)
  versus wanting instant cutoff, and instant cutoff needs either a DB read per request or a server-side
  revocation store (shared state). Already tracked as `PLAN.md`'s "Claims are stale for up to an hour"
  gap with its own trigger ("needing to cut off an account immediately"); no new decision needed there,
  just closing out the exploit-report finding against it.
- **Fixed `exploit-report-2026-09-22-subscriptions.md` #4** — `SubscriptionServiceImpl.subscribe()`'s
  `catch (DataIntegrityViolationException)` now discriminates on SQLState (`e.getMostSpecificCause()`
  against `23505`) instead of assuming every violation is the unique-constraint race. A non-unique
  violation (in practice, only the `user_id` FK) is rethrown as-is rather than relabelled
  `DuplicateSubscriptionException`, so it falls through to `GlobalExceptionHandler`'s existing generic
  `DataIntegrityViolationException` handler (a neutral 409) instead of the misleading "already subscribed"
  message. `AuthServiceImpl.register`'s identically-shaped catch was left alone: `users` has no foreign
  key, only the unique email constraint, so there's no second violation type to mis-attribute there — the
  report's "same shape" note is about the code pattern, not an equivalent bug.
- **Fixed `exploit-report-2026-09-23-topic-routing.md` #2** — `InterestTopicRoutesConfiguration` now
  strips every inbound header starting with `X-User-` before forwarding to the topic service, not just
  `Cookie`/`Authorization`. Done ahead of the trust-model gap's own trigger ("the first downstream that
  needs caller identity") rather than waiting for it, since it's cheap and guards a name space nothing
  currently uses. `BeforeFilterFunctions.removeRequestHeader(String)` only removes one exact name, so this
  needed a hand-written `Function<ServerRequest, ServerRequest>` (`removeHeadersWithPrefix`) matching its
  same `ServerRequest.from(request).headers(...).build()` shape. Spring 7's `HttpHeaders` no longer
  exposes `keySet()`/`MultiValueMap`-style mutation directly (`headerNames()` returns a `Set<String>`,
  removal is by name via `remove(String)`), so the helper snapshots matching names first, then removes
  each - a live-view `removeIf` isn't available. A new `InterestTopicRoutesIntegrationTest` scenario
  (`should_strip_client_supplied_identity_headers_before_forwarding`) pins it; **not verified green in
  this session** - every `@SpringBootTest` class here (including untouched, previously-green ones) is
  currently failing to report results after Kafka connects and ~2 requests process, for reasons unrelated
  to this change (isolated: reproduces on `SubscriptionControllerIntegrationTest` too). The user will
  verify locally.
- **Fixed `exploit-report-2026-09-24-topic-news-fanout.md` #2 /
  `exploit-report-2026-09-24-notification-inbox-outbox.md` #3** - `TopicNewsNotificationServiceImpl.
  notifySubscribers` now rejects a record whose `data` exceeds a new
  `app.subscriptions.notification-fanout.max-data-length` cap (default 20 000 characters, `NOTIFICATION_
  FANOUT_MAX_DATA_LENGTH`) before doing any fan-out. Chosen well above the topic service's own
  `app.openrouter.max-tokens=2000` completion limit, so no legitimate record is ever rejected - this is a
  resource-exhaustion guard against a forged/oversized record from something on the internal network, not
  a business-rule cap. **Decided with the user:** an oversized record is logged at WARN and marked
  processed in the inbox (same row as a normal successful run), not thrown - retrying changes nothing
  since the payload is identical every attempt, so treating it as a transient failure (3 retries then DLT,
  as the still-open finding #8's oversized-`topicName` case does today) would just be retry noise. The
  durable fix remains the accepted "downstream trust model" gap (identity/authz on Kafka producers); this
  only bounds the damage until then. Verified via log/DB inspection in
  `TopicNewsNotificationServiceImplTest` (same broken surefire reporting as above - the new
  `should_queue_nothing_but_still_mark_the_news_processed_when_data_exceeds_the_length_cap` scenario's log
  line and DB state confirm it, but surefire's own summary doesn't count it).
- **Left `exploit-report-2026-09-24-notification-inbox-outbox.md` #1 as-is - not fixed.** The whole
  keyset walk staying in one `@Transactional` method is deliberate: it's what makes a crash mid-run either
  commit everything or roll back everything, which is the entire point of the inbox/outbox pattern. Fixing
  the long-transaction risk means giving that up (commit per page) or introducing an arbitrary per-run
  subscriber cap, either way a real trade-off for a problem that only bites at a subscriber count this
  project isn't near, and now harder to reach organically or artificially given rate limiting won't be
  built (see the 2026-09-25 entry above). Already tracked in `PLAN.md`'s accepted gaps with its own
  trigger ("a topic's subscriber count makes one run's transaction duration operationally noticeable");
  no change needed there.
- **Fixed `exploit-report-2026-09-24-notification-inbox-outbox.md` #2** -
  `TopicNewsNotificationServiceImpl.notifySubscribers` now also rejects a record whose `topicName` or
  `categoryName` exceeds 255 characters (`MAX_COLUMN_LENGTH`, matching `NotificationOutbox`'s default
  `varchar(255)` - a fixed DB constraint, not a tunable policy value like `data`'s cap, so no new config
  property). Same skip-and-mark-processed treatment as the `data` cap above, via a shared
  `skipOversizedRecord` helper. Unlike #6/#7 this one isn't only an internal-network attack surface - the
  report frames it as equally reachable by ordinary version drift, since `TopicNewsEventDTO`'s contract
  with the topic service has no runtime enforcement. Verified via log inspection in
  `TopicNewsNotificationServiceImplTest` (same broken surefire reporting as the `data`-cap fix; the two
  new scenarios' WARN log lines and empty-outbox assertions confirm it).
