## What this is

The email leg of the Twitter clone. Consumes the gateway's `user.confirmation-requested` and `user.followed`
topics (contracts in `../twitter_api_gateway/docs/EVENTS.md`), renders an HTML + text email from a classpath
template and sends it over SMTP. A Redis inbox dedupes, so a Kafka redelivery never sends the same email twice,
short of a crash between send and mark.

`PLAN.md` is the backlog and the design: two phases (confirmation email, then follow email), numbered steps with
gates. Read it before starting a task. Calls made *during* implementation go in `DECISIONS.md`. It is a port of
`../../SignalFlow/signal_flow_mail_service`: port from there rather than reinventing.

## Running the project

Needs **JDK 25+**; the pom targets release 25 and a newer JDK builds it fine.

`../docker-compose.infra.yml` (the Twitter root, one level up) runs this service's infra: `kafka`, `redis` and
`mailpit`. For local dev: copy `../.env.example` to `../.env` (`REDIS_PASSWORD` and `MAIL_FROM` are required),
run `docker compose -f docker-compose.infra.yml up -d kafka redis mailpit` from the Twitter root, export the env
(`set -a; . ../.env; set +a`), then `./mvnw spring-boot:run` serves the app on **:8082**. SMTP defaults to
Gmail; to send to Mailpit instead (UI on `localhost:8025`) set
`MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false`, so nothing real gets sent.
Other goals: `./mvnw clean install`, `./mvnw test`, `./mvnw verify`.

**Start the gateway first.** It owns the main topics and creates them with 3 partitions. Started earlier, this service
auto-creates them with 1, and its consumers see the other 2 only after a metadata refresh, about 5 minutes later, so
emails arrive that late. `../docker-compose.yml` already orders it (`depends_on: gateway`).

Tests use **Testcontainers only** - no embedded fakes for Kafka, Redis or SMTP. A running Docker daemon is a
hard prerequisite.

## Spring Boot 4

The pom is on **4.1.1**, same as the gateway and the tweet service. Boot 4 renamed the starters -
`spring-boot-starter-webmvc` (not `-web`) - and ships a separate `*-test` starter per module. Copy the
artifactId pattern already in `pom.xml` rather than reaching for the 3.x name, and verify any 3.x-era API
before relying on it.

## Writing code

**Before writing or editing any `.java` file, invoke the `java-code-style` skill first.** It carries the full style rules, conventions and examples. Applies to hand-written and AI-generated code alike.

**Before writing or editing any `*Test.java` file, invoke the `java-junit` skill first.** It carries the full test structure, naming and mocking conventions.

Hard rules - these hold whether or not the skill is loaded:

- Every service = an interface in `/services/interfaces` (`FooService`) + an implementation in `/services/implementations` (`FooServiceImpl`). Always, even with only one implementation. The renderers are pure and have no interface (`PLAN.md`, step 4).
- Constructor injection only: `private final` fields + `@RequiredArgsConstructor`, or an explicit constructor when `@Value` parameters are also needed. No `@Autowired` on fields, no setter injection. Ever.
- Test methods are snake_case; the default is `should_<behaviour>_when_<condition>`.
- Listeners stay thin - validate, key, render, claim, send and mark all live in the `...NotificationService`; the listener only delegates.
- No HTTP surface beyond actuator `health` - no `/api/v1`, no security starter. Nothing else consumes this service over HTTP.
- Anything that compares against "now" injects the `Clock` bean; never call `Instant.now()` in main code.
- Never log the payload, the address or the token - only `eventId`, user ids and the SMTP code.
- **No `Thread.sleep` in tests.** Async assertions use Awaitility with an explicit `atMost` and a short poll interval.
- **If you add, remove or change a scenario in any `*ListenerIntegrationTest` or `SmtpUnreachableIntegrationTest` → MUST update `docs/TESTING.md`** in the same change. It's hand-maintained, so it only stays trustworthy if edits to the tests carry an edit to the catalog.
- **Any edit to a file in `docs/` MUST also update its `Last updated: YYYY-MM-DD` line** (top of the file) to the date of the edit.

Root-level packages, under `com.peter_gerdzhikov.twitter_mail_service`:
- `/configurations` - Kafka consumers, error handling, DLT topics, Redis script bean, clock
- `/DTOs/event` - Kafka message payloads
- `/exceptions`
- `/listeners` - `@KafkaListener`s, thin, delegate to a service
- `/services`
    - `/interfaces` - service interfaces
    - `/implementations` - service implementations
- `/utilities` - pure, Spring-free helpers, if any appear

## Standing rules

- A behaviour change ships with its test change **in the same commit**.
- **Never delete, `@Disabled`, or weaken an assertion to make a build pass without explicit approval.** The one exception is the `@Disabled` test catalog of steps 2 and 10, which the plan enables step by step.
- Never report a suite that didn't run (for example, no Docker) as passing.
- **No step starts on a red or missing test, and each step ends green.** Nothing in phase 2 starts until phase 1's final gate passes and the gateway's `user.followed` change is committed.
- Tests are deterministic: no `Thread.sleep` (bounded Awaitility), unique ids and `<uuid>@example.com` recipients per test, time from the mutable test clock, negative async assertions use a same-key sentinel record, DLT assertions are filtered by the test's own key.
- A phase exits on `mvn verify` green **3x in a row**. A single flaky run is a failure to investigate, not to retry.

## Skills

Load skill bodies on demand (`/skill-name`). Descriptions live here — don't repeat them elsewhere.

| Area | Skills |
| --- | --- |
| Code style | `java-code-style` — mandatory before any `.java` edit (see Writing code) |
| Testing | `java-junit` — mandatory before editing test files (see Writing code) |
| Decisions | `grill-me` — open-ended/vague tasks, or design calls with real tradeoffs |
| Security | `exploit-hunter` — stack-agnostic attack-surface audit; run against finished features |

Done = re-check touched files against any loaded skill's rules.

---

## Working with me

- **No filler.** Skip "Great question!", "Here's a summary:", "I hope this helps." Get to the point.
- **Concision above all.** In all responses and plans — be extremely concise. Sacrifice grammar for
  brevity.
- **After completing a task:** one or two sentences max — what changed and anything worth flagging. No
  listing every file touched, no restating the request.
- **Auto-run builds and tests only for significant changes.** Run `./mvnw clean install` or `./mvnw test` ONLY
  WHEN the change is behavior-affecting or structurally meaningful: new or removed features, modified
  business logic, dependency/build changes, or broader refactors. DO **NOT** run them for comment-only
  edits, formatting-only changes, typo fixes, or similarly tiny updates. If tests fail due to my change —
  fix them, then report. If the cause is unclear — stop and report immediately.
- **Never report a suite that didn't run as passing.** Testcontainers tests fail at startup with no
  Docker daemon — if that happens, say so plainly; don't score a skipped or errored suite as green.
- **A step isn't done until its own tests are written and green.** No step starts on a red or missing
  test.
- **No silent assumptions.** If you hit an unknown, a missing detail, or multiple valid implementation
  paths, stop immediately and ask a focused open or choice question before continuing. Do not guess; this
  is a hard rule.
- **`DECISIONS.md`** (this directory) tracks non-obvious decisions that would be hard to re-derive from
  the code alone. Read it at the start of any non-trivial task; add to it when one is made. Division of
  labour: `PLAN.md` holds what's still open, `DECISIONS.md` holds calls already made *during*
  implementation.

### Autonomy

- **NEVER IMPLEMENT ANYTHING UNLESS I EXPLICITLY TELL YOU TO IMPLEMENT.** This overrides everything
  below. A question ("can we do X?", "how would Y work?", "I don't like Z") is a request for
  discussion/a proposal — NOT a license to write, edit, or delete code. Propose the approach and wait.
  Only act when I use an explicit imperative ("implement", "do it", "go ahead", "write it", "apply",
  "proceed").
- **Simple, well-scoped tasks** (fix this bug, add this field, refactor this method) — propose, then
  implement only on explicit go-ahead.
- **Complex tasks** (architecture, new patterns, meaningful design options) — describe approach in 2-3
  sentences, wait for approval, then implement. At the end of any plan, list unresolved questions
  concisely (grammar optional) — if any. Suggest a suitable skill if appropriate — see Skills section.
- **Stuck mid-task** — stop and ask. Don't guess on hard prerequisites.
- **Open-ended or vague task** — suggest `/grill-me` rather than interpreting unilaterally.

### Pushback and disagreement

- Push back **once**, bluntly — state the concern and why. If the user still wants to proceed their way,
  do it their way without further resistance.
- If asked to do something that violates a CLAUDE.md rule — flag it once, then comply if confirmed.
- Never add `// TODO`, `// !`, or `// ?` inline comments on your own initiative — flag concerns in the
  response instead. Exception: when you explicitly ask for one ("add a TODO here", "leave a `// !` on
  this").

### Scope

- Stay strictly in scope. If something adjacent is worth fixing or flagging, mention it in **one line at
  the end** — never silently expand the change.
- Research silently. Report findings and conclusions, not the exploration process.
