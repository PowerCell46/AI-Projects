# WealthBuilder Backend — Quality Review

**Date:** 2026-06-21
**Scope:** Whole backend application (`com.wealthbuilder.backend`), Spring Boot 4.0.6 / Java 25.
**Method:** Layer-by-layer audit (config/security, services/repositories, web/DTOs, infra/seeding) with each finding traced back to the exact source line.

This is a **recall-biased** report: it surfaces real defects and risks a careful reviewer would flag in one sitting, ranked by severity. Each item states the location, the concrete failure scenario, and a recommended fix.

---

## TL;DR — Top priorities

| # | Severity | One-line | Where |
|---|----------|----------|-------|
| 2 | 🔴 Critical | Data seeders are not profile-gated → **prod gets mock financial data** | `DataSeeder`, `CommandLineRunnerImpl` |
| 3 | 🟠 High | `JwtSecretValidator` guards a secret string that no longer exists → **dead protection** | `JwtSecretValidator:27` |
| 5 | 🟠 High | Several common exceptions fall through to **HTTP 500** | `GlobalExceptionHandler` |
| 7 | 🟠 High | Asset-name uniqueness: TOCTOU race + DB constraint is case-sensitive | `AssetServiceImpl:59` |
| 8 | 🟠 High | Deleting an in-use asset → `DataIntegrityViolation` → 500 | `AssetServiceImpl:96` |

---

## 🔴 Critical

### 2. Data seeders run in every environment — production gets mock data
**`backend/src/main/java/com/wealthbuilder/backend/config/DataSeeder.java:24`**
**`backend/src/main/java/com/wealthbuilder/backend/CommandLineRunnerImpl.java:38`**

Both are annotated only with `@Component @Order(...)` — no `@Profile("dev")`, no `@ConditionalOnProperty`. The class JavaDoc literally says *"Development-only seeder,"* but nothing enforces it.

**Failure scenario:** In production, `CommandLineRunnerImpl` injects ~50+ fake holdings ("Apple Inc.", "Bitcoin", "MBA Program", …) into the seeded moderator's portfolio on first boot. The moderator signs in to a production account pre-polluted with mock financial data. Idempotency only prevents *duplicates* — it does not prevent the initial production seeding.

Secondary issue — **partial-seed lock-in** (`CommandLineRunnerImpl.java:100-104`): the "already seeded" check is per-asset (true if the moderator owns *any one* holding for that asset). If a prior run was interrupted after one of an asset's rows, the remaining rows are skipped forever; and a moderator who legitimately adds one holding can never be seeded for that asset.

**Fix:** Gate both seeders with `@Profile({"dev","local"})` or `@ConditionalOnProperty(name="app.seed.enabled")`. Make idempotency per-row or wrap each asset's seed in a single transaction so partial runs roll back.

---

## 🟠 High

### 3. The JWT dev-secret guard is dead code, and the shipped defaults can't boot
**`backend/src/main/java/com/wealthbuilder/backend/config/JwtSecretValidator.java:27,53`**
**`backend/src/main/resources/application.properties:26-27`**

```java
// JwtSecretValidator
private static final String DEV_DEFAULT_SECRET = "dev-only-insecure-secret-change-me-0123456789abcdef";
```
```properties
# application.properties
app.jwt.secret=${JWT_SECRET:__PLACEHOLDER__}
app.jwt.ttl=${JWT_TTL:__PLACEHOLDER__}
```

Three compounding problems:

1. **Dead guard:** the validator only refuses to start if the secret equals `dev-only-insecure-secret-...`, but the real default is `__PLACEHOLDER__`. The two never match, so the guard never fires — the comment "*Must match the dev fallback*" (line 26) is stale.
2. **Fails open on profile:** `isDevelopmentProfile()` returns `true` when **no profile is active** (`:53`). Most prod deployments run with no explicit profile, so even a matching dev secret would be treated as development.
3. **Defaults can't boot:** `__PLACEHOLDER__` is 15 bytes (< 32), so `JwtServiceImpl.ensureSecretIsStrongEnough()` throws at startup; and `__PLACEHOLDER__` is not a parseable `Duration`, so TTL binding fails too. The inline comment "*The dev default is >= 256 bits so HS256 works out of the box*" (`application.properties:24`) is factually wrong — a clean `mvn spring-boot:run` with no env vars will not start.

**Failure scenario:** A developer follows the README/CLAUDE.md, runs `mvn spring-boot:run`, and the context fails to start with a confusing key-length / duration-parse error. Separately, the protection that was meant to stop the public dev secret reaching prod provides no actual coverage.

**Fix:** Make the constant match the real default (or check by length/known-bad set), treat "no profile" as **non-dev** (fail-closed), and either ship a working dev default (≥32-byte secret + valid `JWT_TTL`, e.g. `PT2H`) or document the required env vars prominently. Add an upper bound on TTL.

---

### 5. Common exceptions fall through to HTTP 500
**`backend/src/main/java/com/wealthbuilder/backend/exceptions/GlobalExceptionHandler.java`**

The handler covers `MethodArgumentNotValidException`, the domain not-found/conflict exceptions, `AuthenticationException`, and `AccessDeniedException`. It does **not** handle:

- `MethodArgumentTypeMismatchException` — `GET /api/assets/abc/image` or `?from=not-a-date` → **500** instead of 400.
- `ConstraintViolationException` — `@RequestParam`/`@PathVariable` constraint failures → 500.
- `DataIntegrityViolationException` — e.g. deleting an in-use asset (#8) or the duplicate-name race (#7) → 500.
- `IllegalArgumentException` / `InvalidMediaTypeException` from `DataUriImage.parse` (#12) → 500.

**Failure scenario:** A client sends a non-numeric id or malformed date and receives a generic `500 "An unexpected error occurred."` for what is plainly bad input — misleading clients and inflating error metrics with false server faults.

Also note the **inconsistent contract**: only `handleValidation` adds an `errors` map; every other handler returns a bare `detail`. Clients can't rely on one schema.

**Fix:** Add handlers mapping the above to 400/404/409 as appropriate. Consider extending `ResponseEntityExceptionHandler` to cover Spring MVC's built-in exceptions consistently.

---

### 7. Asset-name uniqueness: check-then-insert race + case-sensitive DB constraint
**`backend/src/main/java/com/wealthbuilder/backend/services/implementations/AssetServiceImpl.java:59`**
**`backend/src/main/java/com/wealthbuilder/backend/entities/Asset.java:35`**

```java
// AssetServiceImpl.create — comment claims the transaction serializes concurrent creates
if (assetRepository.existsByNameIgnoreCase(request.getName())) { ... }
```
```java
// Asset
@Column(nullable = false, unique = true)  // case-SENSITIVE at the DB level
private String name;
```

The doc comment (`AssetServiceImpl:52-55`) claims the shared transaction prevents two concurrent creates with the same name. Under default `READ_COMMITTED` isolation it does **not** — both `existsByNameIgnoreCase` calls see no committed row, both `save`. The DB `unique` constraint is the only real backstop, and it is **case-sensitive**, so "Stocks" and "stocks" both insert — defeating the case-insensitive intent the service advertises.

**Failure scenario:** Two near-simultaneous `POST /api/assets {name:"Stocks"}` (or one "Stocks" + one "stocks") both succeed, producing duplicate/case-variant catalog entries.

**Fix:** Add a unique index on `lower(name)` (via migration) and catch `DataIntegrityViolationException` → 409. Drop the misleading comment.

---

### 8. Deleting an asset with holdings throws a raw integrity violation
**`backend/src/main/java/com/wealthbuilder/backend/services/implementations/AssetServiceImpl.java:96`**

`delete()` does no pre-check; `AssetHolding.asset` is `@ManyToOne(optional=false)` with a `NOT NULL` FK and no cascade.

**Failure scenario:** A moderator deletes asset id 5 while users hold purchases against it → `DataIntegrityViolationException` → (currently unmapped, see #5) **500**, with no clean "asset is in use" message.

**Fix:** Either block deletion when holdings exist (count check → 409 with a clear message) or define the intended cascade/soft-delete policy. Map the integrity violation regardless.

---

## 🟡 Medium

### 9. No optimistic locking on `Asset` — lost updates
**`AssetServiceImpl.java:75` / `Asset.java`** — `update()` is a read-modify-write with no `@Version` field. Two concurrent moderator edits → second commit silently overwrites the first (last-write-wins). **Fix:** add `@Version private long version;` to `Asset`.

### 11. No size cap on `imageBase64` and no request-body limit
**`AssetRequest.java:28`** — `name`/`description`/`imageName` have `@Size`, but `imageBase64` has only `@NotBlank` + `@Pattern`; the column is unbounded `text` and no `spring.servlet.multipart`/body-size limit is configured. A moderator POSTing a 200 MB data-URI is read fully into memory, regex-scanned, and stored. (Moderator-only, hence Medium not High.) **Fix:** add `@Size(max=...)` and a global max request size.

### 12. `DataUriImage.parse` doesn't validate the media type is an image, and can 500
**`DataUriImage.java:39`** — it trusts whatever precedes the comma and feeds it to `MediaType.parseMediaType`. The write-side `@Pattern` enforces `data:image/...`, so this is **defense-in-depth**: a value altered out-of-band to `data:text/html;base64,...` would be served back from `GET /api/assets/{id}/image` with `Content-Type: text/html` → stored-XSS if opened directly. Also, malformed text → `IllegalArgumentException`/`InvalidMediaTypeException` → 500 (see #5). **Fix:** assert `mediaType.getType().equals("image")` and map parse failures to 4xx.

### 13. No rate limiting on auth; register enables user enumeration
**`AuthController` / `AuthServiceImpl`** — `/api/auth/login` and `/register` are `permitAll` with no throttling, lockout, or CAPTCHA → BCrypt brute force is unmetered. `register` throws a distinct `UsernameAlreadyTakenException` (→ 409), letting an attacker enumerate valid usernames. (Login itself is uniform via `AuthenticationManager` — good.) **Fix:** add per-IP/username rate limiting (e.g. Bucket4j) and consider a uniform register response.

### 14. No token revocation / logout; TTL unbounded
**`JwtServiceImpl`** — no logout endpoint, no `jti`/blacklist, no per-user token version. A stolen token is valid for the full TTL even after password change; and TTL has no upper-bound validation, so `JWT_TTL=PT720H` yields month-long tokens. Per-request DB authority reload (good) handles deletion/role changes, but not an existing user's leaked token. **Fix:** short TTL + refresh tokens, or a token-version claim checked against the user.

### 15. CORS default reflects any localhost port and is baked into the jar
**`application.properties:22` / `CorsConfig`** — default `http://localhost:[*],http://127.0.0.1:[*]`. `allowCredentials` is unset (correct for bearer auth), but nothing fails loudly if `FRONTEND_BASE_URIS` is forgotten in prod, so a malicious local process could call the API from any localhost origin. **Fix:** ship no permissive default, or add a startup guard (like the JWT one *should* be) that rejects the localhost pattern under a non-dev profile.

### 16. `Money.unitPrice` has no divide-by-zero guard
**`Money.java:22`** — `boughtForAmount.divide(quantity, ...)` throws `ArithmeticException` if `quantity == 0`. `@Positive` guards the HTTP write path, so this is latent: any zero-quantity row introduced via seed/admin/future endpoint makes both the list endpoint (`HoldingResponse.from`) and `summarize` 500. **Fix:** guard or document the invariant.

---

## 🔵 Low / polish

- **`HoldingFilter` date range unvalidated** (`HoldingController.java:46-47`): `?from=2999-01-01&to=1900-01-01` silently returns empty rather than 400. Add a `from <= to` check.
- **Unused `role` claim in the JWT** (`JwtServiceImpl.java:71`): authorities are (correctly) reloaded from the DB, so the baked-in `role` claim is never read — dead, slightly misleading data. Remove it or document why it's there.
- **Image endpoint isn't cacheable** (`AssetController.java:47`): re-decodes the base64 text to `byte[]` on every request, with no `Cache-Control`/`ETag`/`Content-Length`. A carousel of N assets re-fetches and re-decodes N blobs each render. Add caching headers.
- **`HoldingSummaryResponse.empty()` scale mismatch**: returns `BigDecimal.ZERO` (scale 0) while the populated path returns scale-4/8. Any `equals`-based comparison downstream mismatches; use `compareTo` and/or a consistent scale.
- **Dockerfile builds with `-DskipTests`**: the image pipeline never runs tests, so a regression can ship unless CI runs `mvn test` separately.
- **Naming** (`CommandLineRunnerImpl`): named after the Spring interface, not its job (it seeds holdings). CLAUDE.md stresses cognitive naming — `HoldingsDataSeeder` reads better alongside `DataSeeder`.

---

## ✅ What's already solid (verified, not issues)

- **Layered package structure** matches CLAUDE.md exactly; no feature-first dirs, no `@Autowired` field injection, no Java records, constructor injection throughout.
- **Actuator is locked down**: only `health` is exposed (`application.properties:16`) and only `/actuator/health` is public.
- **Stateless JWT security** is correct: CSRF disabled (appropriate), 401 via `HttpStatusEntryPoint` (no redirects), authorities **reloaded from the DB per request** so role changes/deletions take effect immediately.
- **Holding ownership** is enforced server-side (`requireOwnedHolding`, 403 on mismatch); asset writes are `@PreAuthorize("hasRole('MODERATOR')")` with `@EnableMethodSecurity` active.
- **Passwords** are BCrypt-hashed; DTOs are write-only projections with no `@ToString` leakage; responses use DTO factories, not entities (no mass-assignment / entity leak).
- **`open-in-view=false`** is set (no lazy-loading-in-view surprises); `HoldingRequest` has solid `@Positive`/`@Digits` bounds.
- **Nimbus `MACVerifier`** rejects non-HMAC algorithms, so basic RS256→HS256 confusion is blocked (though explicit `alg` pinning would be stronger defense-in-depth).

---

## Suggested order of work

1. **Stop prod pollution:** gate seeders by profile (#2).
2. **Close the 500s:** exception-handler gaps (#5), asset-name index + delete guard (#7, #8).
3. **Harden auth config:** fix/replace `JwtSecretValidator` and the broken defaults (#3), add rate limiting (#13), bound TTL (#14).
4. **Correctness/perf:** `@Version` on `Asset` (#9), image size/type limits (#11, #12).
5. **Polish** the Low list as capacity allows.
