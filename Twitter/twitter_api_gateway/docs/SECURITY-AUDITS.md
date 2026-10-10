# Security audits

Last updated: 2026-10-10

The `exploit-hunter` audits of the gateway, one section per phase, condensed from the original per-audit reports
(dates are the audit dates). Only fixed findings are kept in this public file; the open and accepted ones are kept
privately.

| Phase | Audited | Scope | Result |
| --- | --- | --- | --- |
| 1 | 2026-09-29 | Auth, email confirmation, outbox, cleanup job | 4 findings fixed; the open ones are kept privately |
| 2 | 2026-09-29, re-check 2026-09-30 | Picture upload, file serving, profile routes | 1 finding fixed; the open ones are kept privately |
| 3 | 2026-09-30 | Follow, unfollow, follower and following lists | No fixed findings; the open ones are kept privately |
| 4 | 2026-09-30 | Routing of `/api/v1/tweets/**` | 1 finding fixed; the open ones are kept privately |
| 5 | 2026-10-04 | `GET /api/v1/users` (people to follow) | No fixed findings kept here; the open ones are kept privately |
| Follow check | 2026-10-04 | `GET /internal/v1/users/{a}/follows/{b}` | No findings |

## Phase 1: auth and email confirmation

- **Multi-byte password crashed register and login with a 500 (Medium), fixed.** `@Size(max = 72)` counts
  characters but bcrypt takes 72 bytes, so a 63-character password of 2-byte characters passed validation and the
  encoder threw. New `@MaxUtf8Bytes(72)` on both DTOs; both answer 400.
- **A short `JWT_SECRET` was accepted at startup and failed at login (Low), fixed.** Startup now fails below 32 bytes.
- **`COOKIE_SECURE` defaulted to `false` (Low), fixed 2026-10-10.** It defaults to `true`; plain-HTTP local dev and the
  e2e stack set `COOKIE_SECURE=false`.
- **The seeder's shared password defaulted to a value in the public repo (Medium), fixed 2026-10-10.** `SEED_PASSWORD`
  has no default; with seeding on, startup fails unless it is 12-72 bytes with a lowercase letter, an uppercase letter
  and a digit (`DataSeedServiceImplTest`). The seeder stays on by default, so a deployment must set it.
The open ones are kept privately.

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
The open ones are kept privately.

- **Re-check, verified:** the lock reads fresh state, so the loser sees the winner's picture as the old one;
  `@DynamicUpdate` keeps a racing profile edit from being overwritten; the lock is only on the caller's own row; a
  failed transaction still deletes the newly stored object, and old objects are deleted only after commit.
- **Clean:** 20 MB and 6 MB chunked uploads answer 413 and create no row; the client's type and filename are never
  read (type from magic bytes, key a random UUID); GIF, renamed text, truncated headers and a non-WebP RIFF give 415;
  the user id comes from the JWT only, so no IDOR; storage failures answer a fixed 502.

## Phase 3: follows

No finding was fixed in this phase; the open ones are kept privately.

- **Clean:** bound parameters throughout; a hand-built cursor can only move the page start (extreme values give
  200, malformed ones 400); identity from the JWT only; unknown and unconfirmed targets both 404; self-follow is 400
  before any write; `size` is validated before `size + 1` is computed; a 10-item page costs 3 statements; counters
  stay equal to `COUNT(*)` in all four concurrency tests.

## Phase 4: tweet routing

- **A chunked body over a cap answered 502 instead of 413 (Low), fixed 2026-09-30.** The size-cap exception
  surfaced wrapped in the proxy's I/O error. `handleUpstreamFailure` now unwraps it and answers 413, covered by a
  handler unit test and two chunked scenarios in `TweetBodyCapsIntegrationTest`.
### 2. (moved out of the public repo, 2026-10-10)

### 3. (moved out of the public repo, 2026-10-10)

### 4. (moved out of the public repo, 2026-10-10)

The other open ones are kept privately.

- **Clean:** no path trick (`..`, `%2e%2e`, `..;`, double encoding, CR/LF, case changes) reached the tweet service;
  anonymous requests were 401 with nothing forwarded; the tweet cap applies to the exact `POST /api/v1/tweets` only;
  `Content-Length` plus chunked was not smuggled; `Cookie`, `Authorization` and client `X-Forwarded-*` never
  reach the tweet service; `/actuator/env` is 401.

## Phase 5: people to follow

No fixed finding is kept here; the open ones are kept privately.

### 2. (moved out of the public repo, 2026-10-10)

- **Clean:** bad or missing credentials are 401; the caller is excluded by the JWT `sub`; every malformed cursor is
  a 400 and nothing is reflected; every bad `size` is a 400; markup in a username or bio comes back as escaped JSON;
  the response is `no-store` with `nosniff` and `X-Frame-Options: DENY`.

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
