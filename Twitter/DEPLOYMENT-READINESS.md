# Deployment readiness review

Review date: 2026-10-09. Branch: `seed-demo-data` (one commit ahead of `main`, plus uncommitted frontend work).

Scope: the whole system: `twitter_api_gateway`, `twitter_tweet_service`, `twitter_timeline_service`,
`twitter_mail_service`, `frontend/`, `e2e/`, the compose files, the Dockerfiles and the docs.

Seven read-only reviews ran in parallel (one per service, the frontend source, the frontend tooling plus e2e, and the
cross-cutting infra and docs). The lead ran the builds and tests.

## Verdict

The code is in good shape. What is missing is the layer between "works in dev compose" and "deployable". No service
has a correctness P0. The blockers are configuration, topology and a few reliability gaps.

| Check | Result |
| --- | --- |
| Gateway tests (`./mvnw verify`) | 872 pass, 0 failures, 0 skipped |
| Tweet service tests | 468 pass |
| Timeline service tests | 709 pass |
| Mail service tests | 162 pass |
| Maven warnings, all four services | none |
| Frontend `tsc -b` | clean |
| Frontend lint (oxlint, also `--deny-warnings`) | 0 warnings, 0 errors |
| Frontend unit tests | 1147 / 1147 in 66 files, identical on a second run |
| Frontend build | OK; main JS 333 kB (104 kB gzip), CSS 44 kB; no source maps |
| `npm audit`, with and without dev | 0 vulnerabilities |
| e2e suite | not run (it needs Docker, and this machine's ports are taken by another project); reviewed statically |

Legend: effort **S** (under half a day), **M** (about a day or two), **L** (more than that).

## P0: blocks deployment

- [ ] **1. There is no way to serve the frontend (M).**
  - The repo has only the four service Dockerfiles. There is no `frontend/Dockerfile`, no nginx/Caddy config, no TLS,
    and `docker-compose.yml` has no frontend service. The gateway is published only on `127.0.0.1:8080`. The root
    `README.md` documents only `npm run dev`.
  - The proxy in `frontend/vite.config.ts:9-14` exists in dev only. The cookie is `HttpOnly` + `SameSite=Strict`
    (`CookieFactory.java:41-44`) and the gateway has no CORS, so the only topology that works is one origin serving
    both the SPA and `/api`.
  - The proxy needs:
    - SPA `try_files … /index.html`, not applied to `/api/`. The emailed `/confirm?token=`, `/users/:name` and
      `/tweets/:id` are deep links (`BrowserRouter`).
    - `/api/` proxied to `gateway:8080`.
    - A request body limit of at least 21 MB. The tweet cap is 21,037,056 bytes (`application.properties:50`), and
      nginx's default 1 MB breaks image uploads.
    - `/assets/*` cached as immutable, `index.html` as `no-cache`.
    - TLS, because `COOKIE_SECURE=true` needs HTTPS.
  - No service sets `server.forward-headers-strategy`, so the gateway will not see the real scheme behind a TLS proxy.
  - `CONFIRMATION_LINK_BASE_URL` (gateway) and `APP_BASE_URL` (mail) both default to `http://localhost:5173`. Set both to
    the public https origin.
  - Fix: a multi-stage `node:24` build (`npm ci && npm run build`) into nginx or Caddy, plus a compose service.

- [ ] **2. Dev defaults fail open (S).**
  - `twitter_api_gateway/src/main/resources/application.properties`:
    - `:141` `app.seed.enabled=${SEED_ENABLED:true}`
    - `:142` `app.seed.password=${SEED_PASSWORD:Password123}`
    - `:27` `app.cookie.secure=${COOKIE_SECURE:false}`
  - The first production start on an empty database creates 12 confirmed accounts (`DataSeedServiceImpl.java:31-34,80`)
    with a password that is in a public repo. Their follow events also send about 46 emails to `@seed.local` addresses
    (`DataSeedServiceImpl:121-133`, 12 users at 0.35 probability), which means bounces and SMTP quota use over real SMTP.
  - Fix:
    - Default `app.seed.enabled` to false and put `@ConditionalOnProperty` on the seed beans.
    - Give `SEED_PASSWORD` no default, and fail startup if seeding is on and it is unset.
    - Default `COOKIE_SECURE` to true, with the dev `.env` opting out.
    - Ship an `application-prod.properties` or a prod env example.

- [ ] **3. The root compose file is dev-only (M).**
  - `docker-compose.yml:108-125`: the mail service hardcodes `MAIL_HOST: mailpit` plus port, auth and STARTTLS values.
    These override `.env`, so `MAIL_USERNAME` and `MAIL_PASSWORD` are ignored and a deployed stack can never send to a
    real inbox.
  - It `include`s the whole infra file, so kafka-ui (no auth, `docker-compose.infra.yml:80`) and mailpit (`:171`) ship
    with it.
  - `env_file: .env` (`docker-compose.yml:11`) gives every container every secret. The mail service receives
    `JWT_SECRET`, `MINIO_*` and `POSTGRES_PASSWORD`, so a compromised mail service could forge JWTs.
  - Fix: a `docker-compose.prod.yml` override, or `profiles: [dev]` for kafka-ui and mailpit with the mail overrides
    removed from the base file. Give each service an explicit `environment:` list (or per-service env files or Docker
    secrets).

- [ ] **4. Keep the seed photos out of git (S).**
  - The repo is **public** (`https://github.com/PowerCell46/AI-Projects`, confirmed with `gh repo view`).
  - `twitter_api_gateway/seed-images/` has 6 untracked photos (about 25 MB, against a repo pack of about 33 MB). They are
    named after real people (Gabi, Kristian, Gosho, Stiliyan) and are full camera originals (2880×1800 up to
    6016×6016). `gosho.jpg`, `stiliyan.jpg` and `Gabi2.jpg` carry EXIF (`gosho.jpg`: Sony ILCE-7RM4). No GPS was found.
  - They are not git-ignored (`git check-ignore` prints nothing). Do not run `git add -A` before deciding. Once
    committed they cannot be cleaned out of history.
  - `stiliyan.jpg` (7.4 MB) and `gosho.jpg` (5.5 MB) exceed the 5,242,880 B upload cap
    (`ProfilePictureServiceImpl.java:98`), so the seeder skips them with a WARN on every start. Of the 12 seeded users,
    six have no picture file at all, and two of the six files are skipped.
  - Fix: add `seed-images/*` with `!README.md` to `.gitignore`, or replace them with generated or licensed avatars of
    256 px or less (under 100 KB). If real photos are ever committed, get consent and strip EXIF.

## P1: fix before deployment

### Bugs and data loss

- [ ] **Outbox events are lost after a short Kafka outage (M)** (gateway and tweet service share the same publisher).
  - `OutboxPublisherServiceImpl.java:50-100` (gateway `:57-65,88-97`); `OUTBOX_MAX_ATTEMPTS` defaults to 3 and the poll
    delay is 3 s.
  - A row that hits max attempts becomes `FAILED` and is never queried again. A roughly 30 s outage therefore loses
    confirmation mails, follow mails and the timeline's `tweet.created` / `tweet.deleted` until someone edits the
    database by hand.
  - Each failing row can block for the 10 s send timeout (plus Kafka's 60 s default `max.block.ms`), so a batch of 500
    can hold the single scheduler thread for hours.
  - Tweet service only: the loop `continue`s past a failure, so a later `tweet.deleted` can publish before an earlier
    failed `tweet.created`. `twitter_tweet_service/docs/EVENTS.md:10-11` and `:63` say that cannot happen. An `InterruptedException` at shutdown is
    also recorded as a failed attempt.
  - Fix: break out of the batch on the first infrastructure failure; do not count timeouts, connect errors or
    interrupts as attempts (backoff with a `nextAttemptAt`, or 20-50 attempts); set `max.block.ms` and
    `delivery.timeout.ms`; add a `FAILED` to `PENDING` requeue script; expose the `FAILED` count as a health indicator
    or metric.

- [ ] **Registration rejects valid emails (S).** (Reproduced.)
  - `RegisterRequestDTO.java:24`: `^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$`.
  - It fails `a@mail.company.com`, `x@fmi.uni-sofia.bg` and `y@example.co.uk` with a 400. The resend DTO uses only
    `@Email`, so the two endpoints disagree. No test covers it.
  - Fix: drop the pattern or use `[\w.-]+\.[a-zA-Z]{2,}`, and add a parameterized test.

- [ ] **Old Kafka history gets replayed (S each).**
  - Timeline fan-out (`FeedFanOutServiceImpl.java:32-37`) and mail follow events (`UserFollowedEventDTO:40`,
    `occurredAt` validated but never read) have no staleness check. Both consume from `auto-offset-reset=earliest`
    (`application.properties:53` in the timeline service).
  - A first deploy against an existing topic, a new consumer group, expired offsets or a DLT replay makes every old
    tweet call the gateway and land in feeds until the 04:00 cleanup, and re-mails every historical follow.
  - Fix: skip events older than `now - retention` (timeline) or a max age (mail, with the injected `Clock`).

- [ ] **Mail claim TTL (300 s) is longer than the retry window (242 s) (S).**
  - `application.properties:44` vs `:32-34` in the mail service (2 s doubling to 60 s, 8 retries). After a hard kill
    mid-send (SIGKILL or OOM), the redelivered record is `HELD` for each of its 9 attempts, dead-letters, and the email
    is lost.
  - Fix: a claim TTL of about 2 minutes, and `stop_grace_period: 30s` in compose.

- [ ] **Mail configuration fails silently instead of at startup (S).**
  - `APP_BASE_URL` defaults to `http://localhost:5173` (`application.properties:74`) and `.env` has no value, so
    follow-email links point at localhost.
  - `MAIL_USERNAME` / `MAIL_PASSWORD` default to empty while `smtp.auth=true` (`:54-56`). A missing secret is an auth
    failure, which is classified transient, so each email retries for 242 s and then dead-letters.
  - `MailDeliveryServiceImpl:36` stores `MAIL_FROM` without parsing it, so a typo fails every send as a permanent
    `AddressException`.
  - A trailing slash on `APP_BASE_URL` gives `//feed`. `CONFIRMATION_MAIL_SENT_TTL` is unchecked (the follow window
    already has a check).
  - Fix: validate at startup. Make `APP_BASE_URL` required, absolute and slash-free (warn on non-https outside
    localhost), require the credentials when auth is on, and parse `MAIL_FROM`.

- [ ] **Timeline: one consumer group for four listener containers (S).**
  - `application.properties:52`, no `groupId` on any `@KafkaListener`. A rebalance caused by one container reaches all
    four. After the first deploy, separate groups replay from `earliest`, so do this together with the staleness guard.

### Security

- [ ] **The tweet service trusts any caller (M).**
  - `/internal/v1/tweets/**` has no authentication and `X-User-Id` is accepted from whoever reaches port 8081
    (`InternalTweetController.java`, `CurrentUserIdArgumentResolver.java`; root `SECURITY-FINDINGS.md` #4). The
    documented trigger (a deployed network) is met.
  - Fix: port the gateway's `InternalApiSecretFilter` (constant-time compare, 32-byte floor, fail at startup) guarding
    everything except `/actuator/health`. Use a dedicated secret such as `TWEET_SERVICE_SECRET`, not the gateway's (the
    timeline's `tweetServiceRestClient` comment says it must never hold the gateway's secret). Callers that need the
    header: gateway `TweetRoutesConfiguration`, the gateway seeders `TweetSeedServiceImpl` / `ReplySeedServiceImpl`, and
    timeline `RestClientConfiguration.tweetServiceRestClient`.
  - Keep ports 8081 and 8083 private. The timeline service `Dockerfile:49` comment suggests `docker run -p 8083:8083`,
    which publishes a service that trusts `X-User-Id`; change the comment.
  - The internal secret travels over plain `http://gateway:8080`; accept this explicitly or add TLS between services.

- [ ] **No rate limiting (M).**
  - Unauthenticated `login` and `register` each cost one bcrypt-12 hash (about 250 ms, `application.properties:29`);
    unknown identifiers cost the same. About 4 requests per second saturates the gateway's 1-CPU limit
    (`docker-compose.yml:31-35`). `register` can also mail arbitrary addresses.
  - The author-tweets fan-out read (root `SECURITY-FINDINGS.md` #1) is unlimited.
  - Fix: per-IP limits at the reverse proxy or a Bucket4j filter on `/auth/**`, plus per-user limits on the proxied read
    routes.
  - Also block `/internal/**` and `/actuator` at the ingress, since they sit on the public port.

- [ ] **Data stores and accounts (S/M).**
  - Mongo has no auth or keyfile and `directConnection=true` is hardcoded in compose, so there is no replica-set
    failover and no `serverSelectionTimeoutMS` (default 30 s). `rs.initiate` uses `localhost:27017`. Have compose read
    `${MONGODB_URI}` with credentials, `authSource`, `replicaSet` and short timeouts.
  - Kafka is single-node PLAINTEXT with no auth. The apps connect as the Postgres superuser and as MinIO root, and
    MinIO runs as `user: "0:0"` (`docker-compose.infra.yml:100`) with no comment. Create app roles and MinIO service
    accounts scoped to the two buckets.
  - Redis has no `maxmemory` against a 128 MB limit.

- [ ] **EXIF/GPS is kept on every uploaded image (M).** Strip metadata (re-encode, or a stripper) before `put`.
  Documented trigger: "before any public deployment".

- [ ] **No dependency vulnerability scan has ever run (S).** Both audit docs say "dependencies were not scanned".
  Run OWASP dependency-check or osv-scanner on Spring Boot 4.1.1, minio 9.0.3 and okhttp 5.3.2 (`okhttp-jvm` is a
  hardcoded version in the gateway pom).

### Operations

- [ ] **Schema is managed by `ddl-auto=update` with no migrations (M).**
  - Gateway `application.properties:16` and timeline `:18`. `@Check` constraints are never added to existing tables;
    column widening is manual (gateway `DECISIONS.md:98`). In the timeline service the primary-key column order depends
    on Hibernate's attribute-name ordering (`DECISIONS.md`), so drift would be silent.
  - Both documented triggers ("a second environment") are met.
  - Fix: Flyway baseline V1 from the current schema, then `ddl-auto=validate`, before any production data exists.
  - Tweet service: `auto-index-creation=true` fails startup on an index conflict, so later index changes need a
    manual migration.

- [ ] **Runtime settings are at Spring Boot defaults in every service (S).**
  - `server.shutdown` is `immediate`: a SIGTERM cuts in-flight requests and 20 MB uploads, which leaves orphan MinIO
    objects, and the default scheduler interrupts the outbox job. Set `server.shutdown=graceful`,
    `spring.lifecycle.timeout-per-shutdown-phase=30s` and `spring.task.scheduling.shutdown.await-termination=true`
    (and raise compose's default 10 s `stop_grace_period`).
  - Timeline: Hikari pool defaults to 10 against 12 consumer threads (4 listeners × concurrency 3) plus up to 200
    request threads, and `connection-timeout` is 30 s while the gateway gives up at 10 s. `max-poll-records` defaults
    to 500 against a 5-minute `max.poll.interval.ms`, with HTTP per record in fan-out and back-fill (this is the open
    back-fill item in the timeline `SECURITY-AUDITS.md`).
  - Gateway: `spring.jpa.open-in-view` is on (Boot default, with a startup warning). `register` hashes the password
    inside `@Transactional` (`AuthServiceImpl.java:38-47`), so 10 concurrent registers can exhaust the default pool;
    hash before the transaction.
  - JVM: `-XX:MaxRAMPercentage=75` against 768 MB limits (1 GB for the gateway) leaves about 190 MB for non-heap; use
    60-65% and add `-XX:+ExitOnOutOfMemoryError`. Kafka's default heap is 1 GB inside a 1024 MB limit, so set
    `KAFKA_HEAP_OPTS` to about 512m.
  - Health: tweet health covers Mongo only; mail has no Kafka listener indicator; no liveness/readiness groups are
    enabled; management settings differ across services (mail sets `show-details=always`, `application.properties:80`).

- [ ] **MinIO client has no timeouts, in the gateway and the tweet service (S).**
  - `MinioConfiguration.java:17-21` (gateway), `:12-22` (tweet). minio 9.0.3 `Http.DEFAULT_TIMEOUT` is 5 minutes for
    connect, read and write, so a stalled MinIO pins Tomcat threads on `/files/{id}`, uploads, tweet delete, and in
    `MinioBucketInitializer` at startup.
  - Fix: about 2 s connect and 10-30 s read/write.

- [ ] **`.env.example` contradicts the README (S).**
  - The file has 43 uncommented empty keys, only 10 of them required. The README (`:31-33`) says an empty value
    overrides the default and optional lines must be left out. Following `cp .env.example .env` therefore sets about 33
    optional keys (`JWT_TTL=`, `BCRYPT_STRENGTH=`, `SEED_ENABLED=`) to empty.
  - Comment out every optional key and add one-line comments. Quote cron values, since the file is sourced in a shell.
  - Used in code but missing from the file: `GATEWAY_INTERNAL_URL`, `TIMELINE_HTTP_CONNECT_TIMEOUT`,
    `TIMELINE_HTTP_READ_TIMEOUT`, `USER_FOLLOWED_TOPIC_NAME`, `USER_FOLLOWED_TOPIC_PARTITIONS`,
    `USER_UNFOLLOWED_TOPIC_NAME`, `USER_UNFOLLOWED_TOPIC_PARTITIONS`.
  - `# SERVER_PORT=` appears three times; `OUTBOX_*` is shared by gateway and tweet; `KAFKA_RETRY_*` by mail and
    timeline.

- [ ] **Kafka data may not be on its volume (S, unverified).**
  - `docker-compose.infra.yml:72` mounts `kafka_data` at `/var/lib/kafka/data` and sets no `KAFKA_LOG_DIRS`. The
    upstream trunk `server.properties` sets `log.dirs=/tmp/kraft-combined-logs`. The reviewer checked trunk, not the
    4.3.1 tag, and did not run Docker.
  - Verify with `docker compose exec kafka ls /var/lib/kafka/data`. Fix: `KAFKA_LOG_DIRS=/var/lib/kafka/data`.
  - Topics are created by four services' `NewTopic` beans while broker auto-create stays on, and DLT replicas are
    hardcoded to 1 (`KafkaTopicConfiguration.java:17-20`). Consider one owner for topics.

- [ ] **No CI/CD and no backups (M/L).**
  - `git ls-files | grep -iE 'gitlab-ci|github|jenkins|workflows'` returns nothing. All poms are `0.0.1-SNAPSHOT`, and
    there is no image tagging scheme.
  - Tests need Docker and Testcontainers. A pipeline should run: `npm ci`, build, lint, test, `npm audit --omit=dev`,
    the Maven suites, image build and push, a dependency scan, then e2e. The e2e config has no CI mode (add
    `forbidOnly`, `retries: 1`, a non-opening reporter, and screenshot/video on failure).
  - Add backup jobs and restore tests for the two Postgres databases, Mongo and MinIO.

- [ ] **Logging and monitoring (S/M).** Plain-text logs, no compose `logging:` rotation (json-file is unbounded), no
  Micrometer registry or tracing, and nothing alerts on a dead-lettered record. Fix:
  `logging.structured.format.console=ecs`, a Prometheus registry, rotation in compose.

### Frontend

- [ ] **Avatars load full-size originals (S frontend, M backend).**
  - `components/shared/Avatar/Avatar.tsx:15` has no `loading="lazy"` or `decoding="async"`. Avatars show at 30-84 px in
    every post cell and People card, while uploads can be 5 MB (seed images 1.4-7.4 MB, up to 6016×6016) and the
    backend does no resizing (documented trigger "before public deployment", gateway `PLAN.md:132`).
  - Fix: both attributes on `Avatar` now; thumbnail plus full variant on upload (or presigned URLs / a CDN) in the
    backend.

- [ ] **No request timeout or abort, and no 429 handling (S).**
  - `api/http.ts:64-82` has no `AbortSignal`. A hung request leaves `usePagedList.isFetchingRef` true, so the list shows
    "FETCHING MORE" forever and optimistic toggles stay in flight. The rate limit planned above will surface as a
    generic SIGNAL LOST.
  - Fix: `AbortSignal.timeout(...)` in `send` that throws `ApiError(0)`, plus a 429 message.

- [ ] **No error boundary (S, needs a decision).** Any render-time throw leaves a blank page (`main.tsx`, `App.tsx`).
  An error boundary needs a class component or a new dependency, which conflicts with the "functional components only"
  hard rule in the root `CLAUDE.md`.

- [ ] **No per-route titles and no focus move on route change (S-M).** `index.html` has one static `<title>` and
  `document.title` is set nowhere. Opening details or a profile only calls `window.scrollTo`
  (`TweetDetailsPage.tsx:51`, `ProfilePage.tsx:28`), so focus stays on the now-hidden clicked element. Add a
  `useDocumentTitle` hook and focus the page `h1` (`tabIndex={-1}`, as `Arrival.tsx:19` already does).

- [ ] **`index.html` and the pre-deployment items you deferred (S-M).**
  - `PLAN.md` ("Carried over", "Left open") and `SECURITY-FINDINGS.md` #1-#2 deferred these "before any deployment":
    `<meta name="referrer">`, a CSP, self-hosted fonts, and `COOKIE_SECURE` defaulting to true.
  - CSP shape: scripts are all external, so `script-src 'self'` works. You need `style-src-attr 'unsafe-inline'`
    (inline `style=` in `TabRow.tsx:80`, `Horizon.tsx:22`, `Ruler.tsx:23`) and `img-src 'self' blob:`
    (`createObjectURL` in `useImageAttachments.ts` and `usePhotoPick.ts`).
  - Google Fonts (`index.html:6-10`) sends visitor IPs to Google, a GDPR concern for an EU deployment.
  - The favicon is `public/Twitter-Logo.png`: 684 KB, 1383×1137, declared `type="image/svg+xml"` (`index.html:3-4`),
    referenced nowhere else, and a dark-navy bird on transparent, nearly invisible on dark tabs. Ship small square icons
    (32/180/512 px) with correct MIME types.
  - Missing meta: description, `theme-color`, apple-touch-icon, manifest.
  - Branding: it is the classic Twitter bird under the title "Twitter". That is a trademark decision for you before
    going public.

## P2: cleanup

### Docs

- Root `PLAN.md:32-76` is a stale "next step" scratch note about a news-feed service that is already built as
  `twitter_timeline_service`, and the saved/liked notes are superseded. Delete from the `---` line on.
- Dangling references to deleted docs: `docs/likes-design.md`, `docs/replies-design.md`, `frontend/feed-design.md`,
  `feed-design-addition.md` (deleted in commit 8238824) and `AuthenticationViewsDesigns.md` are still cited by root
  `PLAN.md:10,72`, the tweet and timeline PLANs, and `frontend/CLAUDE.md:8,9`, `frontend/PLAN.md:11,14,146`.
  `LAN-DEV-SERVER.md` (`frontend/CLAUDE.md:42`) was never written. Restore the briefs or remove the references.
- Gateway docs: `CLAUDE.md:5` ("four phases"), `:18-19` and `PLAN.md:22` call the follow email unbuilt (it exists in
  the mail service, `UserFollowedListener`), `CLAUDE.md:25` still says "MinIO joins in phase 2", `PLAN.md:59,62` still
  say `PUT /users/me` and location ≤30 (code: `PATCH`, 60), and `SECURITY-AUDITS.md` predates the seeder, PATCH
  profile, likes and author-tweets routes.
- Timeline docs: `PLAN.md:78` says timeouts 2 s/5 s (code: 1 s/3 s); `:231` lists the tweet details page and tweets by
  author as out of scope (both built); the call table (`:65-71`) lacks `by-author/{id}/page`; `CLAUDE.md:8` omits
  `/api/v1/author-tweets/*`; `CLAUDE.md:9` promises "numbered steps with gates" that compaction removed, so
  `DECISIONS.md`'s 42 "step N" references point nowhere; `TESTING.md:17,33,98` are stale.
- Tweet docs: `PLAN.md` "Out of scope: tweets by user" and "Later changes" omit the by-author, page and count routes;
  `CLAUDE.md` summary omits them too; `application-test.properties:1-6` repeats the main Kafka logging lines.
- Mail docs: `PLAN.md` left-open 5 ("nothing committed") is wrong, left-open 2 (start order) is solved by
  `depends_on gateway: service_healthy`, `CLAUDE.md` still says the `@Disabled` catalog exists (none remains), and
  places validate/claim/send/mark in the notification service when they now live in two shared services; the PLAN says
  infra is in `docker-compose.yml` (it is in `docker-compose.infra.yml`).
- Frontend `PLAN.md`: "Path segments are not encoded in ENDPOINTS" is false now (`endpoints.ts` `segment()`); the "Out
  of scope" list still names profile pages, follow UI, the tweet-details page, the edit-account page and
  containerisation, which are mostly built; a stray blank line at lines 28-30 breaks the status table.
- `frontend/PLAN.md` (32 KB) and `DECISIONS.md` (35 KB) record the same step-by-step results and `DECISIONS.md` holds
  superseded decisions (for example "POST button has no handler yet"). Mark them historical or trim.
- `frontend/exploit-report-2026-10-01.md`: no secrets, but it is superseded (its findings 1-2 are tracked as root
  `SECURITY-FINDINGS.md` #1) and lists weaknesses in a public repo. Fold it into `SECURITY-FINDINGS.md` as "Audit
  2026-10-01" or delete it (git history keeps it).
- Security doc naming is inconsistent: gateway, mail and tweet have only `SECURITY-AUDITS.md` (tweet merged its raw
  report into it on 2026-10-09), timeline has both files, frontend and root have only `SECURITY-FINDINGS.md`. Unify
  on one file per service: fold the timeline's `SECURITY-FINDINGS.md` into its `SECURITY-AUDITS.md`.
- Root `README.md` never mentions the seeder, its 12 `@seed.local` users, `Password123`, `seed-images`, the production
  frontend build, or the e2e ports and prerequisites. `seed-images/README.md` holds a dated to-do list.
- `.claude/skills/java-junit` is tracked while the other skills are global, so a fresh clone lacks the skills the
  `CLAUDE.md` files mandate.

### Docker and infra hygiene

- All four Dockerfiles end with a stale "Run project" comment block (`Dockerfile:41-51`). Its `docker run` example
  omits required env (`INTERNAL_API_SECRET`, `MINIO_*`), so the container would not start. Delete it; the README covers
  it.
- No `.dockerignore` in any service. The gateway build context carries `target/` (109 MB), `seed-images/` (about 24 MB)
  and `.idea`. (The `COPY` lines are explicit, so nothing leaks into the image.) Add a frontend one that excludes `*.md`
  and tests when the frontend image lands.
- Base image tags float (`eclipse-temurin:25`, `postgres:18`, `mongo:8.2`). The four Dockerfiles are identical apart
  from `EXPOSE`: use `COPY --chown`, BuildKit cache mounts for `~/.m2`, and digest-pinned bases.
- `alpine/minio:RELEASE.2025-10-15…` is a third-party mirror about a year old; check its upstream support status.
- e2e: `docker-compose.e2e.yml:4-5,141-142` publishes Postgres (`5452`) and the gateway (`8180`) on `0.0.0.0` with
  `twitter_e2e` credentials, and nothing in `tests/` uses 5452. Bind to loopback. Ports 5273, 8180, 8125 and 5452 are
  hardcoded in several files with no override (`fixtures.ts:47`).
- e2e: `global-setup.ts:12-15` runs `up -d --build --wait` with a 300 s timeout, which includes four Maven image builds;
  healthchecks allow about 90 s per Spring service. Raise to about 900 s or build in a separate step (unverified).
- e2e never touches a production build, an SPA fallback or `/api` through a real proxy. Once the frontend container
  exists, add a smoke project against it (or `vite preview`).
- e2e covers desktop Chromium only, with `reducedMotion: 'reduce'` globally and no mobile project; `auth.spec.ts` uses
  no semicolons while `fixtures.ts` does.
- No `LICENSE` (a public repo is all-rights-reserved by default) and no root `.gitattributes`.
- No Node pin: no `engines`, `.nvmrc` or `packageManager`, although Vite 8 needs Node 20.19 or later and you run 24.13.

### Code (backend)

- **Dead code and unused dependencies**
  - Tweet pom: `spring-boot-starter-validation` (`:33`), `-validation-test` (`:74`), `spring-boot-starter-kafka-test`
    (`:69`), `testcontainers-junit-jupiter` (`:89`). Nothing in `src/main` uses `jakarta.validation`.
  - Timeline pom: `testcontainers-junit-jupiter` looks unused; the `<description>` still says "feed, saved tweets and
    views".
  - Mail pom: unused test deps `spring-boot-starter-kafka-test`, `testcontainers-junit-jupiter`, `webmvc-test`. JUnit
    and Mockito arrive only transitively, so add `spring-boot-starter-test` before removing them.
  - Dead exception handlers: `GlobalExceptionHandler.handleMethodArgumentNotValid` plus `describe` in tweet
    (`:161-174`, `:262`) and timeline (`:89-99`), and `handleMaxUploadSizeExceededException` in timeline (`:19`, `:133`;
    no multipart endpoints).
  - Confirm each with `mvn dependency:analyze` and a build.
- Project rule breaks: `Instant.now()` in main code (`ErrorResponseWriter.java:34` in timeline and tweet,
  `GlobalExceptionHandler.java:185` timeline, `:267` tweet); the `ErrorResponseWriter` Javadoc mentions
  `AuthenticationEntryPoint` although there is no Spring Security; two TODO comments in the gateway
  (`UnconfirmedUserCleanupJob.java:11`, `LoginRequestDTO.java:19`).
- Timeline: `UserLookupServiceImpl.java:41-46` throws an NPE (500) on an empty body, `null` or a null id;
  `FollowerLookupServiceImpl.java:36-37` throws an NPE on a null page or null `ids` (in a listener: about 4 minutes of
  retries, then dead-letter). `TweetLookupServiceImpl.requireWellFormed` was hardened in the 2026-10-07 audit but these
  two were not, and their test suites have no such case.
- Timeline back-fill cheap fixes (PLAN "left open"): reject `FEED_BACKFILL_SIZE` above 100 at startup
  (`FeedBackfillServiceImpl.java:55`), reject a self-follow event, clamp `since` to `now - retention`, cap the returned
  list. Also the log-forging item (`GlobalExceptionHandler.java:~123` logs the path unescaped), which is a one-minute
  `curl` check, and the unseen `/liked` browser check.
- Tweet: a garbled or truncated multipart body, or more than 10 parts, should hit the catch-all (500 plus an ERROR
  stack trace), because neither the advice nor `ResponseEntityExceptionHandler` handles Spring's `MultipartException`
  (`GlobalExceptionHandler.java:155-159`; checked in the jar, not run). Map it to 400 and add a test. No test covers
  `handleHttpMessageNotReadable`.
- Mail: `MailInboxServiceImpl:62-64` swallows a `markSent` failure, so the catch at `MailDispatchServiceImpl:46-51` is
  unreachable in production, and the unit test that mocks it throws something that cannot happen. Keep one catch and
  test it with a real Redis failure.
- Mail follow email links to `/feed` and reads "ana (@ana)" (display name and handle identical). The "SPA gets a
  profile page" trigger is met (`frontend/src/routes.ts` has `/users/:username`): link to
  `APP_BASE_URL/users/<follower>`.
- Seeder robustness: `TweetSeedServiceImpl.java:72-82` has no connect retry (likes retry for about 3 minutes,
  `LikeSeedServiceImpl.java:28-30`), and compose starts `tweet-service` independently of the gateway, so a cold `up`
  can leave users without tweets for good. Reuse the like retry. There are no tests for the Tweet/Reply/Like seed
  services, the `DataSeedRunner` gate or `PathMultipartFile`.
- Timeline test gaps: nobody asserts `ix_feed_entries_tweet` or `ix_feed_entries_tweet_created` (which `tweet.deleted`
  and retention depend on), and `AbstractListenerIntegrationTest.java:332,365` scan whole tables with `findAll()` on
  every Awaitility poll.
- Tweet test isolation: `MongoCollectionInitializerIntegrationTest.java:62-64` drops the shared `tweets` and `outbox`
  collections and their indexes without restoring them (it passes only because the index-asserting `@DataMongoTest`
  classes use a different Spring context); `OutboxPublisherServiceKafkaIntegrationTest` reads only the oldest 500
  `PENDING` rows of a shared outbox no test clears (backlog size not measured); the explain tests
  (`TweetRepositoryIntegrationTest.java:376-413`) hand-build their own filters instead of calling the repository.
- Mail test isolation: `TwitterMailServiceApplicationTests`, `MailTlsConfigurationIntegrationTest` and
  `MailInboxServiceIntegrationTest` boot full contexts with listeners on the same topics and group as the shared e2e
  context. Set `spring.kafka.listener.auto-startup=false` in those three. There are no unit tests for
  `MailDispatchServiceImpl` or `MailEventValidationServiceImpl`, and no Playwright follow-email spec exists.
  `MailInboxServiceIntegrationTest` asserts TTLs against Redis wall-clock windows (290-300 s).
- Gateway: `email_confirmation_tokens.user_id` has no index; no reserved usernames; avatars served at original size
  (see P1); root `SECURITY-FINDINGS.md` #2 and #3 are still open (`UpdateProfileRequestDTO.java:20-27` has no birthdate
  lower bound and accepts NUL characters; the birthdate is visible to every logged-in user).
- Style, grouped: about 26 one-line multi-call chains in gateway main (`CallerIdentityFilters.java:58`,
  `FollowCursorCodec.java:50`) and about 230 in its tests, about 30 in tweet, about 15 in timeline, about 10 in mail;
  gateway packages with more than 5 files (`configurations` 6, `services/{implementations,interfaces}/auth` 6 each;
  tests: `controllers` 15, `support` 13, `implementations/auth` 7), tweet (`configurations` 9,
  `DTOs/response/tweets` 6, test `support` 8); 8 field-order slips in timeline; mail import order in
  `ConfirmationEmailRenderer` / `FollowEmailRenderer`; `LINE_BREAKS` duplicated in mail; mail test `@Nested` names
  `Staleness` and `Leakage` are not method names; `DEFAULT_PAGE_SIZE` duplicated in two tweet controllers.

### Code (frontend)

- **In-progress work (uncommitted).** It is finished and coherent. Two gaps:
  - `PostActions.tsx:44-57` reports to `onChange` immediately, and `useOptimisticToggle` is called without `onFailure`.
    After a failed like or save request, Back shows an action that never happened. Pass `onFailure` that re-reports
    the old value.
  - Test coverage is one overlay test in `PostList.test.tsx`. Nothing covers `PostActions.onChange`, the `PostCell`
    re-key, or `TweetDetailsPage` reporting.
  - Minor: `oxlint-disable` at `PostUpdatesContext.tsx:40` has no explanatory comment, unlike `AuthContext`.
- People card goes stale after following on a profile: `usePersonFollow` state is local to `PersonCard` and
  `ProfileView`, the People panel stays mounted, so Back shows FOLLOW and the old count. `followChangeCount` only
  reloads an empty feed. Not mentioned in the docs; if you accept it, record it as a shortcut.
- 46 of 80 `font-size` declarations in 27 CSS files are below 0.75rem, down to 0.46875rem (`Ruler.css:25,39`); many are
  the 9.5 px mono labels.
- Visible-label rule: inputs use `sr-only` labels (`StepInput.tsx:34`, `ReplyTextField.tsx:58`); the visible question
  heading serves as the prompt, but the hard rule says "visible labels".
- Delete the stray untracked folder `frontend/src/components/shared/Shell/Header/UserMenu/node_modules/` (git-ignored).
- `tsconfig.app.json` has no explicit `"strict": true` (it is on via TypeScript 6.0.3's default, which was verified);
  `src/vite-env.d.ts` does not type `VITE_BASE_API_URL`, and `frontend/CLAUDE.md` does not document it.

## P3: nice to have

- **Pending style work, for planning.**
  - *Migration pass* (about 1-2 hours): explicit `strict`, 3 `is/has` renames (`offersResend` in `authErrors.ts:11` and
    `useStepFlow.ts:25`, `startsInCooldown` in `ResendButton.tsx:18`), 4 interface member reorders (`StageLink`,
    `DescentStage`, `ReplyTextField`, `PostListStatus`), `GlowButton.tsx:15` template-string className, 2 inline test
    types (`PostList.test.tsx:13`, `useOpenUsername.test.tsx:11`). Already done: semicolons, `ENDPOINTS`/`ROUTES`, rem
    units, named interfaces, `else/catch` layout, chains.
  - *Root-scaling refactor* (needs a visual check, L): `--home-scale` and `--auth-scale` are already gone. Still to do:
    the root font bands and the breakpoint comment in `index.css`, 8 `clamp()` sizes (6 font sizes such as
    `Arrival.css:11`, `StepFlow.css:8`, `ComposeModal.css:29`, plus `index.css:49`, `DescentStage.css:12`), 3
    media-query font sizes (`Ruler.css:39`, `TabRow.css:67`, `PostCell.css:74`), and the 46 small fonts.
  - Class prefix differs from the component in 3 places (`tweet-details-back`, `tab-panel`, `person-list`).
- **Frontend smaller items.**
  - HTTP status constants repeated across 8 files (404 ×4, 400 ×3, 401 ×2, 0 ×2, 503 ×1): move to `api/http.ts`.
  - `useTweetDetails` and `useProfile` repeat the load/404/retry pattern, `STATUS_OF_PROBLEM` exists twice, and the list
    footer (sentinel, status, live region) is triplicated in `PostList.tsx:78`, `PeopleList.tsx:34`,
    `ReplyThread.tsx:57`.
  - Unmemoized context values in `AuthContext` and `PostUpdatesContext`; `PostList.tsx:72` creates a new object per
    cell each render, so all cells re-render on the minute tick, with no virtualization.
  - Hidden mounted panels keep 60 s timers and the feed poll (documented for the poll).
  - `bidi.ts:3` holds raw invisible characters in its regex (use `\u` escapes), `tweetPath` does not encode its id
    (unlike `profilePath`), `DEFAULT_TAB` is used only by its own test, `ProtectedRoute` renders blank while the
    session loads, the `/confirm` token stays in the URL, `ReplyCell.tsx` is 188 lines and not in the accepted list,
    `PostList.test.tsx` is 1455 lines, there is no skip link, and unknown URLs silently redirect (deliberate and
    tested).
  - A single 333 kB chunk is acceptable; route-level `React.lazy` is optional.
- **Toolchain.**
  - Add scripts: `typecheck` (`tsc -b`), `check` (lint, typecheck, test), `test:watch`. The e2e `package.json` has no
    typecheck, so Playwright (which does not typecheck) hides type errors in specs.
  - TypeScript is `~6.0.2` in the frontend but `^7.0.2` in e2e, and e2e `@types/node` is ^26 against Node 24.
  - `npm outdated` shows only patch bumps (vite 8.3.1 → 8.3.4, plugin-react 6.1.2, oxlint 1.87, `@types/node` 24.19.1)
    plus TypeScript 6 → 7. `npm audit` in `e2e/` was not run.
  - `.oxlintrc.json` runs correctness rules only. Adding `jsx-a11y` gives 12 mostly intentional warnings; the `vitest`
    plugin gives 187 (mostly mock type-parameter nits).
- **Duplication across the Java services** (a shared module, if you ever want one): the body-size filter, wrapper,
  `ErrorResponseWriter` and `PageSizeValidator` exist in the gateway, tweet and timeline; `ImageSignatureValidator`,
  `OutboxPublisherServiceImpl`, `MinioBucketInitializer` and `ObjectStorageServiceImpl` exist in gateway and tweet;
  `CurrentUserIdArgumentResolver`, `DownstreamFailures`, `DownstreamLookupSupport` and `HttpClientConfiguration` in
  timeline copy others; `KafkaErrorHandlingConfiguration` and `KafkaConsumerConfiguration` are near-copies in mail and
  timeline (the DLT `KafkaTemplate`s are never closed in either); `Instant.now(clock).truncatedTo(MILLIS)` appears six
  times in three services. Under your "build only what is used" rule this is a decision, not an automatic task.
- **Other.**
  - Timeline: DLT replication factor hardcoded to 1; there is no dead-letter replay tool (a gateway outage longer than 4
    minutes dead-letters those fan-outs for good); `ix_feed_entries_user_author` may be redundant with the primary-key
    `user_id` prefix (measure before dropping); every fan-out row writes 4 indexes.
  - Tweet: `countByAuthorId` is O(the author's tweets); the outbox sends and deletes one message at a time.
  - Mail: no `spring.data.redis.timeout`; no `Auto-Submitted` header or From display name; Gmail is capped at about 500
    recipients a day and has no SPF/DKIM for your domain, so use a transactional provider (no code change: host, port,
    user, password).
  - There is no account deletion or password reset (out of scope per PLAN), but real emails will be stored.
  - The e2e setup is documented only in the root README in two lines.

## Decisions needed before planning

- **Deployment target.** Docker Compose on one host, or Kubernetes / a PaaS? The prod overlay, secrets handling,
  startup order (compose `depends_on` is currently what keeps topic creation and the seeder from racing) and the proxy
  all depend on it.
- **The seeder in production.** Keep it at all? Putting the demo data behind a dev-only flag is cheap either way.
- **The seed photos.** Replace with generated or licensed avatars, or keep them local only?
- **Error boundary.** Allow one class component (or a library) as the exception to "functional only"?
- **Branding.** The classic Twitter bird and the name "Twitter" on a public deployment.
- **Documents in a public repo.** This file, `SECURITY-FINDINGS.md` and the exploit report list weaknesses. Decide
  what stays in git.

## What is in good shape (do not touch)

- **Cross-service consistency.** Spring Boot 4.1.1 and Java 25 are identical in all four poms and match the
  `eclipse-temurin:25` Dockerfiles; Maven wrapper 3.9.16 is identical everywhere. Event DTO field names, types and keys
  match `EVENTS.md` and the consumers. Internal HTTP paths, DTOs and headers (`/internal/v1`, `X-Internal-Secret`,
  `X-User-Id`) agree between callers and callees.
- **Tests.** Deterministic: no `Thread.sleep`, `@Disabled`, `.only`, `.skip`, random ids or `waitFor` in the unit and
  integration suites (mutable clock, `TestIds`, fixed seeds), no assertion-free tests. `TESTING.md` matches the test
  names in tweet, timeline and mail. e2e has no `waitForTimeout` or `networkidle`, 78 specs matching the 78/78 in the
  docs, parallel-safe (UUID users), selectors mostly by role/label, isolated project name with `down -v` teardown.
- **Persistence.** All queries are bounded and page-limited, no N+1 (fetch joins and projections), needed indexes
  exist and match the queries issued (tweet `{authorId, createdAt -1, _id -1}`, replies, outbox; timeline: every SQL
  statement has an index). Mongo writes for tweet, reply and outbox share one transaction. Published outbox rows are
  deleted, so the collection does not grow. Consumers are idempotent (`INSERT … ON CONFLICT DO NOTHING`);
  `tweet.deleted` is one transaction with a retry test; like/view counter lock ordering is correct.
- **Security basics.** The gateway strips every `X-User-*` header, the cookie and `Authorization`, then sets `X-User-Id`
  from the verified JWT; cookie flags other than `Secure` are sound; JWT validation is sound; secrets are scoped (the
  timeline holds the gateway secret only on the gateway client, 32-byte check); no secrets or PII in logs; header
  injection, HTML escaping, STARTTLS with server identity checks and SMTP timeouts all check out in the mail service;
  upload validation (magic bytes, 5 MB per file, 4 images, body cap on every route); actuator exposes `health` only;
  error bodies are fixed messages.
- **Frontend.** No `innerHTML`, web storage, open-redirect params, `target=_blank` or hardcoded origins; ids are gated;
  every timer, listener and object URL is cleaned up; pagination uses a generation counter; 401 handling works;
  optimistic rollback exists for like, save and follow; no dead files, unused CSS classes, console calls, TODOs or
  commented-out code; icon-only buttons are labelled and reduced motion is handled. Style conformance is high: 0
  violations for semicolons, `} else/catch`, `as`/`!`/`any` outside `src/api`, raw colors outside `index.css`, class
  or arrow components, and touch targets under 44 px.
- **Repo.** `.env` is untracked and its key set is a subset of `.env.example`; no secrets found in git history; no
  tracked `.DS_Store`, `.idea` or `target`; all text files are LF; no `dist/` or e2e artifacts tracked.
- **Containers.** All four images run as non-root with multi-stage builds, cached dependency layers and wget
  healthchecks. Compose has restart policies, resource limits, healthchecks and loopback-only ports; the PG18 volume
  path is correct; image tags are pinned.

## Branch and working tree

- `seed-demo-data` is 1 commit ahead of `main` (`0e301de`, the seeder), pushed to `origin/seed-demo-data`, not merged.
- Uncommitted: `frontend/CLAUDE.md`, `PostActions.tsx`, `PostCell.tsx`, `PostList.tsx`, `PostList.test.tsx`,
  `Shell.tsx`, `ProfileView.test.tsx`, `TweetDetailsPage.tsx`, `TweetDetailsPage.test.tsx`, and the new
  `frontend/src/contexts/PostUpdatesContext.tsx`.
- Untracked: the 6 seed photos in `twitter_api_gateway/seed-images/` (see P0 #4).

## How much of this was verified

- **Run directly:** the four Maven suites, the frontend `tsc`, lint, unit tests (twice), build and `npm audit`;
  `gh repo view` (repo is public); the registration regex against sample addresses; the `SEED_*` and `COOKIE_SECURE`
  defaults in `application.properties`; the outbox default of 3 attempts.
- **Read from code by the reviewers** (not run): everything else, with `path:line` evidence as given above. Docker could
  not be used on this machine, so nothing was tried against a running stack.
- **Unverified claims:** the Kafka volume path (checked against upstream trunk, not the 4.3.1 image), the multipart
  500 in the tweet service, the e2e setup timeout, the unused pom dependencies (confirm with `mvn dependency:analyze`),
  and the exact startup behaviour when optional `.env.example` keys are copied as empty values.

## Suggested order

1. **Safe defaults and repo hygiene:** P0 #2 and #4, the regex fix, the `.env.example` rewrite, delete stale doc
   blocks and Dockerfile comments.
2. **Deployment topology:** frontend image and proxy, prod compose overlay, per-service secrets, public URLs, TLS,
   `forward-headers-strategy`, the ingress blocks for `/internal` and `/actuator`.
3. **Reliability:** outbox retry and ordering, event staleness guards, mail startup validation and claim TTL, MinIO
   timeouts, graceful shutdown, Hikari and poll settings, JVM flags, per-listener consumer groups.
4. **Hardening:** tweet-service secret, rate limiting, Mongo/Kafka/MinIO/Postgres accounts, EXIF stripping, avatar
   resizing, CVE scan, Flyway baseline.
5. **Frontend robustness:** request timeout/abort and 429, error boundary decision, per-route titles and focus, avatar
   `loading`/`decoding`, `index.html` meta, CSP, self-hosted fonts, favicon.
6. **Operations:** CI/CD, backups, structured logs and metrics, production smoke test in e2e.
7. **Cleanup and pending style work:** docs pile, dead code and pom dependencies, test isolation, the migration pass,
   then the root-scaling refactor with its visual check.
