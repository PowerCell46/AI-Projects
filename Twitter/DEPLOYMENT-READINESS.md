# Deployment readiness review

First written 2026-10-09 and re-verified the same day against commit `70fea9d`. Branch: `seed-demo-data` (two commits
ahead of `main`, both pushed to `origin/seed-demo-data`).

Scope: the whole system: `twitter_api_gateway`, `twitter_tweet_service`, `twitter_timeline_service`,
`twitter_mail_service`, `frontend/`, `e2e/`, the compose files, the Dockerfiles and the docs.

How this version was made: five read-only reviewers re-checked every item of the first version against `70fea9d`
(P0 and infra, P1 bugs and security, frontend and e2e, docs, backend code). The lead re-ran the builds and tests.
Every `path:line` below is for `70fea9d`. Items marked "carried over" were not re-checked in this pass.

## Verdict

The code is in good shape. What is missing is the layer between "works in dev compose" and "deployable", and what is
left of it is below. Everything fixed, accepted or decided in this review (the four P0 items and the first P1 items) is
gone from this file: it lives in the code, the compose files, the README and each service's `DECISIONS.md`.

| Check | Result |
| --- | --- |
| Gateway tests (`./mvnw -B verify`) | 920 pass, 0 failures, 0 skipped |
| Tweet service tests | 490 pass |
| Timeline service tests | 731 pass |
| Mail service tests | 245 pass |
| Maven warnings, all four services | none (no `[WARNING]` line in any of the logs) |
| Frontend `tsc -b` | clean |
| Frontend lint (oxlint, `--deny-warnings`) | exit 0, no output |
| Frontend unit tests | 1153 pass |
| Frontend build | OK; main JS 333 kB (104 kB gzip), CSS 44 kB; `dist/` holds only the html, css and js files (no source maps) |
| `npm audit` in `frontend/` and `e2e/`, with and without dev | 0 vulnerabilities in all four runs |
| e2e suite | not run in this pass (it builds four images and starts the whole stack). 79 specs, reviewed statically |

Legend: effort **S** (under half a day), **M** (about a day or two), **L** (more than that).

## P1: fix before deployment

### Security

- [ ] **Data stores and accounts (S/M).**
  - Mongo has no auth or keyfile (`docker-compose.infra.yml:126` runs `--replSet rs0 --bind_ip_all`) and
    `directConnection=true` is hardcoded in compose (`docker-compose.yml:55`), so there is no replica-set failover and
    no `serverSelectionTimeoutMS` (default 30 s). `rs.initiate` uses `localhost:27017` (infra `:142`). Have compose read
    `${MONGODB_URI}` with credentials, `authSource`, `replicaSet` and short timeouts.
  - Kafka is single-node PLAINTEXT with no auth (infra `:58-62`). The apps connect as the Postgres superuser
    (`docker-compose.yml:22-23`, infra `:8-9` and, for the timeline, `:31-32`) and as MinIO root (infra `:106-107`),
    and MinIO runs as `user: "0:0"` (infra `:100`) with no comment. Create app roles and MinIO service accounts scoped
    to the two buckets.
  - Redis has `--requirepass` (infra `:152`) but no `maxmemory` against a 128 MB limit (infra `:161`).
  - The infra ports (Postgres, Kafka, MinIO and its console, Mongo, Redis) are still published on `127.0.0.1` in the
    prod overlay. Only local users can reach them; reset them in `docker-compose.prod.yml` if you want none.

- [ ] **EXIF/GPS is kept on every uploaded image (M).** Nothing in the gateway or tweet main code re-encodes or strips
  metadata. Strip it before `put`. Documented trigger: "before any public deployment" (`twitter_tweet_service/PLAN.md:61`,
  `twitter_api_gateway/PLAN.md:52`).

- [ ] **No dependency vulnerability scan has ever run (S).** All four services' `docs/SECURITY-AUDITS.md` say
  "dependencies were not scanned" (tweet `:30,69`, mail `:52`, gateway `:34`, timeline `:7`). There is no
  dependency-check, osv-scanner or dependabot config. Run OWASP dependency-check or osv-scanner on Spring Boot 4.1.1,
  minio 9.0.3 and okhttp 5.3.2 (`okhttp-jvm` is a hardcoded version in **both** the gateway pom, `:62-64`, and the
  tweet pom, `:53-55`).

### Operations

- [ ] **Schema is managed by `ddl-auto=update` with no migrations (M).**
  - Gateway `application.properties:16` and timeline `:18`; neither has Flyway. `@Check` constraints are never added to
    existing tables; column widening is manual (gateway `DECISIONS.md:90`, `@Check` at `:99-100`). In the timeline
    service the primary-key column order depends on Hibernate's attribute-name ordering
    (`twitter_timeline_service/DECISIONS.md:54`), so drift would be silent.
  - Both documented triggers ("a second environment", gateway `PLAN.md:49`, timeline `PLAN.md:111`) are met.
  - Fix: Flyway baseline V1 from the current schema, then `ddl-auto=validate`, before any production data exists.
  - Tweet service: `auto-index-creation=true` (`application.properties:19`) fails startup on an index conflict, so
    later index changes need a manual migration.

- [ ] **Runtime settings are at Spring Boot defaults in every service (S).** The health and management settings still differ across services:
  - Tweet health covers Mongo only; mail has no Kafka listener indicator; no liveness/readiness groups are
    enabled; management settings differ across services (mail sets `show-details=always`, `application.properties:80`;
    the timeline sets only `exposure.include`, `:106`; gateway and tweet set nothing).

- [ ] **Kafka topics have no single owner (S).** Topics are created by four services' `KafkaTopicConfiguration` beans
  while broker auto-create stays on, and DLT replicas are hardcoded to 1 (mail `KafkaTopicConfiguration.java:16`,
  timeline `configurations/kafka/KafkaTopicConfiguration.java:17`). Consider one owner for topics.

- [ ] **No CI/CD and no backups (M/L).**
  - There is no `.github` directory, no `.gitlab-ci.yml`, and `git ls-files` finds no pipeline file. All poms are
    `0.0.1-SNAPSHOT`, and there is no image tagging scheme.
  - Tests need Docker and Testcontainers. A pipeline should run: `npm ci`, build, lint, test, `npm audit --omit=dev`,
    the Maven suites, image build and push, a dependency scan, then e2e. The e2e config has no CI mode
    (`playwright.config.ts` has `retries: 0`, `reporter: 'html'` and no `forbidOnly`; add `forbidOnly`, `retries: 1`, a
    non-opening reporter, and screenshot/video on failure).
  - Add backup jobs and restore tests for the two Postgres databases, Mongo and MinIO.
  - The frontend image, its nginx config and `docker-compose.prod.yml` were checked by hand, not by a test: the e2e
    suite still runs the Vite dev server (`playwright.config.ts:19-24`), so a smoke project against the production
    stack can be added. Certificate issue and renewal are manual (the README says how); an ACME client is not part of
    the stack.

- [ ] **Logging and monitoring (S/M).** Plain-text logs, no compose `logging:` rotation (json-file is unbounded), no
  Micrometer registry or tracing, and nothing alerts on a dead-lettered record. The gateway
  and the tweet service answer `DEGRADED` on `/actuator/health` while outbox rows are `FAILED`, but nothing watches it and
  there is no metric. Fix: `logging.structured.format.console=ecs`, a Prometheus registry, rotation in compose.

### Frontend

- [ ] **Avatars load full-size originals (S frontend, M backend).**
  - `components/shared/Avatar/Avatar.tsx:15` has no `loading="lazy"` or `decoding="async"` (`loading="lazy"` appears
    only in `PostImages.tsx:24`). Avatars show at 30-84 px in every post cell and People card, while uploads can be
    5 MB (seed images 1.4-7.4 MB, up to 6016×6016) and the backend does no resizing (documented trigger "before public
    deployment", gateway `PLAN.md:132`).
  - Fix: both attributes on `Avatar` now; thumbnail plus full variant on upload (or presigned URLs / a CDN) in the
    backend.

- [ ] **No request timeout or abort, and no 429 handling (S).**
  - `api/http.ts:64-82` is a bare `fetch` with `credentials` and no signal; `AbortSignal`, `AbortController` and `429`
    appear nowhere in non-test source (the only 429 is `utils/authErrors.test.ts:93`, mapped to "signal lost"). A hung
    request leaves `usePagedList.isFetchingRef` true, so the list shows "FETCHING MORE" forever and optimistic toggles
    stay in flight. (No rate limit is planned, so this stack sends no 429; a proxy in front could.)
  - Fix: `AbortSignal.timeout(...)` in `send` that throws `ApiError(0)`, plus a 429 message.

- [ ] **No error boundary (S, needs a decision).** Any render-time throw leaves a blank page (`main.tsx` has only
  `StrictMode`; no `ErrorBoundary`, `componentDidCatch`, `getDerivedStateFromError` or `errorElement` anywhere). An
  error boundary needs a class component or a new dependency, which conflicts with the "functional components only"
  hard rule in the root `CLAUDE.md`.

- [ ] **No per-route titles and no focus move on route change (S-M).** `index.html:13` has one static `<title>` and
  `document.title` is set nowhere. Opening details or a profile only calls `window.scrollTo`
  (`TweetDetailsPage.tsx:52`, `ProfilePage.tsx:29`), so focus stays on the now-hidden clicked element. The `h1`s on
  Feed, People, Post and Profile are `sr-only` with no `tabIndex`. Add a `useDocumentTitle` hook and focus the page
  `h1` (`tabIndex={-1}`, as `Arrival.tsx:19,25` already does).

- [ ] **`index.html` and the pre-deployment items you deferred (S-M).**
  - `frontend/PLAN.md:332-333` and `frontend/SECURITY-FINDINGS.md:14-24` deferred these "before any deployment":
    `<meta name="referrer">`, a CSP and self-hosted fonts.
  - CSP shape: scripts are all external, so `script-src 'self'` works. You need `style-src-attr 'unsafe-inline'`
    (inline `style=` in `TabRow.tsx:80`, `Horizon.tsx:22`, `Ruler.tsx:23`) and `img-src 'self' blob:`
    (`createObjectURL` in `useImageAttachments.ts:53` and `usePhotoPick.ts:51`).
  - Put the CSP, HSTS (the TLS server does not send it yet) and other response headers in `frontend/nginx/app.conf` and
    repeat them in each `location` there that sets `add_header`, because nginx does not inherit `add_header` into a
    location that sets its own. `app.conf` is shared by the dev and the TLS server.
  - Google Fonts (`index.html:7-12`) sends visitor IPs to Google, a GDPR concern for an EU deployment.
  - The favicon (`index.html:4`) is `public/Twitter-Logo.png`: 684,454 bytes, 1383×1137 RGBA, declared
    `type="image/svg+xml"`, referenced nowhere else, and a dark-navy bird on transparent, nearly invisible on dark tabs.
    Ship small square icons (32/180/512 px) with correct MIME types.
  - Missing meta: referrer, CSP, description, `theme-color`, apple-touch-icon, manifest.
  - Branding: it is the classic Twitter bird under the title "Twitter". That is a trademark decision for you before
    going public.

## P2: cleanup

### Docs

- Gateway `docs/SECURITY-AUDITS.md` was last updated 2026-10-04 (`:3`) and predates the seeder, PATCH profile, likes and
  author-tweets. The 2026-10-08 audit of PATCH and author-tweets lives only in root `SECURITY-FINDINGS.md`, and no
  document audits the seeder. Gateway `DECISIONS.md` still has 47 "step N" references although `PLAN.md` was trimmed to
  a phase table (some of them are frontend-plan steps).
- Timeline docs: `CLAUDE.md:7-9` omits `/api/v1/author-tweets/*` (route at `AuthorTweetsController.java:19`); `PLAN.md:43`
  only says "tweets by ids, tweets by author" and the `/page` read is named only in code
  (`TweetLookupServiceImpl.java:36`); `DECISIONS.md`'s 43 "step N" references point nowhere because the steps were
  trimmed (`PLAN.md:6`); `docs/TESTING.md:19` and `:100` are stale ("Written `@Disabled` in step 5…", "endpoint doesn't
  exist yet"), `:33` gives the old key set without likes, `likedByMe` or `replyCount`, and `:11-12,162,266,355,448` keep
  the "written `@Disabled` first" wording although no `@Disabled` remains in the timeline tests.
- Tweet docs: `PLAN.md:48` still says "Out of scope: tweets by user"; the route list (`PLAN.md:25-28`) and
  `CLAUDE.md:3-9` omit the by-author, page and count routes (`InternalTweetController.java:34,39,48`,
  `TweetController.java:61`), which are described only in `DECISIONS.md:80,181`;
  `src/test/resources/application-test.properties:1-4` repeats the main Kafka logging lines (`application.properties:102-104`).
- Mail docs: `CLAUDE.md:70` still names "the `@Disabled` test catalog of steps 2 and 10" (none remains in `src/test`);
  `CLAUDE.md:49` places validate/claim/send/mark in the notification service although they now live in
  `MailEventValidationService` and `MailDispatchService` (`PLAN.md:27-33` is right); `PLAN.md:22` says infra is in
  `../docker-compose.yml` (redis and mailpit are in `docker-compose.infra.yml:149,171`; `CLAUDE.md:16` is right);
  `CLAUDE.md:8-9` says "numbered steps with gates" while `PLAN.md:9-10` says the gates were trimmed.
- Frontend `PLAN.md`: `:349` says "Path segments are not encoded in ENDPOINTS", but `api/endpoints.ts:7` has
  `segment()`; the "Out of scope" list (`:378-381`) still names profile pages, follow UI, the tweet-details page, the
  edit-account page and containerisation, and only containerisation is unbuilt; a stray blank line at `:26` breaks the
  status table, and lines 3-4 have an unbalanced `**`; `:79` says "78/78 green 3x" but there are 79 specs now.
- `frontend/PLAN.md` (32,184 bytes) and `DECISIONS.md` (34,884 bytes) record the same step-by-step results and
  `DECISIONS.md` holds superseded decisions (for example "POST button has no handler yet", `:50`). Mark them
  historical or trim.
- `frontend/exploit-report-2026-10-01.md` (tracked, 5,076 bytes): no secrets, but it is superseded and lists weaknesses
  in a public repo. Its findings 1-2 are tracked as `frontend/SECURITY-FINDINGS.md:14` (#1, "still open from
  2026-10-01"; the first version wrongly said root #1) and 3-4 by the gateway accepted gap (`PLAN.md:36`) and
  `frontend/SECURITY-FINDINGS.md:50`, so the overlap is complete. It is cited only by `frontend/PLAN.md:332,333,345`.
  Fold it into `SECURITY-FINDINGS.md` as "Audit 2026-10-01" or delete it (git history keeps it).
- Root `PLAN.md` is slightly stale after the trim: `:9-10` still says "gateway phase 4 may start", `:25` cites the
  `user.followed` change (`85019be`) as news, and `:27` says the timeline has "three phases" while describing phases 4-5.
- Root `README.md` (117 lines) never mentions the seeder, its 12 `@seed.local` users, `Password123`, `seed-images`, or the
  e2e ports and prerequisites (`:106-108` says only `npm test` in `e2e/`, and there is
  no `e2e/README.md`). `seed-images/README.md` now documents the seeder but still ends with a dated to-do list
  ("Still to add or fix (as of 2026-10-08)").
- `.claude/skills/java-junit` is the only tracked file under `.claude`, while the other skills are global
  (`~/.claude/skills` has `java-code-style` and `frontend-code-style` but no `java-junit`). All four service `CLAUDE.md`
  files mandate `java-junit` (for example gateway `CLAUDE.md:55`), so a fresh clone lacks the skills the docs require.

### Docker and infra hygiene

- Only `twitter_tweet_service/.dockerignore` (tracked, 16 lines) and `frontend/.dockerignore` exist. The gateway,
  timeline and mail have none. The gateway build context carries `target/` (109 MB), `seed-images/` (about 23 MB) and `.idea`; the other
  targets are 76 MB (tweet), 81 MB (timeline) and 58 MB (mail). (The `COPY` lines are explicit, so nothing leaks into
  the image.) The frontend now has one (`frontend/.dockerignore`: `node_modules`, `dist`, `*.md`, env files; it keeps the
  tests because `tsc -b` in the image build type-checks them).
- Base image tags float: the Dockerfiles use `eclipse-temurin:25-jdk` and `25-jre` (major only, not digest-pinned) and
  infra uses `postgres:18`, `mongo:8.2`, `redis:8.10.2`, `kafka:4.3.1`, `kafka-ui:v1.5.0` and `mailpit:v1.31.2`. The four
  Dockerfiles are identical apart from `EXPOSE` and the comment: use `COPY --chown` (there is a separate
  `RUN chown` at `:34`), BuildKit cache mounts for `~/.m2`, and digest-pinned bases.
- `alpine/minio:RELEASE.2025-10-15…` (`docker-compose.infra.yml:98`) is a third-party mirror about a year old; check its
  upstream support status.
- e2e: `docker-compose.e2e.yml:5` (`"5452:5432"`) and `:142` (`"8180:8080"`) publish Postgres and the gateway on
  `0.0.0.0` with `twitter_e2e` credentials (mailpit, `:126`, is already loopback), and nothing in `tests/` uses 5452.
  Bind to loopback. Ports 5273, 8180, 8125 and 5452 are hardcoded in several files with no override
  (`playwright.config.ts:3-4`, compose `:158`, `fixtures.ts:47`).
- e2e: `global-setup.ts:10-18` runs `up -d --build --wait` with a 300 s timeout (`:17`), which includes four Maven image
  builds; healthchecks allow about 90 s per Spring service. Raise to about 900 s or build in a separate step
  (unverified). There is already an `E2E_NO_BUILD=1` skip (`test:fast`).
- e2e never touches a production build, an SPA fallback or `/api` through a real proxy (`webServer` runs `npm run dev`,
  `playwright.config.ts:19-24`). The frontend container exists now, so a smoke project against it can be added.
- e2e covers desktop Chromium only, with `reducedMotion: 'reduce'` globally and no mobile project; `auth.spec.ts` uses
  no semicolons while `fixtures.ts` (95 lines) and `follow-email.spec.ts` (13) do.
- No `LICENSE` (a public repo is all-rights-reserved by default) and no root `.gitattributes` (only the four services
  have one).
- No Node pin: no `engines`, `.nvmrc`, `.node-version` or `packageManager` in `frontend/` or `e2e/`, although Vite 8
  needs Node 20.19 or later and you run 24.13.

### Code (backend)

- **Dead code and unused dependencies** (the `70fea9d` pom changes were blank-line removals only)
  - Tweet pom: `spring-boot-starter-validation` (`:33`), `-validation-test` (`:74`), `spring-boot-starter-kafka-test`
    (`:69`), `testcontainers-junit-jupiter` (`:89`). Nothing in `src/main` uses `jakarta.validation` (the only
    validation import is `org.springframework.validation.FieldError`), and tests use no `jakarta.validation`,
    `@Testcontainers` or `org.springframework.kafka.test`.
  - Timeline pom: `testcontainers-junit-jupiter` (`:83`) has no usage; the `<description>` (`:15`) still says "feed,
    saved tweets and views".
  - Mail pom: unused test deps `spring-boot-starter-kafka-test` (`:63`), `testcontainers-junit-jupiter` (`:83`),
    `webmvc-test` (`:58`); no test uses MockMvc. JUnit and Mockito arrive only transitively, so add
    `spring-boot-starter-test` before removing them.
  - Dead exception handlers: `GlobalExceptionHandler.handleMethodArgumentNotValid` plus `describe` in tweet
    (`:168`, `:268`) and timeline (`:89`, `:180`), and `handleMaxUploadSizeExceededException` in timeline (import `:19`,
    handler `:133`, test `GlobalExceptionHandlerTest:121`; no multipart endpoints). Neither service has `@Valid` or
    `@Validated` on an endpoint.
  - Confirm each with `mvn dependency:analyze` and a build.
- Project rule breaks:
  - `Instant.now()` in main code: `ErrorResponseWriter.java:34` in all three services (tweet `utilities/`, timeline and
    gateway `utilities/web/`), `GlobalExceptionHandler` (timeline `:185`, tweet `:273`, gateway `:259`), and
    `twitter_api_gateway/.../auth/TokenServiceImpl.java:36` (not in the first version).
  - The `ErrorResponseWriter` Javadoc (`:18`) in tweet and timeline mentions `AuthenticationEntryPoint` although there
    is no Spring Security there (the gateway copy is accurate).
  - One TODO comment is left, in the timeline: `jobs/FeedRetentionJob.java:14` ("Add short java doc what this chron job
    does").
- Timeline: `UserLookupServiceImpl.java:44-46` throws an NPE (500) on an empty body, `null` or a null id;
  `FollowerLookupServiceImpl.java:36-40` throws an NPE on a null page or null `ids` (in a listener: about 4 minutes of
  retries, then dead-letter). `TweetLookupServiceImpl.requireWellFormed` (`:110`) was hardened in the 2026-10-07 audit
  but these two were not, and their test suites have no such case.
- Remaining back-fill item: the log-forging line still logs the request path in the tweet service
  (`GlobalExceptionHandler.java:232`, `e.getResourcePath()`) and the gateway (`:221`); the timeline's was fixed. The
  one-minute `curl` check and the unseen `/liked` browser check were not repeated.
- Tweet: a garbled or truncated multipart body, or more than 10 parts, should hit the catch-all (500 plus an ERROR
  stack trace, `handleUnexpectedError` at `GlobalExceptionHandler.java:162`), because neither the advice nor
  `ResponseEntityExceptionHandler` handles Spring's `MultipartException` (not referenced in main code; checked in the
  jar, not run). Map it to 400 and add a test. No test covers `handleHttpMessageNotReadable` (`:183`).
- Mail: `MailInboxServiceImpl:62-64` swallows a `markSent` failure, so the catch at `MailDispatchServiceImpl:49-51` is
  unreachable in production, and the unit test that mocks it (`UserConfirmationNotificationServiceImplTest.java:136`)
  throws something that cannot happen. Keep one catch and test it with a real Redis failure.
- Mail follow email links to `/feed` (`FollowEmailRenderer.java:15`) and reads "ana (@ana)" (display name and handle
  identical; `followEmail.txt`, `followEmail.html:46`). The "SPA gets a profile page" trigger is met
  (`frontend/src/routes.ts:11` has `/users/:username`): link to `APP_BASE_URL/users/<follower>`. The new e2e spec now
  asserts the "name (@name)" text and a `/feed` link, so change both together.
- Seeder robustness: `TweetSeedServiceImpl.java:70-89` has no connect retry (it logs a warning and returns what it
  created; the likes retry up to 36 times at 5 s, about 3 minutes, `LikeSeedServiceImpl.java:28`), and compose starts
  `tweet-service` independently of the gateway (`depends_on` at `:42-48` and `:70-76` do not reference each other), so a
  cold `up` can leave users without tweets for good. Reuse the like retry. The only seed tests are
  `DataSeedServiceImplTest` (mocks every seeder) and `ProfilePictureSeedServiceImplTest`: none for the
  Tweet/Reply/Like seed services, the `DataSeedRunner` gate or `PathMultipartFile`.
- Timeline test gaps: nobody asserts `ix_feed_entries_tweet` or `ix_feed_entries_tweet_created` (`FeedEntry.java:39,41`,
  which `tweet.deleted` and retention depend on), and `AbstractListenerIntegrationTest.java:332,365` scan whole tables
  with `findAll()` on every Awaitility poll.
- Tweet test isolation: `configurations/mongo/MongoCollectionInitializerIntegrationTest.java:62-63` drops the shared
  `tweets` and `outbox` collections and their indexes without restoring them (it passes only because the
  index-asserting `@DataMongoTest` classes use a different Spring context);
  `OutboxPublisherServiceKafkaIntegrationTest` reads only the oldest 500 `PENDING` rows (`app.outbox.batch-size`,
  `application.properties:72`) of a shared outbox no test clears (backlog size not measured); the explain tests
  (`TweetRepositoryIntegrationTest.java:~380-435`) hand-build their own filters instead of calling the repository.
- Mail test isolation: `TwitterMailServiceApplicationTests`, `MailTlsConfigurationIntegrationTest` and
  `MailInboxServiceIntegrationTest` boot full contexts with listeners on the same topics and group as the shared e2e
  context. Set `spring.kafka.listener.auto-startup=false` in those three (no `auto-startup` setting exists anywhere
  under `twitter_mail_service/src`). `MailDispatchServiceImpl` and `MailEventValidationServiceImpl` have no test class
  of their own (they are built for real inside the two notification tests, and only the DTO validation tests cover the
  constraints). `MailInboxServiceIntegrationTest.java:46` asserts TTLs against Redis wall-clock windows (290-300 s).
- Gateway: `email_confirmation_tokens.user_id` has no index (`EmailConfirmationToken.java:36-40` has only the
  `token_hash` unique constraint); no reserved usernames (`RegisterRequestDTO.java:28` is only a regex); avatars served
  at original size (see P1); root `SECURITY-FINDINGS.md` #2 and #3 are still open (`UpdateProfileRequestDTO.java:20-27`
  has `@Past` only, no birthdate lower bound, and `MaxCodePointsValidator` does not reject NUL; `ProfileMapper.java:24`
  still returns the birthdate to every logged-in user).
- Style, grouped (chain and field-order counts are from the first pass, not recounted): about 26 one-line multi-call
  chains in gateway main (`configurations/routing/CallerIdentityFilters.java:58`, `utilities/follows/FollowCursorCodec.java:50`)
  and about 230 in its tests, about 30 in tweet, about 15 in timeline, about 10 in mail; gateway packages with more
  than 5 files (`configurations` 6, `services/{implementations,interfaces}/auth` 6 each; tests: `controllers` 15,
  `support` 13, `implementations/auth` 7), tweet (`DTOs/response/tweets` 6, test `support` 8; its `configurations` is
  fixed) and, newly counted, timeline test `support` 8 and mail test `support` 8; 8 field-order slips in timeline; mail
  import order in `ConfirmationEmailRenderer.java:3-4` / `FollowEmailRenderer.java:3-6` (`com.peter_gerdzhikov` before
  `java.*`); `LINE_BREAKS` duplicated in mail (`EmailTemplate.java:26`, `MailDeliveryServiceImpl.java:28`); mail test
  `@Nested` names `Staleness` (`UserConfirmationNotificationServiceImplTest.java:167`) and `Leakage`
  (`MailDeliveryServiceImplTest.java:237`) are not method names; `DEFAULT_PAGE_SIZE` duplicated in
  `InternalTweetController.java:30` and `ReplyController.java:31`.

### Code (frontend)

- **The committed `PostUpdatesContext` work.** It is finished and coherent. Two gaps remain:
  - `PostActions.tsx:44-57` reports to `onChange` immediately, and `useOptimisticToggle` is called without `onFailure`
    (`:31-38`), although the hook supports it (`hooks/useOptimisticToggle.ts:16,43`, tested at
    `useOptimisticToggle.test.tsx:158-188`; only `usePersonFollow.ts:40` passes it). After a failed like or save
    request, Back shows an action that never happened. Pass `onFailure` that re-reports the old value.
  - Test coverage is one overlay test in `PostList.test.tsx:1419-1454` (like state, like count and reply count after
    `reportUpdate`; not saves, not the toggles after the re-key). `PostActions.test.tsx` has no `onChange` test,
    `PostCell.test.tsx` has no re-key test, and `TweetDetailsPage.test.tsx` only wraps the page in the provider
    (`:117-122`) with no reporting assertion.
  - Minor: `oxlint-disable` at `PostUpdatesContext.tsx:40` has no explanatory comment, unlike `AuthContext.tsx:122-123`.
- People card goes stale after following on a profile: `usePersonFollow` state is local to `PersonCard` (`:28`) and
  `ProfileView` (`:28`), the People panel stays mounted, so Back shows FOLLOW and the old count. `PostUpdatesContext`
  carries only `likedByMe`, `likes`, `savedByMe` and `replyCount`, and `followChangeCount` (`Shell.tsx:26,55`) only
  reloads an empty feed. Not recorded as a shortcut (`frontend/CLAUDE.md:111` records only the PostUpdates one); if you
  accept it, record it.
- 46 of 88 `font-size` declarations (80 plain rem values, 6 `clamp()`, `100%` and `0.34em`) across 37 CSS files are
  below 0.75rem, in 27 files, down to 0.46875rem (`Ruler.css:25,39`); another 14 are exactly 0.75rem. Many are the
  9.5 px mono labels. (The first version counted rem values only: "46 of 80 in 27 files".)
- Visible-label rule: inputs use `sr-only` labels (`StepInput.tsx:34`, `ReplyTextField.tsx:58`); the visible question
  heading serves as the prompt, but the hard rule says "visible labels".
- Delete the stray untracked folder `frontend/src/components/shared/Shell/Header/UserMenu/node_modules/` (git-ignored,
  holds only a `.vite` cache).
- `tsconfig.app.json` has no explicit `"strict": true` (it is on via TypeScript 6.0.3's default, which was verified);
  `src/vite-env.d.ts` does not type `VITE_BASE_API_URL` (`api/endpoints.ts:1` reads it with `?? ''`), and
  `frontend/CLAUDE.md` does not document it (only the root `CLAUDE.md:14` does).

## P3: nice to have

- **Pending style work, for planning.**
  - *Migration pass* (about 1-2 hours): explicit `strict`, 3 `is/has` renames (`offersResend` in `authErrors.ts:11` and
    `useStepFlow.ts:25`, plus 7 more uses; `startsInCooldown` in `ResendButton.tsx:18`), 4 interface member reorders
    (`StageLink.tsx:6`, `DescentStage.tsx:7`, `ReplyTextField.tsx:7`, `PostListStatus.tsx:11`: required and optional
    members are still interleaved), `GlowButton.tsx:15` template-string className, 2 inline test types
    (`PostList.test.tsx:13`, `useOpenUsername.test.tsx:11`). Already done: semicolons, `ENDPOINTS`/`ROUTES`, rem units,
    named interfaces, `else/catch` layout, chains.
  - *Root-scaling refactor* (needs a visual check, L): `--home-scale`, `--auth-scale`, the root font bands and the
    breakpoint comment are already gone (`index.css:73` is just `html { font-size: 100% }`). Still to do: 8 `clamp()`
    uses (6 font sizes at `ComposeModal.css:29,46`, `Horizon.css:59`, `Arrival.css:11`, `StepFlow.css:8`,
    `StepInput.css:15`, plus `index.css:49` and `DescentStage.css:12`), 3 media-query font sizes (`Ruler.css:39`,
    `TabRow.css:67`, `PostCell.css:74`), and the 46 small fonts.
  - Class prefix differs from the component in 3 places (`tweet-details-back`, `tab-panel`, `person-list`).
- **Frontend smaller items.**
  - HTTP status constants are repeated: 12 in 9 files (404 in `useTweetDetails.ts:7`, `ReplyCell.tsx:14`,
    `ReplyComposer.tsx:11`, `useProfile.ts:8`; 400 in `profileEdit.ts:12`, `authErrors.ts:28`, `useConfirmation.ts:13`;
    401 in `http.ts:9`, `authErrors.ts:30`; 0 in `http.ts:7`, `useStepFlow.ts:41`; 503 in `ReplyComposer.tsx:13`), plus
    403 and 409 in `authErrors.ts:32,34` and the 400/500 boundaries in `composeChecks.ts:17,19`. Move them to
    `api/http.ts`.
  - `useTweetDetails` and `useProfile` repeat the load/404/retry pattern, `STATUS_OF_PROBLEM` exists twice
    (`TweetDetailsPage.tsx:25`, `ProfilePage.tsx:10`), and the list footer (sentinel, status, live region) is
    triplicated in `PostList.tsx:78`, `PeopleList.tsx:34`, `ReplyThread.tsx:57`.
  - Unmemoized context values in `AuthContext` and `PostUpdatesContext` (`:34`, no `useMemo`/`useCallback` in
    `contexts/`); `PostList.tsx:72` creates a new object per cell each render, so all cells re-render on the minute
    tick, with no virtualization.
  - Hidden mounted panels keep 60 s timers and the feed poll (documented for the poll).
  - `bidi.ts:3` holds raw invisible characters in its regex (use `\u` escapes), `tweetPath` (`routes.ts:14-16`) does not
    encode its id (unlike `profilePath`), `DEFAULT_TAB` (`utils/tabs.ts:26`) is used only by its own test,
    `ProtectedRoute` renders blank while the session loads, the `/confirm` token stays in the URL, `ReplyCell.tsx` is
    188 lines and not in the accepted list, `PostList.test.tsx` is 1455 lines, there is no skip link, and unknown URLs
    silently redirect (deliberate and tested).
  - A single 333,016-byte chunk is acceptable; route-level `React.lazy` is optional.
- **Toolchain.**
  - Add scripts: `typecheck` (`tsc -b`), `check` (lint, typecheck, test), `test:watch`. `frontend/package.json` has only
    `dev`, `build`, `lint`, `test` and `preview`, and the e2e `package.json` has no typecheck, so Playwright (which
    does not typecheck) hides type errors in specs.
  - TypeScript is `~6.0.2` in the frontend (6.0.3 installed) but `^7.0.2` in e2e (7.0.2), and e2e `@types/node` is
    `^26.6.3` against Node 24.13 (the frontend has `^24.13.3`).
  - `npm outdated` (frontend, run today) shows only patch bumps (vite 8.3.1 → 8.3.4, plugin-react 6.1.1 → 6.1.2,
    oxlint 1.86.0 → 1.87.0, `@types/node` 24.19.0 → 24.19.2) plus TypeScript 6.0.3 → 7.0.2. `npm audit` in `e2e/` is
    clean.
  - `.oxlintrc.json` runs two rules only (`react/rules-of-hooks`, `react/only-export-components`). Adding
    `jsx-a11y` gives 12 mostly intentional warnings; the `vitest` plugin gives 187 (mostly mock type-parameter nits).
    Re-run with oxlint 1.86.0.
- **Duplication across the Java services** (a shared module, if you ever want one): the body-size filter, wrapper,
  `ErrorResponseWriter` and `PageSizeValidator` exist in the gateway (`configurations/security/`, `utilities/web/`),
  tweet (`configurations/web/`) and timeline (`RequestBodySizeLimitFilter` in `configurations/`, the rest in
  `utilities/web/`); `ImageSignatureValidator`, `OutboxPublisherServiceImpl`, `MinioBucketInitializer` and
  `ObjectStorageServiceImpl` exist in gateway and tweet; `CurrentUserIdArgumentResolver`, `DownstreamFailures`,
  `DownstreamLookupSupport` and `HttpClientConfiguration` exist in both tweet and timeline (the timeline's in
  `utilities/web`, `utilities/downstream` and `configurations/downstream`); `KafkaErrorHandlingConfiguration` and
  `KafkaConsumerConfiguration` are near-copies in mail and timeline (the DLT `KafkaTemplate`s are never closed in
  either: mail `:108`, timeline `:144`). `Instant.now(clock).truncatedTo(MILLIS)` appears six times, all in the tweet
  service (`TweetServiceImpl:151,294,367`, `OutboxServiceImpl:38`, `ReplyServiceImpl:106,147`); the timeline and gateway
  use `MICROS` variants (`LikeServiceImpl:54`, `SavedTweetServiceImpl:51`, `FollowServiceImpl:64`). The first version
  said "six times in three services". Under your "build only what is used" rule this is a decision, not an automatic
  task.
- **Other.**
  - Timeline: DLT replication factor hardcoded to 1 (`KafkaTopicConfiguration.java:17`; mail does the same at `:16`);
    there is no dead-letter replay tool (a gateway outage longer than 4 minutes dead-letters those fan-outs for good;
    not checked in depth); `ix_feed_entries_user_author` (`FeedEntry.java:40`) may be redundant with the primary-key
    `user_id` prefix (measure before dropping); every fan-out row writes 4 indexes.
  - Tweet: `countByAuthorId` (`TweetRepository.java:11`, used at `TweetServiceImpl.java:233`) is O(the author's tweets);
    the outbox sends and deletes one message at a time.
  - Mail: no `spring.data.redis.timeout` (only host, port and password, `:39-42`); no `Auto-Submitted` header or From
    display name (`MailDeliveryServiceImpl.java:47` uses `setFrom(fromAddress)` only); Gmail is capped at about 500
    recipients a day and has no SPF/DKIM for your domain, so use a transactional provider (no code change: host, port,
    user, password).
  - There is no account deletion or password reset (out of scope per PLAN), but real emails will be stored.
  - The e2e setup is documented only in the root README in two lines (`:107-108`).

- The mail service has the shape the timeline service had: two listeners (`user.confirmation-requested`, `user.followed`)
  in one consumer group, `twitter-mail-service`, so a rebalance in one reaches the other. Give each its own group, as the
  timeline service's listeners have (`groupId = "${spring.kafka.consumer.group-id}-<topic>"`), if rebalances there ever
  become a problem.

## Decisions needed before planning

- **Deployment target.** The prod overlay, the secrets handling and the proxy assume Docker Compose on one host. Is that
  right, or Kubernetes / a PaaS? Startup order (compose `depends_on` is currently what keeps topic creation and the
  seeder from racing) depends on it too. The root `PLAN.md` already plans nginx in front of two gateway instances;
  before that, the gateway needs `SKIP LOCKED` in the outbox poller and a lock (ShedLock) on
  `UnconfirmedUserCleanupJob`: both are recorded as accepted gaps with a "second instance" trigger in
  `twitter_api_gateway/PLAN.md`, so two instances are unsafe today.
- **The demo users.** The seeder stays on in production (decided 2026-10-10), but two things are open. All 12 accounts
  share one password, so anyone who knows it can log in as each of them: give the real people (Gabi, Kristian, Gosho,
  Stiliyan) their own passwords, or drop their accounts from the seed. Their follows (53 with the fixed random seed)
  publish `user.followed` events, so over real SMTP the mail service tries up to 53 emails to `@seed.local` addresses on
  the first production start: suppress them in the seeder, or skip `@seed.local` in the mail service.
- **The seed photos.** They are local only and git-ignored (decided 2026-10-10), so they have to be copied onto the
  production host (compose mounts `twitter_api_gateway/seed-images/` read-only) or the seeded users keep the default
  avatar. `stiliyan.jpg` (7.4 MB) and `gosho.jpg` (5.5 MB) are over the 5,242,880 byte upload cap and are skipped with a
  WARN on every start, and six users have no file at all (hristo, bobkata, atanas, stanimir, bogdan, kaloyan). The photos
  are full camera originals with their EXIF (the camera model is in `gosho.jpg`), they show real people by name, and on
  a public deployment they are served to everyone: make sure those people agree.
- **Plain http between containers.** The gateway's internal secret travels over `http://gateway:8080` inside the Docker
  network, with no TLS. Accepted 2026-10-10: one host and nothing else on that network. Revisit it with the deployment
  target.
- **Error boundary.** Allow one class component (or a library) as the exception to "functional only"?
- **Branding.** The classic Twitter bird and the name "Twitter" on a public deployment.
- **Documents in a public repo.** This file, `SECURITY-FINDINGS.md` and the exploit report list weaknesses, and the
  repo is public. The first version of this file was committed and pushed in `70fea9d`, so it is already in the public
  history. Decide what stays in git.

## What is in good shape (do not touch)

Carried over from the first pass; this update did not re-check these claims except where a number is marked as
re-counted.

- **Cross-service consistency.** Spring Boot 4.1.1 and Java 25 are identical in all four poms and match the
  `eclipse-temurin:25` Dockerfiles; Maven wrapper 3.9.16 is identical everywhere. Event DTO field names, types and keys
  match `EVENTS.md` and the consumers. Internal HTTP paths, DTOs and headers (`/internal/v1`, `X-Internal-Secret`,
  `X-User-Id`) agree between callers and callees.
- **Tests.** Deterministic: no `Thread.sleep`, `@Disabled`, `.only`, `.skip`, random ids or `waitFor` in the unit and
  integration suites (mutable clock, `TestIds`, fixed seeds), no assertion-free tests. `TESTING.md` matches the test
  names in tweet, timeline and mail, apart from the stale lines listed in P2. e2e has no `waitForTimeout` or
  `networkidle`, 79 specs (re-counted: auth 10, backfill 2, feed-ui 15, feed 3, follow-email 1, likes 5, people-ui 8,
  people 2, profile-ui 11, profile 9, replies-ui 4, replies 5, saved-tweets 3, views 1; no `.skip`, `.fixme` or
  `.only`), parallel-safe (UUID users), selectors mostly by role/label, isolated project name with `down -v` teardown.
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
  tracked `.DS_Store`, `.idea` or `target`; all text files are LF; no `dist/` or e2e artifacts tracked. The new
  per-service `.env.example` files hold blank values only.
- **Containers.** All four images run as non-root with multi-stage builds, cached dependency layers and wget
  healthchecks. Compose has restart policies, resource limits, healthchecks and loopback-only ports; the PG18 volume
  path is correct; image tags are pinned.

## Branch and working tree

- `seed-demo-data` is 2 commits ahead of `main` (`0e301de`, the seeder, and `70fea9d`, the package and docs
  reorganisation), both pushed to `origin/seed-demo-data`, not merged.
- Uncommitted (2026-10-10): all the work of this review, across the four services, the frontend, the compose files, the
  `.env.example` files and the docs. New files: `docker-compose.prod.yml`, `frontend/Dockerfile`,
  `frontend/.dockerignore`, `frontend/nginx/`, `Durations` in the timeline service, `BaseUrls` and
  `SmtpCredentialsValidator` in the mail service, `OutboxHealthIndicator` in the gateway and the tweet service, and the
  tests for them. Nothing was committed or pushed.
- Untracked and git-ignored: the 6 seed photos in `twitter_api_gateway/seed-images/`.

## How much of this was verified

- **Run directly (this pass):** the four Maven suites (`./mvnw -B verify`), the frontend `tsc`, lint, unit tests (twice),
  build, `npm audit` (with and without dev) and `npm outdated`, `npm audit` in `e2e/`, `gh repo view` (the repo is
  public), and Docker and port availability.
- **Read from code by the reviewers** (not run): everything else, with `path:line` evidence as given above, at
  `70fea9d`. Nothing was tried against a running stack, and the e2e suite was not run.
- **Carried over from the first pass, not re-run:** the EXIF findings and the "What is in good shape" section.
- **Unverified claims:** the Kafka volume path (checked against upstream trunk, not the 4.3.1 image), the multipart
  500 in the tweet service, the e2e setup timeout, the unused pom dependencies (confirm with `mvn dependency:analyze`),
  the exact startup behaviour when optional `.env.example` keys are copied as empty values.

## Suggested order

1. **Repo hygiene:** the root `.env.example` rewrite and the dangling doc references.
2. **Reliability:** the health/management settings.
3. **Hardening:** Mongo/Kafka/MinIO/Postgres accounts, EXIF stripping, avatar resizing, CVE scan, Flyway baseline.
4. **Frontend robustness:** request timeout/abort (and 429 if a proxy ever sends one), error boundary decision,
   per-route titles and focus, avatar `loading`/`decoding`, `index.html` meta, CSP, self-hosted fonts, favicon, the
   `PostActions` rollback gap.
5. **Operations:** CI/CD, backups, structured logs and metrics, production smoke test in e2e.
6. **Cleanup and pending style work:** the remaining docs pile, dead code and pom dependencies, test isolation, the
   migration pass, then the root-scaling refactor with its visual check.
