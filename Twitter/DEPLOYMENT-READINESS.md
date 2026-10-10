# Deployment readiness: what is left

Updated 2026-10-10. Everything fixed or decided in the review is gone from this file; it lives in the code, the compose
files, the README and each service's `DECISIONS.md`. Branch: `seed-demo-data`. The `path:line` references below are for
commit `70fea9d` unless marked, so check them before relying on a line number. Effort: **S** (under half a day), **M**
(about a day or two), **L** (more than that).

## Before the first deployment

These come from this session's changes; none has been run against a live stack.

- [ ] **Cold-start the full stack with two gateways (S).** `docker compose up -d --build` (and the prod overlay): check
  that both `gateway` and `gateway-2` become healthy, exactly one of them seeds, nginx serves requests through both, and
  the other services reach `gateway:8080`. Only the compose rendering and the gateway unit/integration tests were run.
- [ ] **Run the e2e suite (S).** It was not run after any change today (79 specs; builds four images).
- [ ] **Add the four `SEED_PASSWORD_<NAME>` values to `.env`** (`STILIYAN`, `KRISTIAN`, `GOSHO`, `GABI`), different from each
  other, 12-72 bytes with a lowercase letter, an uppercase letter and a digit. The gateway refuses to start without them
  while seeding is on.
- [ ] **Investigate the flaky `MinioConfigurationTest` (S).** `should_give_up_on_a_minio_that_accepts_the_connection_but_never_answers`
  failed in about two of three isolated runs (`SocketException: Socket closed` instead of `SocketTimeoutException`) and
  passed in full runs. The gateway rule treats a flaky run as a failure to investigate.
- [ ] **Copy the seed photos to the production host** (`twitter_api_gateway/seed-images/`, yours to replace). Files over
  5,242,880 bytes are skipped with a WARN.
- [ ] **Commit and merge.** Almost everything in this branch is uncommitted; `main` is two commits behind this branch.

## Accepted gaps to revisit

- DLT replication factor is hardcoded to 1 (mail `KafkaTopicConfiguration.java:16`, timeline `:17`): make it a property
  when a second Kafka broker arrives.
- The mail service does not filter `@seed.local`: a later real follow of a demo user would email that address.
- No Kafka listener health indicator; the compose healthchecks hit `/actuator/health`, so a `DEGRADED` outbox does not
  fail them.
- Plain http between containers (one host, accepted); the seed photos and demo passwords are yours to manage.
- Git history still holds the old audit documents (including `70fea9d`), and each service's `PLAN.md` / `DECISIONS.md`
  still lists accepted gaps, several now pointing at stubs in the audit files (for example gateway `PLAN.md:82-85`,
  mail `PLAN.md:108-123`, timeline `PLAN.md:58`, `DECISIONS.md:122-125`, tweet `PLAN.md:70`, frontend `PLAN.md:81,344,353,356`).
  Clean those references up or accept them; a history rewrite needs a force-push.
- Open and accepted security findings now live in the git-ignored `security-private/`.

## P2: cleanup

### Docs

- Gateway `DECISIONS.md` still has 47 "step N" references although `PLAN.md` was trimmed to a phase table (some of them are frontend-plan steps).
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
- Root `PLAN.md` is slightly stale after the trim: `:9-10` still says "gateway phase 4 may start", `:25` cites the
  `user.followed` change (`85019be`) as news, and `:27` says the timeline has "three phases" while describing phases 4-5.
- Root `README.md` does not describe the seeder's 12 `@seed.local` users or the e2e ports and prerequisites (it says
  only `npm test` in `e2e/`, and there is no `e2e/README.md`). `seed-images/README.md` now documents the seeder but still ends with a dated to-do list
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
    `twitter_api_gateway/.../auth/TokenServiceImpl.java:36`.
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
  at original size (accepted); two profile findings are still open, kept in `security-private/` (`UpdateProfileRequestDTO.java:20-27`
  has `@Past` only, no birthdate lower bound, and `MaxCodePointsValidator` does not reject NUL; `ProfileMapper.java:24`
  still returns the birthdate to every logged-in user).
- Style, grouped (chain and field-order counts are old and not recounted): about 26 one-line multi-call
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

- **The committed `PostUpdatesContext` work.** It is finished and coherent. A failed like or save now re-reports the old
  value (fixed 2026-10-10, with tests in `PostActions.test.tsx`). Two small gaps remain:
  - Test coverage is one overlay test in `PostList.test.tsx:1419-1454` (like state, like count and reply count after
    `reportUpdate`; not saves, not the toggles after the re-key). `PostCell.test.tsx` has no re-key test, and `TweetDetailsPage.test.tsx` only wraps the page in the provider
    (`:117-122`) with no reporting assertion.
  - Minor: `oxlint-disable` at `PostUpdatesContext.tsx:40` has no explanatory comment, unlike `AuthContext.tsx:122-123`.
- People card goes stale after following on a profile: `usePersonFollow` state is local to `PersonCard` (`:28`) and
  `ProfileView` (`:28`), the People panel stays mounted, so Back shows FOLLOW and the old count. `PostUpdatesContext`
  carries only `likedByMe`, `likes`, `savedByMe` and `replyCount`, and `followChangeCount` (`Shell.tsx:26,55`) only
  reloads an empty feed. Not recorded as a shortcut (`frontend/CLAUDE.md:111` records only the PostUpdates one); if you
  accept it, record it.
- 46 of 88 `font-size` declarations (80 plain rem values, 6 `clamp()`, `100%` and `0.34em`) across 37 CSS files are
  below 0.75rem, in 27 files, down to 0.46875rem (`Ruler.css:25,39`); another 14 are exactly 0.75rem. Many are the
  9.5 px mono labels.
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
  use `MICROS` variants (`LikeServiceImpl:54`, `SavedTweetServiceImpl:51`, `FollowServiceImpl:64`). Under your "build only what is used" rule this is a decision, not an automatic
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
