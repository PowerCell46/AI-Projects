## What this is

The tweets service of the Twitter clone: create (with up to 4 images), read, edit the text, delete, and the
`tweet.created` / `tweet.deleted` events. It knows nothing about authentication: the gateway
(`../twitter_api_gateway`) proxies `/api/v1/tweets/**` to it and tells it who is calling through the
`X-User-Id` header. It looks users up at the gateway's `/internal/v1/users` (`UserLookupService`,
`INTERNAL_API_SECRET` as `X-Internal-Secret`, 32+ bytes, no default; `GATEWAY_INTERNAL_URL`) to name reply authors. Only the create route takes multipart; the body-size filter refuses it elsewhere (`415`). `PLAN.md` is the design, what is left open and the accepted gaps. Read it
before starting a task. Calls made *during* implementation go in `DECISIONS.md`. The events it produces are
documented in `docs/EVENTS.md`.

## Running the project

Needs **JDK 25+**; the pom targets release 25 and a newer JDK builds it fine.

`../docker-compose.infra.yml` (the Twitter root, one level up) runs Mongo (a single-node replica set), Kafka and
MinIO. For local dev: copy `../.env.example` to `../.env`, run `docker compose -f docker-compose.infra.yml up -d` from the Twitter root,
export the env (`set -a; . ../.env; set +a`), then `./mvnw spring-boot:run` serves the app on **:8081**.
Other goals: `./mvnw clean install`, `./mvnw test`, `./mvnw verify`.

Tests use **Testcontainers**, never embedded fakes - a running Docker daemon is a hard prerequisite.

## Spring Boot 4

The pom is on **4.1.1**. Boot 4 renamed the starters - `spring-boot-starter-webmvc` (not `-web`) - and ships a
separate `*-test` starter per module. Copy the artifactId pattern already in `pom.xml` rather than reaching for
the 3.x name, and verify any 3.x-era API before relying on it. Mongo properties live under `spring.mongodb.*`
(not `spring.data.mongodb.*`).

There is no Spring Security here. "Who am I" is the `X-User-Id` header, read by the `@CurrentUserId` argument
resolver; the service trusts it blindly, which is safe only while it is reachable through the gateway alone.

Anything marked "as the gateway" in `PLAN.md` is ported from `../twitter_api_gateway`. Port from there, don't
reinvent.

## Writing code

**Before writing or editing any `.java` file, invoke the `java-code-style` skill first.** It carries the full style rules, conventions and examples. Applies to hand-written and AI-generated code alike.

**Before writing or editing any `*Test.java` file, invoke the `java-junit` skill first.** It carries the full test structure, naming and mocking conventions.

Hard rules - these hold whether or not the skill is loaded:

- Layered: `controller -> service -> repository`. Controllers never touch repositories directly. Services own business logic; controllers stay thin (validate input, delegate, shape response).
- Every service = an interface in `/services/interfaces` (`FooService`) + an implementation in `/services/implementations` (`FooServiceImpl`). Always, even with only one implementation.
- Constructor injection only: `private final` fields + `@RequiredArgsConstructor`. No `@Autowired` on fields, no setter injection. Ever.
- Test methods are snake_case; the default is `should_<behaviour>_when_<condition>`.
- Endpoints live under `/api/v1`.
- Anything that compares against "now" injects the `Clock` bean; never call `Instant.now()` in main code. Timestamps are set explicitly from the clock and truncated to milliseconds (Mongo's precision), not by Spring Data auditing.
- **If you add, remove or change a scenario in any HTTP-layer e2e suite (`*ControllerIntegrationTest`) → MUST update `docs/TESTING.md`** in the same change. It's hand-maintained, so it only stays trustworthy if edits to the tests carry an edit to the catalog.
- **If you change anything Kafka-related** (topic, key, payload in `/DTOs/event`, when an event is emitted) **→ MUST update `docs/EVENTS.md`** if the contract changed.
- **Any edit to a file in `docs/` MUST also update its `Last updated: YYYY-MM-DD` line** (top of the file) to the date of the edit.

Root-level packages, under `com.peter_gerdzhikov.twitter_tweet_service`:
- `/configurations` - Spring configuration
- `/controllers` - the REST surface, plus the `@RestControllerAdvice`
- `/DTOs`
    - `/request` - inbound request bodies
    - `/response` - outbound payloads
    - `/event` - Kafka message payloads
    - `/client` - what another service answers to our calls
- `/documents` - Mongo documents (the counterpart of the gateway's `entities`)
- `/exceptions`
- `/jobs` - scheduled jobs
- `/repositories`
- `/services`
    - `/interfaces` - service interfaces
    - `/implementations` - service implementations
- `/utilities`

## Standing rules

- A behaviour change ships with its test change **in the same commit**.
- **Never delete, `@Disabled`, or weaken an assertion to make a build pass without explicit approval.**
- **No step starts on a red or missing test, and each step ends green with its own tests written.**
- Tests are deterministic: no `Thread.sleep` (Awaitility with a bounded timeout), time from the mutable test clock, scheduled jobs disabled in the test profile and invoked directly, a fresh random author UUID per test, Kafka assertions filtered by the test's own `tweetId`.
- A phase exits on `mvn verify` green **3× in a row**. A single flaky run is a failure to investigate, not to retry.

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
- **Concision above all.** In all responses and plans — be extremely concise. Sacrifice grammar for brevity.
- **After completing a task:** one or two sentences max — what changed and anything worth flagging. No listing every file touched, no restating the request.
- **Auto-run builds and tests only for significant changes.** Run `./mvnw clean install` or `./mvnw test` ONLY WHEN the change is behavior-affecting or structurally meaningful: new or removed features, modified business logic, API/contract changes, dependency/build changes, or broader refactors. DO **NOT** run them for comment-only edits, formatting-only changes, typo fixes, or similarly tiny updates. If tests fail due to my change — fix them, then report. If the cause is unclear — stop and report immediately.
- **Never report a suite that didn't run as passing.** Testcontainers tests fail at startup with no Docker daemon — if that happens, say so plainly; don't score a skipped or errored suite as green.
- **No silent assumptions.** If you hit an unknown, a missing detail, or multiple valid implementation paths, stop immediately and ask a focused open or choice question before continuing. Do not guess; this is a hard rule.
- **`DECISIONS.md`** (this directory) tracks non-obvious decisions that would be hard to re-derive from the code alone. Read it at the start of any non-trivial task; add to it when one is made. Division of labour: `PLAN.md` holds what's still open, `DECISIONS.md` holds calls already made *during* implementation.

### Autonomy

- **NEVER IMPLEMENT ANYTHING UNLESS I EXPLICITLY TELL YOU TO IMPLEMENT.** This overrides everything below. A question ("can we do X?", "how would Y work?", "I don't like Z") is a request for discussion/a proposal — NOT a license to write, edit, or delete code. Propose the approach and wait. Only act when I use an explicit imperative ("implement", "do it", "go ahead", "write it", "apply", "proceed").
- **Simple, well-scoped tasks** (fix this bug, add this field, refactor this method) — propose, then implement only on explicit go-ahead.
- **Complex tasks** (architecture, new patterns, meaningful design options) — describe approach in 2-3 sentences, wait for approval, then implement. At the end of any plan, list unresolved questions concisely (grammar optional) — if any. Suggest a suitable skill if appropriate — see Skills section.
- **Stuck mid-task** — stop and ask. Don't guess on hard prerequisites.
- **Open-ended or vague task** — suggest `/grill-me` rather than interpreting unilaterally.

### Pushback and disagreement

- Push back **once**, bluntly — state the concern and why. If the user still wants to proceed their way, do it their way without further resistance.
- If asked to do something that violates a CLAUDE.md rule — flag it once, then comply if confirmed.
- Never add `// TODO`, `// !`, or `// ?` inline comments on your own initiative — flag concerns in the response instead. Exception: when you explicitly ask for one ("add a TODO here", "leave a `// !` on this").

### Scope

- Stay strictly in scope. If something adjacent is worth fixing or flagging, mention it in **one line at the end** — never silently expand the change.
- Research silently. Report findings and conclusions, not the exploration process.
