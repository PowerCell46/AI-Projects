# SignalFlow — cross-service improvements

Improvements that touch more than one service, picked from the first one to two weeks of running the
system. Each service still keeps its own `PLAN.md` for service-local work.

**How to work through a phase here.** Do the steps in order. Inside a step, tick each task `- [x]` as it
lands. A step is done only when its **Gate** passes: tick the gate and append ` ✅ **Done** (YYYY-MM-DD)`
to the step heading in the same change. **No step starts on a red or missing test, and each step ends
green.** Before any `.java` edit load `java-code-style` (and `java-junit` for tests); before any `.ts`/`.tsx`/
`.css` edit load `frontend-code-style`. Each service's own `CLAUDE.md` rules still apply.

---

## Phase 1 — An interest topic belongs to 1–3 categories

### Why

`InterestTopic → Category` is `@ManyToOne` today, but real topics span several categories (e.g. "AI
regulation" is both Technology and Politics). A topic gets **1 to 3** categories, all equal (no primary).
**Every** category is shown on the feed's topic card and in the news email. Category stays a label: there
is no category filter in the feed (see Accepted gaps).

### Design

**Data model (topic service).**
- `InterestTopic.category` becomes `Set<Category> categories`: `@ManyToMany(fetch = LAZY)`,
  `@JoinTable(name = "interest_topic_categories", joinColumns = interest_topic_id,
  inverseJoinColumns = category_id)`, plus `@BatchSize(size = 100)` so loading the categories of a page of
  topics costs one extra query, not one per topic. `InterestTopic` owns the relation; `Category` gets no
  back-reference.
- A constant `InterestTopic.MAX_CATEGORIES = 3`, used by the request DTOs' `@Size`.
- `Category.name` max length drops **100 → 40** (entity `@Size` + `@Column(length = 40)`, and
  `CategoryRequestDTO`). This keeps chips and the email header tidy. The seed names already fit (longest is
  "Automotive & Transportation", 27).
- No migration: `ddl-auto=create` recreates both databases on every start, and that is intentional.

**HTTP contract (topic service, passed through unchanged by the gateway's `/api/v1/interest-topics/**`
route).**

| Where | Before | After |
| --- | --- | --- |
| `CreateInterestTopicRequestDTO` | `@NotNull UUID categoryId` | `@NotNull @Size(min = 1, max = MAX_CATEGORIES) List<@NotNull UUID> categoryIds` |
| `UpdateInterestTopicRequestDTO` | `UUID categoryId` (null = unchanged) | `@Size(min = 1, max = MAX_CATEGORIES) List<@NotNull UUID> categoryIds` (null = unchanged; present = **replaces** the whole set; `[]` → 400) |
| `InterestTopicResponseDTO` | `categoryId`, `categoryName` | `List<CategorySummaryResponseDTO> categories` (`id`, `name`), **sorted by name** |
| `GET /api/v1/interest-topics?categoryId=` | topics in that category | topics that have that category among theirs (unchanged parameter) |

- Duplicate ids in `categoryIds` → **400**, through a new `DuplicateCategoryIdsException` mapped in
  `GlobalExceptionHandler`. Duplicates are rejected, not silently de-duplicated.
- Any unknown id → **404** `CategoryNotFoundException`, as today. Resolve with `categoryRepository.findAllById(ids)`;
  a size mismatch means an id is unknown.
- Deleting a category stays **409 `CategoryInUseException`** while any topic has it, whether as its only category
  or one of several. `existsByCategory_Id` → `existsByCategories_Id`, and `findByCategory_Id` →
  `findByCategories_Id`.

**Reads without collection fetch-joins.** Never `JOIN FETCH` the `categories` collection in a query with
`Pageable`/`Limit`, because Hibernate then paginates in memory (HHH90003004). Concretely:
- `InterestTopicRepository.findPageAfter*`: drop `JOIN FETCH t.category`. The controllers map entities to
  DTOs inside the open session (`open-in-view` is on by default, which is what the lazy `category` already
  relies on), and `@BatchSize` loads the categories in one extra query.
- `TopicNewsRepository.findByStatusAndNextAttemptAt…`: keep `JOIN FETCH tn.interestTopic` (to-one, safe) and
  drop `JOIN FETCH topic.category`. The publisher reads its batch **detached** (no `@Transactional`, by
  decision: `DECISIONS.md` step 7), so it can't lazy-load. Add
  `InterestTopicRepository.findCategoryNamesByTopicIds(Collection<UUID>)`: a JPQL projection
  `SELECT t.id, c.name FROM InterestTopic t JOIN t.categories c WHERE t.id IN :ids`. The publisher calls it
  once per batch and groups the result into a `Map<UUID, List<String>>` (names sorted).

**Event contract (all three services).**

| Event | Before | After |
| --- | --- | --- |
| `topic-news.generated` (`TopicNewsEventDTO`, topic service + gateway copy) | `String categoryName` | `List<String> categoryNames` (sorted by name, 1–3) |
| `topic-news.notification-requested` (`TopicNewsNotificationEventDTO`, gateway + mail copy) | `String categoryName` | `List<String> categoryNames` |

- **Gateway outbox:** `notification_outbox.category_name varchar` becomes `category_names text[]`:
  `@JdbcTypeCode(SqlTypes.ARRAY) @Column(name = "category_names", nullable = false, columnDefinition = "text[]")
  List<String> categoryNames`. The batch insert in `TopicNewsNotificationServiceImpl` binds it with
  `ps.setArray(7, ps.getConnection().createArrayOf("text", names.toArray()))`. It's a list, not a joined string,
  so the 255-character column cap can't skip a valid record, and formatting stays in the mail service.
- **Gateway guard:** the current `categoryName.length() > MAX_COLUMN_LENGTH` check becomes "skip (via the
  existing `skipOversizedRecord`) when `categoryNames` is null or empty, or any name exceeds
  `MAX_COLUMN_LENGTH`".
- **Mail validation:** `@NotEmpty @Size(max = 3) List<@NotBlank @Size(max = MAX_NAME_LENGTH) String>
  categoryNames`. An invalid event takes the same path today's invalid `categoryName` takes.

**Display.**
- **Email:** the token `CATEGORY_NAME` is renamed to `CATEGORY_NAMES` in `KNOWN_TOKENS` and both templates.
  The HTML version escapes each name with `HtmlUtils.htmlEscape` and joins them with ` &middot; `. The
  plain-text version applies `stripLineBreaks` to each name and joins them with ` · `. One category renders
  with no separator.
- **Feed card:** `frontend/src/api/feed.ts` changes `categoryId`/`categoryName` to
  `categories: { id: string; name: string }[]`. `TopicCard` renders one chip per category (`key={id}`) in a
  wrapping flex row. Each chip has `max-width: 100%`, `overflow: hidden`, `text-overflow: ellipsis` and
  `white-space: nowrap`, so three 40-character names on a phone-width card wrap instead of overflowing.
- **Gateway feed:** `FeedTopicResponseDTO` and the gateway's `InterestTopicResponseDTO` change
  `categoryId`/`categoryName` to `List<…> categories` (`id`, `name`), passed through in the order the topic
  service sends.

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

### Steps

#### 1. Topic service: category name capped at 40

- [ ] `Category.name`: `@Size(max = 40)`, `@Column(length = 40)`; `CategoryRequestDTO.name`: `@Size(max = 40)`.
- [ ] `CategoryControllerIntegrationTest`: a 40-character name → 201, a 41-character name → 400, on both create and rename.
- [ ] Update `TESTING.md`.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 2. Topic service: many-to-many model, HTTP contract and admin filter

- [ ] `InterestTopic`: `Set<Category> categories` with `@ManyToMany`/`@JoinTable`/`@BatchSize` as in Design;
      add `MAX_CATEGORIES = 3`.
- [ ] Request DTOs: `categoryIds` as in the HTTP contract table.
- [ ] `DuplicateCategoryIdsException` → 400 in `GlobalExceptionHandler`.
- [ ] `InterestTopicService`/`Impl`: `create` and `update` take `List<UUID> categoryIds`; resolve via
      `findAllById`; reject duplicates (400) and unknown ids (404); `update` replaces the set when non-null.
- [ ] `CategorySummaryResponseDTO(id, name)`; `InterestTopicResponseDTO.categories`;
      `InterestTopicResponseMapper` sorts by name.
- [ ] Repository: `existsByCategories_Id`, `findByCategories_Id`; drop `JOIN FETCH t.category` from the three
      `findPageAfter*` queries.
- [ ] `CategoryServiceImpl.delete` uses `existsByCategories_Id`.
- [ ] `DatabaseSeedServiceImpl`: pass `List.of(category.getId())`. The seed content stays single-category.
- [ ] Tests, `InterestTopicControllerIntegrationTest`:
  - [ ] create with 1 and with 3 categories → 201, response `categories` sorted by name;
  - [ ] create with 0 ids, 4 ids, a `null` element, duplicate ids → 400;
  - [ ] create with one unknown id among known ones → 404, and no topic row is created;
  - [ ] PATCH with `categoryIds` replaces the set; PATCH without it leaves the set unchanged;
        PATCH with `[]` → 400;
  - [ ] `GET ?categoryId=X` returns a topic whose **second** category is X; `totalElements` counts each topic once.
- [ ] Tests, `CategoryControllerIntegrationTest`: deleting a category that is one of a topic's three
      categories → 409; after that topic is deleted, the category deletes → 204. This proves the join rows go
      with the topic.
- [ ] Tests, `InternalInterestTopicControllerIntegrationTest` and `InterestTopicRepositoryIntegrationTest`:
      3 topics × 3 categories with limit 2 → exactly 2 distinct topics, each with all 3 categories, for all
      three `findPageAfter*` variants.
- [ ] Unit tests (`InterestTopicServiceImplTest`, `CategoryServiceImplTest`, `DatabaseSeedServiceImplTest`)
      follow the new signatures.
- [ ] Update `TESTING.md`.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 3. Topic service: `topic-news.generated` carries `categoryNames`

- [ ] `TopicNewsEventDTO.categoryName` → `List<String> categoryNames`.
- [ ] `TopicNewsRepository`: drop `JOIN FETCH topic.category` and keep `JOIN FETCH tn.interestTopic`.
- [ ] `InterestTopicRepository.findCategoryNamesByTopicIds` (projection query from Design).
- [ ] `TopicNewsOutboxPublisher`: one `findCategoryNamesByTopicIds` call per batch → `Map<UUID, List<String>>`
      sorted by name; `toEvent` reads from it. Still no `@Transactional`.
- [ ] Tests: `TopicNewsOutboxPublisherIntegrationTest`: a topic with 3 categories → the Kafka record's
      `categoryNames` equals the 3 names in sorted order. Also, a due batch of several topics with different
      category sets → each event carries its own topic's names. `TopicNewsRepositoryIntegrationTest`: the
      due-batch query returns each news row once and respects the page size when topics have 3 categories.
      `TopicNewsOutboxPublisherTest` follows the new shape.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 4. Gateway: feed and route DTOs

- [ ] Gateway `InterestTopicResponseDTO` and `FeedTopicResponseDTO`: `categoryId`/`categoryName` →
      `List<…> categories` (`id`, `name`), in a new gateway-side summary DTO. `FeedServiceImpl` passes the list
      through.
- [ ] Tests: `FeedControllerIntegrationTest`: a topic stubbed with 3 categories → the feed returns all 3 in the
      stubbed order. `InterestTopicRoutesIntegrationTest`: a create/patch body with `categoryIds` and a
      response with `categories` pass through unchanged.
- [ ] Update `TESTING.md`.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 5. Gateway: events and notification outbox carry `categoryNames`

- [ ] Gateway `TopicNewsEventDTO` and `TopicNewsNotificationEventDTO`: `categoryName` → `List<String> categoryNames`.
- [ ] `NotificationOutbox.categoryNames` as `text[]` (Design); `TopicNewsNotificationServiceImpl` binds it with
      `setArray`; `NotificationOutboxPublisherServiceImpl` maps it onto the outgoing event.
- [ ] Guard: skip when `categoryNames` is null or empty, or any name is longer than `MAX_COLUMN_LENGTH`.
- [ ] Tests: `NotificationOutboxRepositoryIntegrationTest`: a 3-name `text[]` round-trips in order.
      `TopicNewsListenerIntegrationTest`: an event with 3 names → outbox rows carry all 3 in order. Empty
      list, null list, and one 256-character name → skipped and inbox-marked, asserted with a sentinel event.
      `NotificationOutboxPublisherServiceImplTest` and `TopicNewsNotificationServiceImplTest` follow the new
      shape.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 6. Mail service: render all categories

- [ ] `TopicNewsNotificationEventDTO.categoryNames` with the validation from Design.
- [ ] `TopicNewsEmailRenderer`: token `CATEGORY_NAMES`; HTML: escape each name and join with ` &middot; `; text:
      strip line breaks from each name and join with ` · `.
- [ ] `topicNewsEmailTemplate.html` (header and preheader) and `.txt`: `{{CATEGORY_NAME}}` → `{{CATEGORY_NAMES}}`.
- [ ] Tests (renderer): 1 name → no separator; 3 names → joined in the given order in both HTML and text;
      a name containing `<b>&` is escaped in HTML only; a name containing `\r\n` is flattened in text.
      Validation: empty list, 4 names, a blank name → rejected the way an invalid event is today.
      `AbstractKafkaE2ETestSupport` fixtures follow the new shape.
- [ ] Update `TESTING.md` if any listed scenario changed.
- [ ] **Gate:** `signal_flow_mail_service` `mvn verify` green 3× in a row.

#### 7. Frontend: one chip per category

- [ ] `src/api/feed.ts`: `categories: { id: string; name: string }[]`.
- [ ] `TopicCard.tsx`/`.css`: a chip row as in Design (wrapping, ellipsis per chip, keyed by id), styled with
      the existing tokens and `--home-scale`. `.topic-card-category` becomes the chip class.
- [ ] **Gate:** `npm run build` and `npm run lint` pass; then a manual check with the stack running. A topic with three
      40-character categories shows three chips that wrap without horizontal scroll at 375 px and at desktop width,
      in both themes.

#### 8. e2e: fixtures and one multi-category check

- [ ] `e2e/tests/fixtures.ts`: create topics with `categoryIds: [id]`. Add a helper to create a topic with
      several categories.
- [ ] `feed.spec.ts`: a topic created with 2 categories shows both names on its card, in name order.
- [ ] Update `e2e/PLAN.md` or `e2e/CLAUDE.md` if either lists the scenarios.
- [ ] **Gate:** the full e2e suite is green twice in a row against a freshly started stack.

#### 9. Docs and the end-to-end smoke check

- [ ] `DECISIONS.md` entries: topic service (many-to-many with a cap of 3, duplicates rejected, no collection
      fetch-join with paging, the publisher's per-batch category query, name cap 40); gateway (outbox
      `text[]` instead of a joined string); mail (the `CATEGORY_NAMES` join format).
- [ ] Each touched service's `CLAUDE.md` gets an "easy to break" note: never `JOIN FETCH` `categories` in a
      paged query (topic service); event field changes must ship to all three services together (all three).
      Record the latter as a **shortcut**.
- [ ] Fix the stale gap lines in the gateway's and topic service's `PLAN.md`: they say `ddl-auto=update`, but
      it is `create`, and the wipe-on-restart is intentional.
- [ ] **Gate:** a manual smoke check on a freshly started full stack. Create a topic with 3 categories and
      subscribe to it; the feed card shows 3 chips; trigger news generation; the delivered email shows
      "A · B · C" in the header and the preheader.

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

## Phase 2 — Tell the model which stories were already covered

### Why

A big story keeps coming back, reworded, for several days in a row on the same topic. The reader has
already seen it. Each day's prompt now lists the headlines this topic received in the last 7 days and
asks the model to **prefer other stories**. This is a **suggestion, not a rule**. The model may still
pick a covered story when there is a meaningful new development, and it then says what's new. On a
quiet day it may write a short "nothing new" note instead. That note is ordinary HTML, saved, emailed
and shown like any other day.

Only the topic service changes; the event, gateway, mail service and frontend are untouched. This phase
is independent of Phase 1 and can be done before or after it.

### Design

**Where the headlines come from.** Each item's headline is the `<strong>` text inside its `<li>`. The
headlines are parsed from the stored `TopicNews.data` HTML of past days, not stored separately. To make
parsing reliable, the prompt now **requires** that item shape (today it only lists the allowed tags).

**Output format (system prompt, always).** It replaces the current tag list in `SYSTEM_PROMPT_TEMPLATE`,
keeping the date, the "only what the web search returned" rule and "at most 5 items":

```
Output only an HTML fragment - no preamble, no Markdown. Put the items in one <ul>, and write every
item exactly as:
<li><strong>Headline</strong> One or two sentences of detail. <a href="source URL">Source name</a></li>
<em> is allowed inside the detail. If you find nothing new worth reporting, output only a short
<p> note saying so instead of the list.
```

**Already-covered block (system prompt, only when there are headlines).** It goes after the format
rules. The headlines are listed newest day first, one per line:

```
For context, these stories were already covered for this topic recently:
- Apple’s fall lineup
- U.S. stocks rallied on Friday, October 2.
Prefer stories that are not on this list. If you do include one of them, it should be because of a
meaningful new development - say what's new.
```

With no headlines (a new topic, or none parsed), the block is left out entirely.

**Window.** Rows of the same topic with `newsDate` in `[newsDate − N, newsDate)`, so today's own row is
excluded. **Any status counts** (`PENDING`, `SENT`, `FAILED`): the history is "what the model already
said", not "what was delivered". `N` is the new property
`app.news.covered-lookback-days=${NEWS_COVERED_LOOKBACK_DAYS:7}`. Setting it to `0` gives an empty
window, so no block is sent; this needs no extra code. The query is served by the existing
`(interest_topic_id, news_date)` unique index.

**Code shape.**
- `TopicNewsRepository.findDataByTopicInDateRange(UUID interestTopicId, LocalDate from, LocalDate to)`:
  a JPQL projection `SELECT tn.data FROM TopicNews tn WHERE tn.interestTopic.id = :id AND tn.newsDate
  >= :from AND tn.newsDate < :to ORDER BY tn.newsDate DESC`, returning `List<String>`.
- `utilities/NewsHeadlines` (a final class with a private constructor and a static
  `List<String> extract(String html)`, the same shape as `LogSanitizer`). It matches each `<li>`
  (attributes allowed) whose first child is `<strong>`, case-insensitively and across line breaks. It
  strips any tags nested in the headline, unescapes entities with `HtmlUtils.htmlUnescape`, collapses
  whitespace to single spaces, trims, and drops blanks. Input with no matching item (for example a
  "nothing new" `<p>`) gives an empty list. No new dependency; a regex is enough for the shape the
  prompt requires.
- `TopicNewsGenerationJob.generateNewsForTopic`: after the "already exists for today" check, load the
  window's `data`, extract the headlines of every row in order, and de-duplicate them while keeping
  the first occurrence (`LinkedHashSet`). Log the topic id and the headline **count** (not the text)
  at `info`, then call `generate`.
- `NewsGenerationService.generate(InterestTopic, LocalDate, List<String> coveredHeadlines)`: the
  implementation appends the block above to the system message when the list is non-empty.

### Test strategy

- **Deterministic only.** These tests don't check what the model does with the hint; that can't be
  asserted. They check the **request we send**: the WireMock stand-in for OpenRouter records the body,
  and the tests assert on it. Dates are fixed `LocalDate`s, never "today" relative to the clock, except
  where the job computes it itself.
- The job integration test makes the **only** generated request belong to the topic under test: every
  other topic in that test already has a row for today, so the job skips it. That way an assertion
  on "the request body" can't read another topic's prompt.
- Each step's gate: `signal_flow_interest_topic_service` `mvn verify` green **three runs in a row**.

### Steps

#### 1. Topic service: require the item format

- [ ] `SYSTEM_PROMPT_TEMPLATE`: the format rules from Design, including the "nothing new" `<p>` note.
- [ ] `NewsGenerationServiceImplIntegrationTest.should_carry_the_format_rules_the_date_and_the_topic_in_the_messages`:
      also assert that the system message contains `<li><strong>Headline</strong>` and the "nothing new"
      sentence.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 2. Topic service: extract headlines from stored news

- [ ] `utilities/NewsHeadlines.extract` as in Design.
- [ ] `NewsHeadlinesTest` (unit):
  - [ ] the required shape with 3 items → 3 headlines, in document order;
  - [ ] the real shape from production: a `<ul>` with newlines and indentation between items, and an
        `&amp;` and a `’` in a headline → unescaped text;
  - [ ] a `<strong>` holding a nested `<em>` → the tag is stripped and its text kept;
  - [ ] `<LI>`/`<STRONG>` in upper case, and `<li class="x">` → still matched;
  - [ ] a line break inside a headline → collapsed to a single space;
  - [ ] an `<li>` without a leading `<strong>`, a `<strong>` outside any `<li>`, and an empty
        `<strong></strong>` → ignored;
  - [ ] a "nothing new" `<p>` alone, a blank string → an empty list.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 3. Topic service: send the covered headlines with each generation

- [ ] `application.properties`: `app.news.covered-lookback-days=${NEWS_COVERED_LOOKBACK_DAYS:7}`, with a
      comment in the style of the other `app.news.*` entries (what it bounds; `0` turns the hint off).
- [ ] `TopicNewsRepository.findDataByTopicInDateRange` (Design).
- [ ] `NewsGenerationService`/`Impl`: the new `coveredHeadlines` parameter and the block from Design,
      left out when the list is empty.
- [ ] `TopicNewsGenerationJob`: the lookback `@Value`; load, extract, de-duplicate, log the count, pass
      the list to `generate`.
- [ ] Tests, `TopicNewsRepositoryIntegrationTest` (fixed dates, `to` = 2026-10-13, `from` = 2026-10-06):
  - [ ] rows on 10-06 and 10-12 are returned, newest first; rows on 10-05 and 10-13 are not;
  - [ ] a `SENT` row and a `FAILED` row inside the window are both returned;
  - [ ] another topic's row inside the window is not returned.
- [ ] Tests, `NewsGenerationServiceImplIntegrationTest`:
  - [ ] with two headlines → the system message contains the "already covered" sentence, each headline as
        a `- ` line in the given order, and the "Prefer stories" sentence;
  - [ ] with an empty list → the system message contains neither sentence;
  - [ ] a headline with quotes and a non-ASCII character reaches the request as valid, unchanged JSON
        (the same idea as `should_send_a_prompt_with_special_characters_as_valid_unchanged_json`).
- [ ] Tests, `TopicNewsGenerationJobTest` (unit): two past rows whose headlines overlap → `generate`
      receives each headline once, in first-seen order; no past rows → an empty list.
- [ ] Tests, `TopicNewsGenerationJobIntegrationTest`: topic A has rows for yesterday and 8 days ago, and
      topic B has rows for yesterday and today (so B is skipped). After one run, the single recorded
      request contains A's yesterday headline, and contains neither A's 8-day-old headline nor B's
      yesterday headline. Existing tests in this class follow the new signature.
- [ ] Update `TESTING.md` if it lists any of these scenarios.
- [ ] **Gate:** `signal_flow_interest_topic_service` `mvn verify` green 3× in a row.

#### 4. Docs and a manual check

- [ ] Topic service `DECISIONS.md` entries: headlines are parsed from the stored HTML rather than stored
      separately; the hint is a soft preference with no code-side filtering; any status counts toward
      the window; the "nothing new" note is ordinary news content.
- [ ] Topic service `CLAUDE.md`, "easy to break": the item shape in `SYSTEM_PROMPT_TEMPLATE` and
      `NewsHeadlines.extract` must change together. If the prompt's shape changes and the extractor
      doesn't, the hint silently becomes empty.
- [ ] Document `NEWS_COVERED_LOOKBACK_DAYS` wherever the service's other environment variables are
      documented (today only `application.properties`).
- [ ] **Gate:** a manual check on a running stack. For one topic, insert yesterday's row by hand with
      `psql`, with a recognisable headline in the required shape. Trigger generation, for example with
      `NEWS_CRON` set a minute ahead. Confirm the log line shows a non-zero headline count for that topic
      and that the new row's `data` still follows the required item shape. Whether the model actually
      avoids the story is observed over the following days; it isn't part of the gate.

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

## Phase 3 — An admin can delete a user account

### Why

The admin, the owner only, needs to remove an account permanently, for example a fake or unwanted
sign-up. There's no UI: it's one admin-only endpoint called with curl or Postman. The account and its
subscriptions are hard-deleted, and the person can later register again with the same email as a
fresh account. The person isn't notified.

Only the gateway changes, because accounts live only in its `users` table and the mail service stores
nothing per user.

### Design

**Endpoint.** `DELETE /api/v1/users?email=someone@example.com`, admin only.

| Case | Response |
| --- | --- |
| The email belongs to a `USER` account | **204**. The account and its subscriptions are deleted. |
| The email belongs to an `ADMIN` account, including the caller's own | **403**, and nothing is deleted. Admins can only be removed in the database. |
| No account has that email | **404** |
| `email` missing, blank or not an email | **400** |
| The caller isn't an admin | **403** (Spring Security, as for the other admin routes) |
| No token, or an invalid one | **401** |

- The email is lowercased before the lookup, the same way `AuthServiceImpl` does for login and register,
  so `Someone@Example.com` finds the account.
- On success, log `Admin '{adminId}' deleted user '{userId}'.` at `info`, using ids only, as the other
  log lines do. This log line is the only audit trail.

**Code shape.**
- `SecurityConfiguration`: add a `USERS_PATH` matcher (`/api/v1/users` and `/api/v1/users/**`, every
  method) to the `hasRole(ADMIN)` rule, so any verb added later is admin-only by default.
- New `controllers/UserController` (`@RequestMapping("/api/v1/users")`) with one `@DeleteMapping`
  taking `@RequestParam @NotBlank @Email String email` and the caller's `Jwt` for the log line.
- New `services/interfaces/UserService` + `implementations/UserServiceImpl` with
  `void deleteByEmail(String email, UUID adminId)`, `@Transactional`: find by email (404 if absent),
  refuse an `ADMIN` target (403), otherwise `userRepository.delete(user)`.
- New `exceptions/users/UserNotFoundException` (404) and `exceptions/users/UserNotDeletableException`
  (403), both mapped in `GlobalExceptionHandler`.

**Subscriptions go with the user, through the database.** `Subscription.user` gets
`@OnDelete(action = OnDeleteAction.CASCADE)`, the same approach the topic service uses for `TopicNews`.
Deleting the `users` row then removes its subscriptions in the same statement, and no
"delete subscriptions first" code is needed. There's no migration: `ddl-auto=create` recreates the
foreign key with `ON DELETE CASCADE` on the next start.

**A deleted user who is still logged in.** Their token keeps working until it expires (`JWT_TTL`, 1 h
by default), because requests deliberately don't read the user row. This is accepted (a shortcut, see
Accepted gaps). What happens in that window:
- Reads are harmless. The feed works, `GET /subscriptions` returns an empty list, and `/me` still
  answers from the token.
- **Subscribe** is the only action that needs the user row. Today it fails on the foreign key and the
  generic handler answers with a neutral 409. It now answers **401** with "Please log in again."
  `SubscriptionServiceImpl.lockCallersSubscriptions` already calls `userRepository.lockById`, which
  returns an empty `Optional` when the user is gone. It now does
  `.orElseThrow(AuthenticatedUserNotFoundException::new)`, a new exception in `exceptions/auth`, mapped
  to 401. No extra query is needed.
- **Unsubscribe** already answers 404, because the subscription is gone. That's fine.
- **The race between delete and subscribe** is safe. Subscribe locks the user row `FOR UPDATE`. If it
  runs first, the delete waits and then cascades over the new subscription. If the delete runs first,
  the lock finds no row and subscribe answers 401. The comment on the foreign-key branch in
  `subscribe` gets updated: the lock now guarantees the row exists, and the rethrow stays only for
  other integrity errors.

**Emails already queued** for the user are **not** cancelled. The notification outbox polls every 3 s
and deletes each row once it's sent, so a deletion would have to land within those seconds to matter.
See Accepted gaps.

### Test strategy

- **Deterministic only.** Unique emails per test (random suffix). After every refused case (403/404/400),
  check in the database that nothing was deleted. The race between delete and subscribe is covered by
  reasoning (the row lock), not by a timing-dependent test.
- Each step's gate: `signal_flow_api_gateway` `mvn verify` green **three runs in a row**.
- Every new or changed `*ControllerIntegrationTest` scenario updates the gateway's `TESTING.md` in the
  same change.

### Steps

#### 1. Gateway: subscriptions are deleted with their user

- [ ] `Subscription.user`: `@OnDelete(action = OnDeleteAction.CASCADE)`.
- [ ] Tests, `SubscriptionRepositoryIntegrationTest`: deleting a user with 2 subscriptions removes both. A
      second user's subscriptions to the same topics are untouched.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 2. Gateway: subscribe answers 401 when the caller's account is gone

- [ ] `exceptions/auth/AuthenticatedUserNotFoundException` → 401 "Please log in again." in
      `GlobalExceptionHandler`.
- [ ] `SubscriptionServiceImpl.lockCallersSubscriptions`: `lockById(userId).orElseThrow(...)`. Update the
      foreign-key comment in `subscribe` (see Design).
- [ ] Tests, `SubscriptionServiceImplTest`: `lockById` empty → throws, and `saveAndFlush` is never called.
      `should_rethrow_a_foreign_key_violation_without_relabelling_it` keeps passing with `lockById`
      stubbed present.
- [ ] Tests, `SubscriptionControllerIntegrationTest`: a valid token whose user row was deleted → `POST
      /subscriptions` returns 401, and no subscription row exists for that user id.
- [ ] Update `TESTING.md`.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 3. Gateway: `DELETE /api/v1/users?email=`

- [ ] `SecurityConfiguration`: the `USERS_PATH` admin matcher.
- [ ] `UserNotFoundException` (404) and `UserNotDeletableException` (403), mapped in `GlobalExceptionHandler`.
- [ ] `UserService`/`UserServiceImpl.deleteByEmail` and `UserController` as in Design, including the log line.
- [ ] Tests, new `UserControllerIntegrationTest`:
  - [ ] an admin deletes a `USER` with 2 subscriptions → 204. The user row and both subscriptions are
        gone, and another user's subscriptions are untouched;
  - [ ] the email given in mixed case → 204;
  - [ ] after the deletion, logging in with the old credentials → 401. Registering again with the same
        email → 201, as a new id with no subscriptions;
  - [ ] an `ADMIN` target, and the caller's own email → 403, and the row still exists;
  - [ ] an unknown email → 404;
  - [ ] a missing, blank or malformed `email` → 400;
  - [ ] a `USER` caller → 403, and the target still exists; no cookie → 401.
- [ ] Tests, `UserServiceImplTest` (unit): an admin target is refused before `delete` is called; an
      unknown email throws `UserNotFoundException`; the lookup uses the lowercased email.
- [ ] Update `TESTING.md` with a new `DELETE /api/v1/users` section.
- [ ] **Gate:** `signal_flow_api_gateway` `mvn verify` green 3× in a row.

#### 4. Docs and a manual check

- [ ] Gateway `DECISIONS.md` entries: hard delete with a database cascade; only `USER` accounts can be
      deleted; lookup by email; the 1-hour stale-token window accepted with subscribe → 401; queued
      emails not cancelled.
- [ ] Gateway `CLAUDE.md`: list the new admin route wherever routes and roles are described. Add an "easy
      to break" note: any new table that references `users` needs its own `@OnDelete(CASCADE)` or explicit
      cleanup, or deleting a user will fail on the foreign key.
- [ ] Record the stale-token window as a **shortcut** in the gateway's `CLAUDE.md` and `PLAN.md` gaps.
- [ ] **Gate:** a manual check on a running stack.
  1. Register a test user and subscribe it to 2 topics.
  2. As the admin, run `curl -X DELETE '…/api/v1/users?email=<test user>'` → 204.
  3. With the test user's still-valid cookie, subscribe → 401.
  4. Register again with the same email → it works, with no subscriptions.

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
