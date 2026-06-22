# WealthBuilder Backend — Quality Review

**Date:** 2026-06-21
**Scope:** Whole backend application (`com.wealthbuilder.backend`), Spring Boot 4.0.6 / Java 25.
**Method:** Layer-by-layer audit (config/security, services/repositories, web/DTOs, infra/seeding) with each finding traced back to the exact source line.

This is a **recall-biased** report: it surfaces real defects and risks a careful reviewer would flag in one sitting, ranked by severity. Each item states the location, the concrete failure scenario, and a recommended fix.

---

## 🟡 Medium

### 13. No rate limiting on auth; register enables user enumeration
**`AuthController` / `AuthServiceImpl`** — `/api/auth/login` and `/register` are `permitAll` with no throttling, lockout, or CAPTCHA → BCrypt brute force is unmetered. `register` throws a distinct `UsernameAlreadyTakenException` (→ 409), letting an attacker enumerate valid usernames. (Login itself is uniform via `AuthenticationManager` — good.) **Fix:** add per-IP/username rate limiting (e.g. Bucket4j) and consider a uniform register response.

### 14. No token revocation / logout; TTL unbounded
**`JwtServiceImpl`** — no logout endpoint, no `jti`/blacklist, no per-user token version. A stolen token is valid for the full TTL even after password change; and TTL has no upper-bound validation, so `JWT_TTL=PT720H` yields month-long tokens. Per-request DB authority reload (good) handles deletion/role changes, but not an existing user's leaked token. **Fix:** short TTL + refresh tokens, or a token-version claim checked against the user.

### 15. CORS default reflects any localhost port and is baked into the jar
**`application.properties:22` / `CorsConfig`** — default `http://localhost:[*],http://127.0.0.1:[*]`. `allowCredentials` is unset (correct for bearer auth), but nothing fails loudly if `FRONTEND_BASE_URIS` is forgotten in prod, so a malicious local process could call the API from any localhost origin. **Fix:** ship no permissive default, or add a startup guard (like the JWT one *should* be) that rejects the localhost pattern under a non-dev profile.

---

## 🔵 Low / polish

- **Unused `role` claim in the JWT** (`JwtServiceImpl.java:71`): authorities are (correctly) reloaded from the DB, so the baked-in `role` claim is never read — dead, slightly misleading data. Remove it or document why it's there.

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
2. **Close the 500s:** exception-handler gaps (#5), asset-name index (#7).
3. **Harden auth config:** fix/replace `JwtSecretValidator` and the broken defaults (#3), add rate limiting (#13), bound TTL (#14).
4. **Correctness/perf:** `@Version` on `Asset` (#9), image size/type limits (#11, #12).
5. **Polish** the Low list as capacity allows.
