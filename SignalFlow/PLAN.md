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

## Left open

- **Phase 1, step 7: the frontend gate's manual check was waived.** Three 40-character chips at 375 px and in both
  themes were not checked. The 2026-10-14 smoke check looked only at desktop width, dark theme.

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

## Phase 4 — Each topic has a news schedule

### Why

Some topics change every day (crypto), some only on trading days (stocks), and some barely change in a
week (nutrition and fitness). Generating every topic daily produces thin, repetitive issues for slow
topics and wastes AI calls. Each topic now has a **schedule preset**, set by the admin. The daily job
skips a topic on the days its preset doesn't include. Each issue covers **the whole period since the
topic's previous scheduled day**, so nothing between two issues is lost. Users see the schedule on the
topic card, and the email says which period it covers.

This touches every service: the topic service (schedule, job, prompt, event), the gateway (feed, event,
outbox), the mail service (header), the frontend (card) and e2e.

### Design

**The presets.** These go in a new enum `NewsSchedule`, in the topic service's `entities/enums`:

| Preset | Runs on | Card label | Email label |
| --- | --- | --- | --- |
| `DAILY` (default) | every day | Daily | *(unchanged email)* |
| `WEEKDAYS` | Mon–Fri | Weekdays | Weekdays |
| `WEEKENDS` | Sat, Sun | Weekends | Weekends |
| `WEEKLY` | Mon | Weekly · Mondays | Weekly |

- `boolean runsOn(DayOfWeek day)`.
- `LocalDate periodStart(LocalDate newsDate)`: walk back from `newsDate − 1` to the previous day the
  preset runs on; the period starts the day after it, so it is `[periodStart, newsDate]`. This gives:
  - `DAILY` → `newsDate` (one day);
  - `WEEKDAYS` on Mon → the previous Saturday, on Tue–Fri → `newsDate`;
  - `WEEKENDS` on Sat → the previous Monday (a weekly roundup), on Sun → `newsDate`;
  - `WEEKLY` → `newsDate − 6`.

  It's only called on a day the preset runs on.
- **The period comes from the preset, not from the last news row.** If the admin switches a topic from
  `WEEKLY` to `DAILY` on a Wednesday, Thursday's issue simply covers Thursday. No history lookup is
  needed.
- Days are judged in `app.news.zone` (Europe/Sofia), the same zone `newsDate` already uses. The job
  still runs once a day at the same time for every topic.

**Data model (topic service).**
- `InterestTopic.schedule`: `@Enumerated(STRING) @Column(nullable = false)`, `@Builder.Default` `DAILY`.
- `TopicNews` gets `schedule` and `periodStart` (`LocalDate`), both `nullable = false`. They are set
  by the job when the row is created, a **snapshot**. The event and the email then describe what was
  actually generated, even if the admin changes the topic's schedule before the outbox publishes.
- No migration: `ddl-auto=create`, as in the earlier phases.

**HTTP contract (topic service; the gateway route passes it through).**

| Where | Change |
| --- | --- |
| `CreateInterestTopicRequestDTO` | `NewsSchedule schedule`, optional. If missing or null, it defaults to `DAILY`. |
| `UpdateInterestTopicRequestDTO` | `NewsSchedule schedule`, where null means unchanged. |
| `InterestTopicResponseDTO` (also the internal feed's items) | `NewsSchedule schedule` |
| An unknown value, e.g. `"MONTHLY"` | **400**, the same path as any unreadable body. |

**The job (`TopicNewsGenerationJob`).**
- Split it: `@Scheduled generateDailyNews()` only computes today's `newsDate` and calls a new public
  `generateNewsFor(LocalDate newsDate)`. Tests call `generateNewsFor` with fixed dates, so they never
  depend on what day the build runs.
- In `generateNewsForTopic`, **first** `if (!topic.getSchedule().runsOn(newsDate.getDayOfWeek()))
  return;`, logged at `debug`. This happens before the "already exists" query, so skipped topics cost
  no database read and no AI call. The page loop over all topics stays as it is, because it is cheap.
- Compute `periodStart` once, pass it to `generate`, and store it and `schedule` on the new row.

**The prompt (`NewsGenerationServiceImpl`).** `generate` gets the period start, and the first sentence
of `SYSTEM_PROMPT_TEMPLATE` depends on the period:
- one day (`periodStart == newsDate`): unchanged, "Report notable news published on or just before
  2026-10-13 …";
- a range: "Report the most notable news published between 2026-10-06 and 2026-10-12 …".

Everything else stays the same, including "at most 5 items". A weekly issue is the top 5 of the week.
This works with Phase 2 as it is: a weekly topic's previous issue is exactly 7 days back, so it's
inside the 7-day "already covered" window.

**Event contract (all three services).**

| Event | Added fields |
| --- | --- |
| `topic-news.generated` (`TopicNewsEventDTO`, topic service + gateway copy) | `schedule`, `periodStart` |
| `topic-news.notification-requested` (`TopicNewsNotificationEventDTO`, gateway + mail copy) | `schedule`, `periodStart` |

- **Topic service:** `toEvent` reads both from the `TopicNews` row, not from the topic.
- **Gateway:** it never interprets the schedule, so it carries it as a `String`, plus
  `LocalDate periodStart`. `notification_outbox` gets the columns `schedule varchar(16) not null` and
  `period_start date not null`, bound in the batch insert. The existing guard also skips the record,
  through `skipOversizedRecord`, when `schedule` is blank or longer than 16 characters, or when
  `periodStart` is null.
- **Mail:** its own `NewsSchedule` enum copy with `@NotNull`, and `@NotNull LocalDate periodStart`. An
  unknown or missing value takes the same path an invalid event takes today.

**Email.** The token `NEWS_DATE` becomes `NEWS_PERIOD`, in `KNOWN_TOKENS` and in both templates. The
lines "News for {{NEWS_DATE}}" and the preheader's "news for {{NEWS_DATE}}" become `{{NEWS_PERIOD}}`.
The renderer builds the text with the existing `d MMMM yyyy` style:

| Case | Text |
| --- | --- |
| `DAILY` | News for 13 October 2026 *(exactly as today)* |
| Not daily, one day | Weekdays · 14 October 2026 |
| A range within one month | Weekly · 6–12 October 2026 |
| A range across months | Weekly · 29 September – 5 October 2026 |
| A range across years | Weekly · 29 December 2026 – 4 January 2027 |

The `·` is ` &middot; ` in the HTML version, as elsewhere in that template.

**Feed card.**
- The gateway's `InterestTopicResponseDTO` and `FeedTopicResponseDTO` get `String schedule`, passed
  through by `FeedServiceImpl`.
- `frontend/src/api/feed.ts`: `schedule: 'DAILY' | 'WEEKDAYS' | 'WEEKENDS' | 'WEEKLY'`.
- `TopicCard` shows the card label from the table on **every** card, `Daily` included, as small muted
  text, visually distinct from the category chip(s). An unknown value falls back to showing nothing.

**Seed schedules.** `TopicSeed` gets a `NewsSchedule`. The commented-out entries get one too, so they
still compile when uncommented. Adjust freely:

| Topic | Preset | | Topic | Preset |
| --- | --- | --- | --- | --- |
| Artificial Intelligence | `DAILY` | | Medical Research | `WEEKLY` |
| Consumer Electronics | `WEEKLY` | | Climate Science | `WEEKLY` |
| Cybersecurity | `WEEKDAYS` | | Public Health | `WEEKDAYS` |
| Stock Markets | `WEEKDAYS` | | Nutrition & Fitness | `WEEKLY` |
| Startups & Venture Capital | `WEEKLY` | | Mental Health | `WEEKLY` |
| Cryptocurrency | `DAILY` | | Football (Soccer) | `WEEKENDS` |
| U.S. Politics | `DAILY` | | Basketball | `DAILY` |
| European Union Affairs | `WEEKDAYS` | | Olympic Sports | `WEEKLY` |
| Elections Worldwide | `WEEKLY` | | Film & Television | `WEEKENDS` |
| Space Exploration | `WEEKLY` | | Music Industry | `WEEKLY` |
| Video Games | `WEEKLY` | | Renewable Energy | `WEEKLY` |
| Climate Policy | `WEEKLY` | | Wildlife Conservation | `WEEKLY` |
| Middle East | `DAILY` | | Asia-Pacific | `WEEKDAYS` |
| International Trade | `WEEKDAYS` | | Electric Vehicles | `WEEKLY` |
| Autonomous Driving | `WEEKLY` | | Aviation & Aerospace | `WEEKLY` |

**Order with the other phases.** Phases 1 and 4 both add fields to the same DTOs, events, outbox
insert and email templates. Whichever phase runs second adds its fields alongside the first one's;
the parameter indices in the gateway's outbox `INSERT` shift accordingly.

### Test strategy

- **Deterministic only. No test depends on today's date.** `NewsSchedule` is tested with fixed dates,
  and the job's tests call `generateNewsFor(fixed date)`. Pick dates by weekday on purpose, e.g.
  2026-10-12 is a Monday, 2026-10-17 a Saturday and 2026-10-18 a Sunday.
- Prompt and email assertions are exact strings for the cases in the Design tables.
- Each service gate: `mvn verify` green **three runs in a row**. Every new or changed
  `*ControllerIntegrationTest` scenario updates that service's `TESTING.md` in the same change.

### Steps

#### 1. Topic service: the `NewsSchedule` enum

- [ ] `entities/enums/NewsSchedule` with `runsOn` and `periodStart`, as in Design.
- [ ] `NewsScheduleTest` (unit, parameterized):
  - [ ] `runsOn` for every preset × all 7 days;
  - [ ] `periodStart` for every preset on every day it runs: `WEEKDAYS` on Mon 2026-10-12 → Sat
        2026-10-10; `WEEKENDS` on Sat 2026-10-17 → Mon 2026-10-12, on Sun 2026-10-18 → 2026-10-18;
        `WEEKLY` on 2026-10-12 → 2026-10-06; `DAILY` → the same day;
  - [ ] a period that crosses a month and a year boundary, e.g. `WEEKLY` on Mon 2027-01-04 → 2026-12-29.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 2. Topic service: schedule on the topic, the HTTP contract and the seeds

- [ ] `InterestTopic.schedule` (Design). Request and response DTOs as in the HTTP contract table;
      `InterestTopicService`/`Impl` and `InterestTopicResponseMapper` carry it.
- [ ] `DatabaseSeedServiceImpl`: each `TopicSeed`, active and commented out, gets its preset from the
      seed table.
- [ ] Tests, `InterestTopicControllerIntegrationTest`:
  - [ ] create without `schedule` → 201 and `DAILY`;
  - [ ] create with `WEEKLY` → 201 and `WEEKLY`, also in `GET` by id and in the list;
  - [ ] create or PATCH with `"MONTHLY"` → 400;
  - [ ] PATCH with `schedule` changes only it; PATCH without it leaves it unchanged.
- [ ] Tests, `InternalInterestTopicControllerIntegrationTest`: the feed items carry `schedule`.
- [ ] Tests, `DatabaseSeedServiceImplTest`: seeded topics carry their preset, e.g. Stock Markets is
      `WEEKDAYS` and Nutrition & Fitness is `WEEKLY`.
- [ ] Update `TESTING.md`.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 3. Topic service: the job skips off-schedule topics, and the prompt covers the period

- [ ] `TopicNews.schedule` and `TopicNews.periodStart` (Design).
- [ ] `TopicNewsGenerationJob`: the `generateNewsFor(LocalDate)` split, the schedule check first, then
      compute `periodStart` and store both on the row.
- [ ] `NewsGenerationService`/`Impl`: the period-start parameter and the two prompt wordings.
- [ ] Tests, `TopicNewsGenerationJobTest` (unit): on Tuesday 2026-10-13 a `WEEKLY` topic is skipped, with
      no repository or `generate` call. On Monday 2026-10-12 it's generated with `periodStart`
      2026-10-06, and the saved row carries `WEEKLY` and 2026-10-06.
- [ ] Tests, `TopicNewsGenerationJobIntegrationTest`: four topics, one per preset;
      `generateNewsFor(Sat 2026-10-17)` creates rows only for `DAILY` and `WEEKENDS`, and the
      `WEEKENDS` row has `periodStart` 2026-10-12. Existing tests in this class call
      `generateNewsFor` with a fixed date.
- [ ] Tests, `NewsGenerationServiceImplIntegrationTest`: one day → the system message contains the
      unchanged "on or just before 2026-10-13" sentence; a range → it contains "between 2026-10-06 and
      2026-10-12". Existing tests follow the new signature.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 4. Topic service: `topic-news.generated` carries `schedule` and `periodStart`

- [ ] `TopicNewsEventDTO`: `NewsSchedule schedule`, `LocalDate periodStart`; `toEvent` reads them from
      the row.
- [ ] Tests, `TopicNewsOutboxPublisherIntegrationTest`: a row saved with `WEEKLY`/2026-10-06 → the Kafka
      record carries `"WEEKLY"` and `"2026-10-06"`, even after the topic's schedule is changed to
      `DAILY` before the publish (the snapshot). `TopicNewsOutboxPublisherTest` follows the new shape.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 5. Gateway: feed and route DTOs carry `schedule`

- [ ] Gateway `InterestTopicResponseDTO` and `FeedTopicResponseDTO`: `String schedule`. `FeedServiceImpl`
      passes it through.
- [ ] Tests: `FeedControllerIntegrationTest`: a stubbed topic with `WEEKLY` → the feed item carries
      `WEEKLY`. `InterestTopicRoutesIntegrationTest`: a create body with `schedule` and a response with
      `schedule` pass through unchanged.
- [ ] Update `TESTING.md`.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 6. Gateway: events and outbox carry `schedule` and `periodStart`

- [ ] Both gateway event DTOs, `NotificationOutbox` (two new columns),
      `TopicNewsNotificationServiceImpl` (insert and guard) and
      `NotificationOutboxPublisherServiceImpl` (mapping onto the outgoing event).
- [ ] Tests, `TopicNewsListenerIntegrationTest`: an event with `WEEKLY`/2026-10-06 → outbox rows carry
      both. A null `periodStart`, a blank `schedule`, or a 17-character `schedule` → skipped and marked in
      the inbox, asserted with a sentinel event. `NotificationOutboxRepositoryIntegrationTest`: both
      columns round-trip. The publisher and service unit tests follow the new shape.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 7. Mail service: the `NEWS_PERIOD` header

- [ ] `TopicNewsNotificationEventDTO`: the mail-side `NewsSchedule` copy and `periodStart`, both `@NotNull`.
- [ ] `TopicNewsEmailRenderer`: `NEWS_DATE` → `NEWS_PERIOD`, built as in the email table. Update both
      templates: the header line and the preheader.
- [ ] Tests (renderer): each row of the email table, as exact strings, in both the HTML and the text
      version. A `DAILY` email's header is byte-for-byte what it is today. Validation: a null `schedule`,
      a null `periodStart`, an unknown schedule value → rejected the way an invalid event is today.
      `AbstractKafkaE2ETestSupport` fixtures follow the new shape.
- [ ] Update `TESTING.md` if any listed scenario changed.
- [ ] **Gate:** `signal_flow_mail_service` `mvn verify` green 3× in a row.

#### 8. Frontend: the schedule label on the card

- [ ] `src/api/feed.ts`: the `schedule` union type.
- [ ] `TopicCard.tsx`/`.css`: the label from the Design table, small and muted, styled with the existing
      tokens and `--home-scale`. An unknown value renders nothing.
- [ ] **Gate:** `npm run build` and `npm run lint` pass. Then a manual check with the stack running: cards
      show "Daily", "Weekdays" and "Weekly · Mondays" correctly at 375 px and desktop widths, in both
      themes, without crowding the title or the category.

#### 9. e2e: one schedule check

- [ ] `e2e/tests/fixtures.ts`: an optional `schedule` when creating a topic. Existing callers stay on
      the default.
- [ ] `feed.spec.ts`: a topic created with `WEEKLY` shows "Weekly · Mondays" on its card, and a default
      topic shows "Daily".
- [ ] Update `e2e/PLAN.md` or `e2e/CLAUDE.md` if either lists the scenarios.
- [ ] **Gate:** the full e2e suite is green twice in a row against a freshly started stack.

#### 10. Docs and the end-to-end smoke check

- [ ] `DECISIONS.md` entries:
  - topic service: presets instead of cron or days-of-week; the period derived from the preset; the
    snapshot on `TopicNews`; `WEEKLY` fixed to Monday; 5 items for every preset; the
    `generateNewsFor` split for testability;
  - gateway: `schedule` carried as an uninterpreted `String`;
  - mail: the `NEWS_PERIOD` format.
- [ ] `CLAUDE.md` "easy to break" notes:
  - topic service: the job must not compute `newsDate` anywhere except `generateDailyNews`;
  - all three services: the event fields must ship together. Record this as a **shortcut**, as in
    Phase 1.
- [ ] **Gate:** a manual smoke check on a freshly started full stack. On the day of the check:
  1. Create one non-daily topic that runs today (`WEEKDAYS` on Mon–Fri, `WEEKENDS` on Sat–Sun) and one
     that doesn't. Subscribe to both.
  2. Trigger generation with `NEWS_CRON` a minute ahead.
  3. Only the first topic gets a `topic_news` row, with the expected `schedule` and `period_start`.
  4. Its email header shows the label and the period from the email table.
  5. Both cards show their labels.

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
