# SignalFlow — cross-service improvements

Improvements that touch more than one service, picked from the first one to two weeks of running the
system. Each service still keeps its own `PLAN.md` for service-local work.

**How to work through a phase here.** Do the steps in order. Inside a step, tick each task `- [x]` as it
lands. A step is done only when its **Gate** passes: tick the gate and append ` ✅ **Done** (YYYY-MM-DD)`
to the step heading in the same change. **No step starts on a red or missing test, and each step ends
green.** Before any `.java` edit load `java-code-style` (and `java-junit` for tests); before any `.ts`/`.tsx`/
`.css` edit load `frontend-code-style`. Each service's own `CLAUDE.md` rules still apply.

## Status

| Phase | Scope | Done | Audit |
| --- | --- | --- | --- |
| 1 | An interest topic belongs to 1–3 categories (steps 1–9) | 2026-10-14 | — |
| 2 | Tell the model which stories were already covered (steps 1–4) | 2026-10-14 | — |
| 3 | An admin can delete a user account (steps 1–4) | 2026-10-14 | — |
| 4 | Each topic has a news schedule (steps 1–10) | 2026-10-14 | — |

## Left open

- **Phase 1, step 7: the frontend gate's manual check was waived.** Three 40-character chips at 375 px and in both
  themes were not checked. The 2026-10-14 smoke check looked only at desktop width, dark theme.
- **Phase 4, step 8: the manual card check ran against a mock gateway, not the full stack.** It covered desktop and a
  375 px viewport, dark theme only (the app has no light theme). It also covers the 375 px part of the Phase 1 item above.
- **Mail service: consumers started before their topic exists pick it up only after the Kafka metadata refresh (about
  5 minutes).** Seen in the Phase 4 smoke check (the email arrived late); not caused by Phase 4.

---

## Phase 1 — An interest topic belongs to 1–3 categories ✅ **Done** (2026-10-14)

### Why

`InterestTopic → Category` is `@ManyToOne` today, but real topics span several categories (e.g. "AI
regulation" is both Technology and Politics). A topic gets **1 to 3** categories, all equal (no primary).
**Every** category is shown on the feed's topic card and in the news email. Category stays a label: there
is no category filter in the feed (see Accepted gaps).

### What was built

- **Model.** `InterestTopic.categories` is a `@ManyToMany` `Set<Category>` (join table `interest_topic_categories`,
  `@BatchSize(100)`), capped at `MAX_CATEGORIES = 3`; `Category.name` max length 100 → 40. No migration (Q6).
- **HTTP.** Create/update take `categoryIds` (1–3, `[]` and duplicates → 400, unknown id → 404, update replaces the
  set); responses carry `categories` (`id`, `name`) sorted by name. `GET ?categoryId=` matches any of a topic's
  categories. Deleting a category any topic still has → 409.
- **Reads.** No `JOIN FETCH` of `categories` in a paged query (HHH90003004); `@BatchSize` loads a page's categories in
  one extra query. The outbox publisher reads detached and gets names from `findCategoryNamesByTopicIds`, once per batch.
- **Events.** `topic-news.generated` and `topic-news.notification-requested` carry `List<String> categoryNames`
  (sorted, 1–3) in all three services.
- **Gateway.** `notification_outbox.category_names` is `text[]`, bound with `setArray`; the guard skips a record
  whose list is null, empty or has a name over 255 characters. Feed and route DTOs carry `categories`.
- **Mail.** Token `CATEGORY_NAMES`: HTML escapes each name and joins with ` &middot; `; text strips line breaks and joins
  with ` · `. Validation `@NotEmpty @Size(max = 3)`, `@NotBlank @Size(max = 100)` per name.
- **Frontend.** `FeedTopic.categories`; `TopicCard` renders one chip per category in a wrapping flex row, each chip
  ellipsized with a `title` of the full name.
- **e2e.** Fixtures use `categoryIds` and 40-character-safe category names; `feed.spec.ts` checks a two-category card.
- **Audit.** A smoke check on a fresh full stack (2026-10-14) showed 3 chips on the card and `alpha · beta · gamma` in the
  email header, preheader and text body. No exploit-hunter audit was run for this phase.

### Test strategy

- **Deterministic only.** No `Thread.sleep`; use Awaitility for Kafka/async. Generate unique names per
  test, e.g. with a random suffix, so the shared Testcontainers DB can't collide. Assert an async negative
  ("was skipped") with a sentinel record sent after it, as the existing listener tests do.
- **Sorted output is part of the contract.** Assert exact list order (by name) on responses and events,
  not just contents.
- **Pagination correctness.** Every paged or keyset query gets a test with several topics × several
  categories, asserting each topic appears once and the page/limit size is respected. This catches row
  duplication or in-memory paging from a stray collection join.
- Each `*ControllerIntegrationTest` scenario added or changed updates that service's `TESTING.md` in the
  same change (each service's `CLAUDE.md` rule).
- Each service gate: `mvn verify` green **three runs in a row** (flake check).

### Accepted gaps

- **Shortcut: no mixed-version events.** The event field changes from `categoryName` to `categoryNames`
  in one go, so all three services must be deployed together. A stale in-flight event is skipped by the
  gateway guard or rejected by the mail validation; it is not delivered. Acceptable because the databases
  are wiped on restart anyway. **Trigger:** keeping data across deploys, or deploying services
  independently, means adding the new field alongside the old one and removing the old one later.
- **No category filter in the feed.** Decided 2026-10-13: a good idea, but extra effort for now.
  **Trigger:** users ask to browse by category.
- **The admin list filters by one `categoryId`, not by several.** **Trigger:** an admin screen that needs
  "any of" or "all of" filtering.
- **Seed topics stay single-category.** **Trigger:** demo data needs to show multi-category topics.
- **The category order is alphabetical; there's no admin-chosen order or primary category.** **Trigger:**
  a need to emphasize one category.

### Decision record (2026-10-13)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Should a topic belong to many categories? | Yes, 1 to 3. |
| 2 | Is a category filter in the feed planned? | No: a good idea, but extra effort. Category stays a label. |
| 3 | Should one category be primary? | No. All are equal, and all are shown on the card and in the email. |
| 4 | Must the display scale? | Yes: chips that wrap with an ellipsis; a joined header in the email; a list (not a joined string) in the outbox. |
| 5 | Should category names be capped lower? | Yes, 100 → 40. |
| 6 | Is `ddl-auto=create` (wipe on restart) intentional? | Yes. That's why no migration is needed. |

---

## Phase 2 — Tell the model which stories were already covered ✅ **Done** (2026-10-14)

### Why

A big story keeps coming back, reworded, for several days in a row on the same topic. The reader has
already seen it. Each day's prompt now lists the headlines this topic received in the last 7 days and
asks the model to **prefer other stories**. This is a **suggestion, not a rule**. The model may still
pick a covered story when there is a meaningful new development, and it then says what's new. On a
quiet day it may write a short "nothing new" note instead. That note is ordinary HTML, saved, emailed
and shown like any other day.

Only the topic service changes; the event, gateway, mail service and frontend are untouched. This phase
is independent of Phase 1 and can be done before or after it.

### What was built

- **Prompt.** `SYSTEM_PROMPT_TEMPLATE` requires one `<ul>` with items `<li><strong>Headline</strong> detail <a href>Source</a></li>`
  and allows a short "nothing new" `<p>` note instead of the list. At most 5 items.
- **Headlines.** `utilities/NewsHeadlines.extract` reads the `<strong>` text of each `<li>` (case-insensitive, attributes
  allowed, nested tags stripped, entities unescaped, whitespace collapsed, blanks dropped).
- **Query.** `TopicNewsRepository.findDataByTopicInDateRange(topicId, startDate, endDate)`: rows in
  `[newsDate - N, newsDate)`, newest first, any status.
- **Setting.** `app.news.covered-lookback-days=${NEWS_COVERED_LOOKBACK_DAYS:7}`; `0` leaves the hint out.
- **Job.** `TopicNewsGenerationJob` extracts, de-duplicates (first-seen order) and logs the headline count per topic,
  then passes the list to `NewsGenerationService.generate(topic, newsDate, coveredHeadlines)`.
- **Hint block.** Appended to the system message only when the list is non-empty: the "already covered" list plus a
  "prefer other stories, say what's new" sentence.
- **Tests.** `NewsHeadlinesTest`, repository window/status/topic tests, prompt tests (block, empty list, special
  characters), job unit and integration tests (the single recorded request holds only the right topic's recent headlines).
- **Manual check (2026-10-14).** On a live stack with a hand-inserted row, the log showed a non-zero count and the new
  row kept the item shape. Docs in the topic service's `DECISIONS.md` and `CLAUDE.md`.

### Test strategy

- **Deterministic only.** These tests don't check what the model does with the hint; that can't be
  asserted. They check the **request we send**: the WireMock stand-in for OpenRouter records the body,
  and the tests assert on it. Dates are fixed `LocalDate`s, never "today" relative to the clock, except
  where the job computes it itself.
- The job integration test makes the **only** generated request belong to the topic under test: every
  other topic in that test already has a row for today, so the job skips it. That way an assertion
  on "the request body" can't read another topic's prompt.
- Each step's gate: `signal_flow_interest_topic_service` `mvn verify` green **three runs in a row**.

### Accepted gaps

- **Shortcut: no code-side de-duplication.** Repeats are only discouraged in the prompt. A reworded
  repeat can still get through, and so can an identical one. Decided 2026-10-13: we rely on the model.
  **Trigger:** readers keep reporting repeats after this ships, which means comparing items in code (for
  example by source URL or headline similarity) and dropping matches.
- **Headlines are re-parsed from HTML on every run, not stored.** If the model ignores the required
  shape, extraction finds nothing and that day's hint is empty. Nothing fails; the count in the log
  line shows it. **Trigger:** the logged count is often 0 for topics with history, which means asking
  the model for structured (JSON) items and storing the headlines alongside the HTML.
- **No cap on the size of the covered list.** At most about 35 headlines (7 days × 5 items), so it's
  small. **Trigger:** a larger lookback or item limit makes the prompt noticeably longer or more
  expensive.
- **"Nothing new" days still send an email.** **Trigger:** readers find those emails noisy, which means
  recognising the note and not publishing that day.
- **Headlines only, no source links.** Decided 2026-10-13: the headline is enough. **Trigger:** vague
  headlines (for example "Stocks rallied") are often repeated anyway.

### Decision record (2026-10-13)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Should a covered story be dropped, or can a follow-up appear? | It can appear when there's a meaningful new development, and it says what's new. |
| 2 | What happens when nothing new is found? | The model may write a short "nothing new" note, which is saved and emailed like any day. |
| 3 | What is sent as history? | Headlines only (the `<strong>` text), no links. |
| 4 | Should the item shape be enforced? | Yes, by tightening the prompt; no structured output. |
| 5 | How far back? | A setting, default 7 days. |
| 6 | Which past rows count? | Any row, whatever its status. |
| 7 | A rule or a suggestion? | A suggestion ("prefer other stories"), with no check in code. |

---

## Phase 3 — An admin can delete a user account ✅ **Done** (2026-10-14)

### Why

The admin, the owner only, needs to remove an account permanently, for example a fake or unwanted
sign-up. There's no UI: it's one admin-only endpoint called with curl or Postman. The account and its
subscriptions are hard-deleted, and the person can later register again with the same email as a
fresh account. The person isn't notified.

Only the gateway changes, because accounts live only in its `users` table and the mail service stores
nothing per user.

### What was built

- **Endpoint.** `DELETE /api/v1/users?email=`, admin only: a `USER` → 204; an `ADMIN` target, including the caller's own
  account → 403; unknown email → 404; missing, blank or malformed email → 400; a `USER` caller → 403; no token → 401.
- **Security.** `SecurityConfiguration` puts `/api/v1/users` and `/api/v1/users/**` (every method) under `hasRole(ADMIN)`.
- **Code.** `UserController`, `UserService`/`UserServiceImpl.deleteByEmail` (`@Transactional`, lowercased lookup, admin
  refused before `delete`), `UserNotFoundException` (404) and `UserNotDeletableException` (403) in `exceptions/users`.
  The success log line `Admin '{adminId}' deleted user '{userId}'.` is the only audit trail.
- **Cascade.** `Subscription.user` has `@OnDelete(CASCADE)`; no migration (`ddl-auto=create`).
- **Stale token.** `SubscriptionServiceImpl.lockCallersSubscriptions` throws `AuthenticatedUserNotFoundException`
  (401 "Please log in again.") when `lockById` is empty. The delete/subscribe race is safe by the row lock.
- **Tests.** `UserControllerIntegrationTest`, `UserServiceImplTest`, a cascade test in `SubscriptionRepositoryIntegrationTest`,
  and 401 scenarios in `SubscriptionControllerIntegrationTest` and `SubscriptionServiceImplTest`.
- **Manual check (2026-10-14).** On a live gateway: delete → 204, the stale cookie's subscribe → 401, re-register → 201
  with no subscriptions, the admin deleting themselves → 403. Docs in the gateway's `DECISIONS.md`, `CLAUDE.md`, `PLAN.md` and `TESTING.md`.

### Test strategy

- **Deterministic only.** Unique emails per test (random suffix). After every refused case (403/404/400),
  check in the database that nothing was deleted. The race between delete and subscribe is covered by
  reasoning (the row lock), not by a timing-dependent test.
- Each step's gate: `signal_flow_api_gateway` `mvn verify` green **three runs in a row**.
- Every new or changed `*ControllerIntegrationTest` scenario updates the gateway's `TESTING.md` in the
  same change.

### Accepted gaps

- **Shortcut: a deleted user's token keeps working until it expires** (up to `JWT_TTL`, 1 h). Reads
  succeed; subscribe answers 401. **Trigger:** a need to cut off access instantly, which means checking
  the user per request or keeping a revoked-token list.
- **The frontend doesn't treat a 401 from subscribe as "log in again".** It shows whatever it shows
  for a failed subscribe today. **Trigger:** deleted users actually hit this, or the frontend starts
  handling session expiry in general.
- **Queued emails aren't cancelled.** The window is a few seconds. A notification row that fails all
  send attempts stays in `notification_outbox` with the email address. **Trigger:** a data-erasure
  requirement, which means deleting the user's outbox rows in the same transaction.
- **No user list or admin UI.** You need to know the email. **Trigger:** more than one person
  administering accounts, or a need to browse users.
- **Admins can't be deleted through the app**, only in the database. **Trigger:** more than one admin.
- **No deactivate endpoint**, even though `User.enabled` exists. **Trigger:** a need to block an
  account reversibly.
- **The only audit trail is a log line.** **Trigger:** a need to answer "who deleted whom, when"
  after logs rotate.

### Decision record (2026-10-13)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Delete, deactivate, or both? | Hard delete of the account and its subscriptions. The person can register again. |
| 2 | Where does the admin do it? | An endpoint only, with no UI. |
| 3 | Who can be deleted? | Only `USER` accounts. Admins, including yourself, can't be. |
| 4 | A deleted user still logged in? | The window until the token expires is accepted; subscribe answers 401 instead of failing. |
| 5 | Cancel queued emails? | No: the outbox sends within seconds and deletes the row. |
| 6 | Notify the deleted person? | No. |
| 7 | How is the user picked? | By email: `DELETE /api/v1/users?email=…`. |

---

## Phase 4 — Each topic has a news schedule ✅ **Done** (2026-10-14)

### Why

Some topics change every day (crypto), some only on trading days (stocks), and some barely change in a
week (nutrition and fitness). Generating every topic daily produces thin, repetitive issues for slow
topics and wastes AI calls. Each topic now has a **schedule preset**, set by the admin. The daily job
skips a topic on the days its preset doesn't include. Each issue covers **the whole period since the
topic's previous scheduled day**, so nothing between two issues is lost. Users see the schedule on the
topic card, and the email says which period it covers.

This touches every service: the topic service (schedule, job, prompt, event), the gateway (feed, event,
outbox), the mail service (header), the frontend (card) and e2e.

### What was built

- **Presets.** `NewsSchedule` (topic service, `entities/enums`): `DAILY`, `WEEKDAYS`, `WEEKENDS`, `WEEKLY` (Monday), with
  `runsOn(DayOfWeek)` and `periodStart(newsDate)`, which walks back to the previous day the preset runs on. Days are
  judged in `app.news.zone`.
- **Topic.** `InterestTopic.schedule` (`@Enumerated(STRING)`, not null, default `DAILY`). Create takes an optional
  `schedule` (missing or null → `DAILY`, set in the service); update takes it with null meaning unchanged; an unknown
  value → 400. Responses and the internal feed items carry `schedule`. No migration (Q6 of Phase 1).
- **Seeds.** Every `TopicSeed`, active and commented out, carries a preset.
- **Job.** `generateDailyNews()` only computes today's date and calls `generateNewsFor(LocalDate)`. A topic whose preset
  doesn't run that day is skipped first (debug log), before the "already exists" query and any AI call.
- **Snapshot.** `TopicNews.schedule` and `periodStart` (both not null) are set when the row is created, so the event and
  email describe what was generated even if the schedule is edited before the outbox publishes.
- **Prompt.** `generate(topic, newsDate, periodStart, coveredHeadlines)`: one day keeps "on or just before D"; a range
  says "the most notable news published between S and D". At most 5 items for every preset.
- **Events.** `topic-news.generated` and `topic-news.notification-requested` carry `schedule` and `periodStart` in all
  three services. The gateway carries `schedule` as a `String` (`notification_outbox.schedule varchar(16)`,
  `period_start date`); its guard skips a record with a blank or over-16-character `schedule` or a null `periodStart`.
  The mail service has its own `NewsSchedule` copy with `@NotNull`; an unknown value fails JSON reading and dead-letters.
- **Gateway feed.** The topic-service and feed DTOs carry `schedule` as a `String`; the routes pass it through unchanged.
- **Mail.** Token `NEWS_PERIOD` replaces `NEWS_DATE`. `DAILY`: "News for 13 October 2026" (unchanged). Others: label,
  ` · `, then "14 October 2026", "6–12 October 2026", "29 September – 5 October 2026" or
  "29 December 2026 – 4 January 2027". The hidden preheader now starts with a capital "News for".
- **Frontend.** `FeedSchedule` type; `TopicCard` shows a small muted label under the title ("Daily", "Weekdays",
  "Weekends", "Weekly · Mondays"); an unknown value renders nothing.
- **e2e.** `createTopicViaApi` takes an optional schedule; `feed.spec.ts` checks "Weekly · Mondays" and the default "Daily".
- **Audit.** A smoke check on the e2e stack (2026-10-14, fake OpenRouter, cron every minute): the `WEEKDAYS` topic got a
  row (`schedule` WEEKDAYS, `period_start` 2026-10-14) and the `WEEKENDS` topic none; the email header read
  "Weekdays · 14 October 2026"; both cards showed their labels. No exploit-hunter audit was run for this phase.

### Test strategy

- **Deterministic only. No test depends on today's date.** `NewsSchedule` is tested with fixed dates,
  and the job's tests call `generateNewsFor(fixed date)`. Pick dates by weekday on purpose, e.g.
  2026-10-12 is a Monday, 2026-10-17 a Saturday and 2026-10-18 a Sunday.
- Prompt and email assertions are exact strings for the cases in the Design tables.
- Each service gate: `mvn verify` green **three runs in a row**. Every new or changed
  `*ControllerIntegrationTest` scenario updates that service's `TESTING.md` in the same change.

### Accepted gaps

- **Shortcut: no mixed-version events.** The new event fields have to ship in all three services
  together; a stale event is skipped by the gateway guard or rejected by the mail validation.
  Acceptable because the databases are wiped on restart. **Trigger:** keeping data across deploys or
  deploying the services separately. This is the same trigger as Phase 1.
- **A missed or failed run is lost, not retried.** If the service is down, or the AI call fails, on a
  `WEEKLY` topic's Monday, that week has no issue. The next issue covers only its own 7 days. **Trigger:**
  a weekly issue goes missing, which means a same-day retry or a catch-up run on start.
- **The period comes from the preset, not from the last issue.** After a missed run or a schedule
  change, the period can leave a gap (see above) or overlap the previous issue. **Trigger:** readers
  notice gaps or repeats after schedule changes.
- **Only four presets.** `WEEKLY` is always Monday, and there's no every-2-weeks or monthly option.
  **Trigger:** a topic that needs another day or rhythm.
- **One time of day for every topic.** **Trigger:** a topic that needs news at a different hour.
- **No feed filter by schedule.** **Trigger:** users ask to browse by how often a topic updates.

### Decision record (2026-10-13)

| # | Question | Decision |
| --- | --- | --- |
| 1 | What shape should the schedule take? | Presets (`DAILY`, `WEEKDAYS`, `WEEKENDS`, `WEEKLY`), so they're easy to show in the UI. |
| 2 | Per-topic time of day? | No: one job time for all topics, only the days vary. |
| 3 | What period does an issue cover? | Everything since the topic's previous scheduled day; for `WEEKENDS` that makes Saturday a weekly roundup. |
| 4 | Show the schedule to users? | Yes, on the topic card. |
| 5 | Defaults and seeds? | New topics are `DAILY`; each seed topic gets a realistic preset. |
| 6 | Catch up missed runs? | No. |
| 7 | Which day for `WEEKLY`? | Monday. |
| 8 | Items per issue? | At most 5 for every preset. |
| 9 | Show the period in the email? | Yes, for non-daily topics; daily emails are unchanged. |
