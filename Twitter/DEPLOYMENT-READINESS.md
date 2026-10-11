# Deployment readiness: what is left

Updated 2026-10-11 (`ae79f96`, branch `main`). Only open items remain.

## Before the first deployment

- [ ] **Copy the seed photos to the production host** (`twitter_api_gateway/seed-images/`, yours to
  replace). Files over 5,242,880 bytes are skipped with a WARN: `stiliyan.jpg` and `gosho.jpg`
  currently are.

## Accepted gaps (revisit when relevant)

- DLT replication factor is hardcoded to 1 (mail `KafkaTopicConfiguration.java:16`, timeline `:17`):
  make it a property when a second Kafka broker arrives.
- The mail service does not filter `@seed.local`: a later real follow of a demo user would email
  that address.
- No Kafka listener health indicator; the compose healthchecks hit `/actuator/health`, so a
  `DEGRADED` outbox does not fail them.
- Plain http between containers (one host, accepted); the seed photos and demo passwords are yours
  to manage.
- Git history still holds the old audit documents (including `70fea9d`), and each service's
  `PLAN.md` / `DECISIONS.md` still lists accepted gaps, several now pointing at stubs in the audit
  files (for example gateway `PLAN.md:82-85`, mail `PLAN.md:108-123`, timeline `PLAN.md:58`,
  `DECISIONS.md:122-125`, tweet `PLAN.md:70`, frontend `PLAN.md:81,344,353,356`). Clean those
  references up or accept them; a history rewrite needs a force-push.
- Open and accepted security findings now live in the git-ignored `security-private/`.

## P2: cleanup

### Docs

- Gateway `DECISIONS.md` still has 47 "step N" references although `PLAN.md` was trimmed to a
  phase table (some of them are frontend-plan steps).
- Timeline docs: `CLAUDE.md:7-9` omits `/api/v1/author-tweets/*` (route at
  `AuthorTweetsController.java:19`); `PLAN.md:43` only says "tweets by ids, tweets by author" and
  the `/page` read is named only in code (`TweetLookupServiceImpl.java:36`); `DECISIONS.md`'s 43
  "step N" references point nowhere because the steps were trimmed (`PLAN.md:6`);
  `docs/TESTING.md:19` and `:100` are stale ("Written `@Disabled` in step 5…", "endpoint doesn't
  exist yet"), `:33` gives the old key set without likes, `likedByMe` or `replyCount`, and
  `:11-12,162,266,355,448` keep the "written `@Disabled` first" wording although no `@Disabled`
  remains in the timeline tests.
- Tweet docs: `PLAN.md:48` still says "Out of scope: tweets by user"; the route list
  (`PLAN.md:25-28`) and `CLAUDE.md:3-9` omit the by-author, page and count routes
  (`InternalTweetController.java:34,39,48`, `TweetController.java:61`), which are described only in
  `DECISIONS.md:80,181`; `src/test/resources/application-test.properties:1-4` repeats the main
  Kafka logging lines (`application.properties:102-104`).
- Mail docs: `CLAUDE.md:70` still names "the `@Disabled` test catalog of steps 2 and 10" (none
  remains in `src/test`); `CLAUDE.md:49` places validate/claim/send/mark in the notification
  service although they now live in `MailEventValidationService` and `MailDispatchService`
  (`PLAN.md:27-33` is right); `PLAN.md:22` says infra is in `../docker-compose.yml` (redis and
  mailpit are in `docker-compose.infra.yml:149,171`; `CLAUDE.md:16` is right); `CLAUDE.md:8-9`
  says "numbered steps with gates" while `PLAN.md:9-10` says the gates were trimmed.
- Frontend `PLAN.md`: `:349` says "Path segments are not encoded in ENDPOINTS", but
  `api/endpoints.ts:7` has `segment()`; the "Out of scope" list (`:378-381`) still names profile
  pages, follow UI, the tweet-details page, the edit-account page and containerisation, and only
  containerisation is unbuilt; a stray blank line at `:26` breaks the status table, and lines 3-4
  have an unbalanced `**`; `:79` says "78/78 green 3x" but there are 79 specs now.
- `frontend/PLAN.md` (32,184 bytes) and `DECISIONS.md` (34,884 bytes) record the same
  step-by-step results and `DECISIONS.md` holds superseded decisions (for example "POST button has
  no handler yet", `:50`). Mark them historical or trim.
- Root `README.md` does not describe the seeder's 12 `@seed.local` users or the e2e ports and
  prerequisites (it says only `npm test` in `e2e/`, and there is no `e2e/README.md`).
  `seed-images/README.md` still ends with a dated to-do list ("Still to add or fix (as of
  2026-10-08)").
- `.claude/skills/java-junit` is the only tracked file under `.claude`, while the other skills are
  global (`~/.claude/skills` has `java-code-style` and `frontend-code-style` but no `java-junit`).
  All four service `CLAUDE.md` files mandate `java-junit` (for example gateway `CLAUDE.md:55`), so
  a fresh clone lacks the skills the docs require.

### Docker and infra hygiene

- Base image tags float: the Dockerfiles use `eclipse-temurin:25-jdk` and `25-jre` (major only,
  not digest-pinned) and infra uses `postgres:18`, `mongo:8.2`, `redis:8.10.2`, `kafka:4.3.1`,
  `kafka-ui:v1.5.0` and `mailpit:v1.31.2`. The four Dockerfiles are identical apart from `EXPOSE`
  and the comment: use `COPY --chown` (there is a separate `RUN chown` at `:34`), BuildKit cache
  mounts for `~/.m2`, and digest-pinned bases.
- `alpine/minio:RELEASE.2025-10-15…` (`docker-compose.infra.yml:98`) is a third-party mirror about
  a year old; check its upstream support status.
- e2e: ports 5273, 8180, 8125 and 5452 are hardcoded in several files with no override
  (`playwright.config.ts:3-4`, compose `:158`, `fixtures.ts:47`).
- e2e: `global-setup.ts:10-18` runs `up -d --build --wait` with a 300 s timeout (`:17`), which
  includes four Maven image builds; healthchecks allow about 90 s per Spring service. Raise to
  about 900 s or build in a separate step. There is already an `E2E_NO_BUILD=1` skip (`test:fast`).
- e2e never touches a production build, an SPA fallback or `/api` through a real proxy (`webServer`
  runs `npm run dev`, `playwright.config.ts:19-24`). The frontend container exists now, so a smoke
  project against it can be added.
- e2e covers desktop Chromium only, with `reducedMotion: 'reduce'` globally and no mobile project;
  `auth.spec.ts` uses no semicolons while `fixtures.ts` (95 lines) and `follow-email.spec.ts` (13)
  do.
- No `LICENSE` (a public repo is all-rights-reserved by default) and no root `.gitattributes`
  (only the four services have one).
- No Node pin: no `engines`, `.nvmrc`, `.node-version` or `packageManager` in `frontend/` or
  `e2e/`, although Vite 8 needs Node 20.19 or later and you run 24.13.

### Code (backend)

- Confirm dead dependencies with `mvn dependency:analyze` and a full build across all services.
- Timeline: `UserLookupServiceImpl.java:44-46` throws an NPE (500) on an empty body, `null` or a
  null id; `FollowerLookupServiceImpl.java:36-40` throws an NPE on a null page or null `ids` (in a
  listener: about 4 minutes of retries, then dead-letter). `TweetLookupServiceImpl.requireWellFormed`
  (`:110`) was hardened in the 2026-10-07 audit but these two were not, and their test suites have
  no such case.
- Log-forging back-fill: the request path is still logged in the tweet service
  (`GlobalExceptionHandler.java:232`, `e.getResourcePath()`) and the gateway (`:221`); the
  timeline's was fixed. The one-minute `curl` check and the unseen `/liked` browser check were not
  repeated.
- Tweet: a garbled or truncated multipart body, or more than 10 parts, should hit the catch-all
  (500 + ERROR stack trace), because neither the advice nor `ResponseEntityExceptionHandler` handles
  Spring's `MultipartException`. Map it to 400 and add a test. No test covers
  `handleHttpMessageNotReadable` (`GlobalExceptionHandler.java:183`).
- Mail: `MailInboxServiceImpl:62-64` swallows a `markSent` failure, so the catch at
  `MailDispatchServiceImpl:49-51` is unreachable in production, and the unit test that mocks it
  (`UserConfirmationNotificationServiceImplTest.java:136`) throws something that cannot happen.
  Keep one catch and test it with a real Redis failure.
- Mail: follow email links to `/feed` (`FollowEmailRenderer.java:15`) and reads "ana (@ana)"
  (`followEmail.txt`, `followEmail.html:46`). Link to `APP_BASE_URL/users/<follower>` and fix the
  display. The e2e spec asserts the current `/feed` link, so change both together.
- Seeder robustness: `TweetSeedServiceImpl.java:70-89` has no connect retry; compose starts
  `tweet-service` independently of the gateway, so a cold `up` can leave users without tweets.
  Reuse the like retry (`LikeSeedServiceImpl.java:28`). No tests for Tweet/Reply/Like seed
  services, the `DataSeedRunner` gate, or `PathMultipartFile`.
- Timeline test gaps: nobody asserts `ix_feed_entries_tweet` or `ix_feed_entries_tweet_created`
  (`FeedEntry.java:39,41`), and `AbstractListenerIntegrationTest.java:332,365` scan whole tables
  with `findAll()` on every Awaitility poll.
- Tweet test isolation: `MongoCollectionInitializerIntegrationTest.java:62-63` drops the shared
  `tweets` and `outbox` collections without restoring them; `OutboxPublisherServiceKafkaIntegrationTest`
  reads only the oldest 500 `PENDING` rows of a shared outbox no test clears; the explain tests
  (`TweetRepositoryIntegrationTest.java:~380-435`) hand-build their own filters.
- Mail test isolation: `TwitterMailServiceApplicationTests`, `MailTlsConfigurationIntegrationTest`
  and `MailInboxServiceIntegrationTest` boot full contexts with listeners on the same topics and
  group as the shared e2e context — set `spring.kafka.listener.auto-startup=false` in those three.
  `MailDispatchServiceImpl` and `MailEventValidationServiceImpl` have no dedicated test class.
  `MailInboxServiceIntegrationTest.java:46` asserts TTLs against Redis wall-clock windows (290-300 s).
- Gateway: `email_confirmation_tokens.user_id` has no index; no reserved usernames
  (`RegisterRequestDTO.java:28` is only a regex); two profile findings are still open in
  `security-private/` (`UpdateProfileRequestDTO.java:20-27` has `@Past` only and
  `MaxCodePointsValidator` does not reject NUL; `ProfileMapper.java:24` still returns the birthdate
  to every logged-in user).
- Style: ~26 one-line multi-call chains in gateway main, ~230 in its tests, ~30 in tweet, ~15 in
  timeline, ~10 in mail; oversized packages in gateway, tweet, timeline and mail test `support`;
  8 field-order slips in timeline; mail import order in `ConfirmationEmailRenderer.java:3-4` /
  `FollowEmailRenderer.java:3-6`; `LINE_BREAKS` duplicated in mail; mail test `@Nested` names
  `Staleness` and `Leakage` are not method names; `DEFAULT_PAGE_SIZE` duplicated in
  `InternalTweetController.java:30` and `ReplyController.java:31`; one TODO comment in timeline
  `jobs/FeedRetentionJob.java:14`.

### Code (frontend)

- `PostUpdatesContext` test gaps: saves and re-key toggles are not covered; `PostCell.test.tsx` has
  no re-key test; `TweetDetailsPage.test.tsx` wraps the page in the provider but makes no reporting
  assertion. `oxlint-disable` at `PostUpdatesContext.tsx:40` has no explanatory comment.
- People card goes stale after following on a profile: `usePersonFollow` state is local to
  `PersonCard` and `ProfileView`; the People panel stays mounted, so Back shows FOLLOW and the old
  count. If accepted, record it as a shortcut in `frontend/CLAUDE.md:111`.
- 46 of 88 `font-size` declarations across 37 CSS files are below 0.75rem, down to 0.46875rem
  (`Ruler.css:25,39`).
- Visible-label rule: inputs use `sr-only` labels (`StepInput.tsx:34`, `ReplyTextField.tsx:58`).
- Delete the stray untracked folder
  `frontend/src/components/shared/Shell/Header/UserMenu/node_modules/`.
- `src/vite-env.d.ts` does not type `VITE_BASE_API_URL`; `frontend/CLAUDE.md` does not document it.

## P3: nice to have

- **Frontend style pass.**
  - *Migration pass* (~1-2 h): explicit `strict`, 3 `is/has` renames (`offersResend`,
    `startsInCooldown`), 4 interface member reorders, `GlowButton.tsx:15` template-string className,
    2 inline test types.
  - *Root-scaling refactor* (L, needs visual check): 8 `clamp()` font sizes, 3 media-query font
    sizes, and the 46 sub-0.75rem fonts.
  - Class prefix differs from the component in 3 places (`tweet-details-back`, `tab-panel`,
    `person-list`).
- **Frontend smaller items:** HTTP status constants duplicated across 9 files; repeated
  load/404/retry pattern in `useTweetDetails` / `useProfile`; triplicated list footer; unmemoized
  context values; hidden panels keeping timers alive; raw invisible characters in `bidi.ts:3`;
  `tweetPath` does not encode its id; `DEFAULT_TAB` used only by its own test; `ProtectedRoute`
  renders blank while the session loads; no skip link.
- **Toolchain:** Add `typecheck`, `check` and `test:watch` scripts; align TypeScript versions
  across frontend and e2e; update patch-level npm deps; expand `.oxlintrc.json` rules.
- **Java service duplication** (shared module, only if wanted): `ErrorResponseWriter`,
  `PageSizeValidator`, body-size filter, `OutboxPublisherServiceImpl`, `MinioBucketInitializer`,
  `ObjectStorageServiceImpl`, `KafkaErrorHandlingConfiguration`, `KafkaConsumerConfiguration` are
  copied across services. The DLT `KafkaTemplate`s are never closed (mail `:108`, timeline `:144`).
- **Other:**
  - Timeline: no dead-letter replay tool; `ix_feed_entries_user_author` may be redundant; every
    fan-out row writes 4 indexes.
  - Tweet: `countByAuthorId` is O(author's tweets); outbox sends and deletes one message at a time.
  - Mail: no `spring.data.redis.timeout`; no `Auto-Submitted` header or From display name; use a
    transactional SMTP provider instead of Gmail.
  - No account deletion or password reset (out of scope), but real emails will be stored.
  - The e2e setup is documented only in the root README in two lines.
  - Mail: two listeners share one consumer group — give each its own group (as the timeline service
    does) if rebalances there ever become a problem.
