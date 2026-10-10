# Security audits

Last updated: 2026-10-10

The `exploit-hunter` audits of the gateway, one section per phase, condensed from the original per-audit reports
(dates are the audit dates). Every finding below was re-checked against the code on 2026-10-04: each "fixed" item
is in place with its test, and each "accepted" item is listed in `PLAN.md` under its accepted gaps, with a trigger.

| Phase | Audited | Scope | Result |
| --- | --- | --- | --- |
| 1 | 2026-09-29 | Auth, email confirmation, outbox, cleanup job | 2 fixed, 4 accepted (1 Medium, 5 Low) |
| 2 | 2026-09-29, re-check 2026-09-30 | Picture upload, file serving, profile routes | 1 Medium fixed, 2 Low accepted |
| 3 | 2026-09-30 | Follow, unfollow, follower and following lists | 3 Low, all accepted |
| 4 | 2026-09-30 | Routing of `/api/v1/tweets/**` | 1 Low fixed, 1 Low and 2 Info accepted |
| 5 | 2026-10-04 | `GET /api/v1/users` (people to follow) | 1 Medium fixed, 1 Low accepted |
| Follow check | 2026-10-04 | `GET /internal/v1/users/{a}/follows/{b}` | No findings |

## Phase 1: auth and email confirmation

- **Multi-byte password crashed register and login with a 500 (Medium), fixed.** `@Size(max = 72)` counts
  characters but bcrypt takes 72 bytes, so a 63-character password of 2-byte characters passed validation and the
  encoder threw. New `@MaxUtf8Bytes(72)` on both DTOs; both answer 400.
- **A short `JWT_SECRET` was accepted at startup and failed at login (Low), fixed.** Startup now fails below 32 bytes.
- **Postgres and Kafka were published on all interfaces with default credentials (Medium, dev compose), fixed for
  the ports.** All compose ports now bind `127.0.0.1`. The default credentials and plaintext Kafka are accepted.
- **`COOKIE_SECURE` defaulted to `false` (Low), fixed 2026-10-10.** It defaults to `true`; plain-HTTP local dev and the
  e2e stack set `COOKIE_SECURE=false`.
- **The seeder's shared password defaulted to a value in the public repo (Medium), fixed 2026-10-10.** `SEED_PASSWORD`
  has no default; with seeding on, startup fails unless it is 12-72 bytes with a lowercase letter, an uppercase letter
  and a digit (`DataSeedServiceImplTest`). The seeder stays on by default, so a deployment must set it.
- **An attacker-chosen path reaches a WARN log line (Low), accepted.** Only reachable authenticated, and forging a
  log line is the worst case.
- **The constraint-violation WARN carries the offending value, an email address (Low), accepted.** Only the
  unknown-constraint fallback reaches it.
- **Clean:** all queries bind parameters; no outbound requests or redirects from input; the only resource route
  is `/me`; HS256 is pinned and `alg=none` or tampered tokens are 401; the token is read from the cookie only; bodies
  are capped for declared and chunked sizes; error bodies are fixed strings; actuator exposes only `health`; no CORS.
  Dependencies were scanned on 2026-10-10 (see the end of this file).

## Phase 2: pictures and files

- **Parallel uploads to one slot leaked rows and objects without bound (Medium), fixed 2026-09-30.** Eight parallel
  uploads all answered 200 and left 4 orphan `DbFile` rows and objects. `ProfilePictureServiceImpl` now takes
  `UserRepository.lockById` before loading the user in both transactions, and
  `should_leave_no_orphan_rows_when_uploads_to_one_slot_run_in_parallel` fails without it (confirmed by removing
  the lock temporarily).
- **Files carry no restrictive CSP (Low), accepted.** `nosniff` plus a detected image type keeps a polyglot from
  rendering. Revisit if the type check is loosened, for example for SVG.
- **Re-check, one Low, accepted:** a burst of uploads from one account holds pool connections while queued on the
  row lock, so up to N pool slots are busy for the burst. It is bounded (the lock holder does DB work only, so no
  deadlock and no leak) and covered by the "no rate limiting" gap.
- **Re-check, verified:** the lock reads fresh state, so the loser sees the winner's picture as the old one;
  `@DynamicUpdate` keeps a racing profile edit from being overwritten; the lock is only on the caller's own row; a
  failed transaction still deletes the newly stored object, and old objects are deleted only after commit.
- **Clean:** 20 MB and 6 MB chunked uploads answer 413 and create no row; the client's type and filename are never
  read (type from magic bytes, key a random UUID); GIF, renamed text, truncated headers and a non-WebP RIFF give 415;
  the user id comes from the JWT only, so no IDOR; storage failures answer a fixed 502.
- **Correction:** file ids are time-based UUIDs, not random (see phase 5 finding 2).

## Phase 3: follows

- **Follow and unfollow are unthrottled write amplifiers (Low), accepted.** Each toggle is an insert or delete plus
  two counter updates and holds the target's row lock briefly. Counts stay correct; only load rises. Needs rate limiting.
- **Any logged-in user can read anyone's follower and following lists (Low), accepted.** By design (no private
  accounts); a scraper walks 100 rows per request. Needs a visibility check once private accounts exist.
- **Cookie-authenticated `PUT`/`DELETE` with CSRF disabled (Low), accepted.** The barrier is `SameSite=Strict`,
  which doesn't stop a request from a sibling subdomain of the same site. Keep untrusted content off sibling
  subdomains, or add a CSRF token if that changes.
- **Clean:** bound parameters throughout; a hand-built cursor can only move the page start (extreme values give
  200, malformed ones 400); identity from the JWT only; unknown and unconfirmed targets both 404; self-follow is 400
  before any write; `size` is validated before `size + 1` is computed; a 10-item page costs 3 statements; counters
  stay equal to `COUNT(*)` in all four concurrency tests.

## Phase 4: tweet routing

- **A chunked body over a cap answered 502 instead of 413 (Low), fixed 2026-09-30.** The size-cap exception
  surfaced wrapped in the proxy's I/O error. `handleUpstreamFailure` now unwraps it and answers 413, covered by a
  handler unit test and two chunked scenarios in `TweetBodyCapsIntegrationTest`.
- **Identity-header stripping is prefix-only (Low), accepted.** `X-UserId` and `X-Original-User-Id` reach the tweet
  service. Harmless while it reads only `X-User-Id`, which is safe in every variant tried (duplicated, lower-case,
  `X_User_Id`, named in `Connection:`). Forward an allowlist if a downstream ever trusts another header.
- **Paths with `;x=y`, `/./` or `//` answer 401 even with a valid cookie (Info), accepted.** It fails closed and is
  identical to the anonymous answer.
- **Tomcat's HTML 400 page on rejected paths (Info), accepted.** Gateway-wide; no version, path or exception text.
- **Clean:** no path trick (`..`, `%2e%2e`, `..;`, double encoding, CR/LF, case changes) reached the tweet service;
  anonymous requests were 401 with nothing forwarded; the tweet cap applies to the exact `POST /api/v1/tweets` only;
  `Content-Length` plus chunked was not smuggled; `Cookie`, `Authorization` and client `X-Forwarded-*` never
  reach the tweet service; `/actuator/env` is 401.

## Phase 5: people to follow

- **The after-cursor query scanned every newer row (Medium), fixed 2026-10-04.** The `OR` predicate can't become an
  index bound, so a crafted cursor made each page read the whole index: with 300,003 users, 486 req/s against 3,783
  req/s for a first page, growing linearly with the user count. The query now uses the row value
  `(u.createdAt, u.id) < (:createdAt, :id)` (0.11 ms on the same worst-case cursor). The follow-list queries keep
  the `OR` form, bounded by one account's follows (accepted).
- **User ids embed the host's IPv4 address, JVM start time and a counter (Low), accepted.** Hibernate's `TIME` UUID
  style is not random, and the list hands ids out in bulk. File ids are the same. No id is a secret today. Move to a
  random or v7 generator if that changes.
- **Clean:** bad or missing credentials are 401; the caller is excluded by the JWT `sub`; every malformed cursor is
  a 400 and nothing is reflected; every bad `size` is a 400; markup in a username or bio comes back as escaped JSON;
  the response is `no-store` with `nosniff` and `X-Frame-Options: DENY`.
- **Enumeration is sharper than the accepted gap's wording:** the list omits birthdate and location, but each listed
  username opens a profile that returns them, so harvesting costs one list call per 100 users plus one profile call
  each. Covered by the "page through every confirmed account" gap.

## Follow check (frontend phase 3)

No findings, not even Low. The secret guard runs first: a missing, empty, wrong, query-string or cookie-only secret
all answer the unknown-path 404, so the follow graph can't be read without it. The endpoint is not reachable
through the tweet or timeline routes (path-traversal variants all 401 or 400), allows only `GET`/`HEAD`/`OPTIONS`,
leaks nothing in error text, and costs one unique-index lookup (0.012 ms).

This audit ran against the `204`/`404` behaviour. The endpoint now answers `200 {"following": true|false}` (see
`DECISIONS.md`, "Internal API and events"), which keeps the guard's 404 as the only meaning of a wrong secret or
a missing route; the checks above still hold.

## Dependency scan (2026-10-10)

`osv-scanner` (Docker image `ghcr.io/google/osv-scanner`) over the four `pom.xml` files and both `package-lock.json`
files found 71 advisories (10 Critical, 34 High, 24 Medium, 3 Low) in `tomcat-embed-core` 11.0.24, `jackson-core` and
`jackson-databind` 2.21.5 and 3.1.5, `lz4-java` 1.10.1 and, in the tweet service, `bcprov-jdk18on` 1.84. All were fixed by
raising the versions in each `pom.xml` (Tomcat 11.0.25, Jackson 2.21.7 and 3.1.7, lz4-java 1.11.4, BouncyCastle 1.85), and
a second scan reported no issues. Reachability was not assessed. Nothing runs the scan automatically yet, so repeat it
before each deployment.
