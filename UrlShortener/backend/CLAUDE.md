## What this is

A URL shortener. **MongoDB is the source of truth** — the original URL *is* the document `_id`, so
"does this URL already exist" is a primary-key lookup and `POST /api/v1/urls` is idempotent.
**Redis is a TTL cache on the redirect path only** — never authoritative; a Redis outage degrades to
Mongo and logs, it does not fail the request. `GET /{code}` returns a **302** (revocable; a 301 is
cached by browsers forever). Codes are Base62-encoded Snowflake ids. No auth, no stats, no expiry.

`PLAN.md` is the roadmap — the design, the step order and the acceptance gate for each step. Read it
before starting a step.

## Running the project

Needs **JDK 25+**; the pom targets release 25 and a newer JDK builds it fine.

The repo-root `../docker-compose.yml` runs the full project (mongo + redis + app + frontend). For local
backend dev, `docker compose up mongo redis` from the repo root brings up just the infra, then `mvn
spring-boot:run` serves the app on **:8080** against it. Other goals: `mvn clean install`, `mvn test`,
`mvn verify`.

Tests use **Testcontainers**, never embedded fakes — a running Docker daemon is a hard prerequisite.

Mongo must be reachable at startup: `spring.data.mongodb.auto-index-creation=true` builds the indexes
while the mapping context initialises, so every Spring context — the `contextLoads` smoke test included
— needs a live Mongo. Pin `mongo:8.2` or newer; 8.0 refuses to boot on Docker Desktop's kernel
(SERVER-121912).

Actuator's `/actuator/health` backs the `app` container's compose healthcheck. Swagger UI is at
`/swagger-ui/index.html`, the OpenAPI spec at `/v3/api-docs` (springdoc, no `OpenApiConfiguration` —
default title/spec).

## Spring Boot 4

The pom is on **4.2.0-M1**, a milestone. Boot 4 renamed the starters — `spring-boot-starter-webmvc`
(not `-web`) — and ships a separate `*-test` starter per module. Copy the artifactId pattern already in
`pom.xml` rather than reaching for the 3.x name, and verify any 3.x-era API before relying on it.

Careful with the two Mongo families: `spring-boot-starter-mongodb` is **driver-level only** (a
`MongoClient`, no `spring-data-mongodb`), while `spring-boot-starter-data-mongodb` adds Spring Data on
top and depends on it. Repositories, `@Document`, auditing and `@DataMongoTest` all need the `-data-`
one, which is what this pom uses. Test-slice annotations moved too — `@DataMongoTest` now lives in
`org.springframework.boot.data.mongodb.test.autoconfigure`.

## Writing code

**Before writing or editing any `.java` file, invoke the `java-code-style` skill first.** It carries the full style rules, conventions and examples. Applies to hand-written and AI-generated code alike.

**Before writing or editing any `*Test.java` file, invoke the `java-junit` skill first.** It carries the full test structure, naming and mocking conventions.

Hard rules — these hold whether or not the skill is loaded:

- Layered: `controller → service → repository`. Controllers never touch repositories directly. Services own business logic; controllers stay thin (validate input, delegate, shape response).
- Every service = an interface in `/services/interfaces` (`FooService`) + an implementation in `/services/implementations` (`FooServiceImpl`). Always, even with only one implementation.
- Constructor injection only: `private final` fields + `@RequiredArgsConstructor`. No `@Autowired` on fields, no setter injection. Ever.
- Test methods are snake_case; the default is `should_<behaviour>_when_<condition>`.
- Endpoints live under `/api/v1`. The one exception is the redirect `GET /{code}` — root-level on purpose, because that path *is* the public short link.
- If a paginated endpoint is ever added: take Spring's `Pageable` and return `PagedModel`, never `Page` — `PageImpl`'s JSON shape is not a stable contract.
- Adding, removing, or changing a scenario in `*ControllerIntegrationTest` (the HTTP-layer e2e suite) updates `TESTING.md` in the same change — it's hand-maintained and only stays trustworthy if edits to the tests carry an edit to the catalog.

Root-level packages, under `com.peter_gerdzhikov.url_shortener_backend`:
- `/configurations` — Spring configuration
- `/controllers` — the REST surface, plus the `@RestControllerAdvice`
- `/DTOs`
    - `/request` — inbound request bodies
    - `/response` — outbound payloads
- `/entities`
- `/exceptions`
- `/repositories`
- `/services`
    - `/interfaces` — service interfaces
    - `/implementations` — service implementations
- `/utilities`

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
- **Auto-run builds and tests only for significant changes.** Run `mvn clean install` or `mvn test` ONLY WHEN the change is behavior-affecting or structurally meaningful: new or removed features, modified business logic, API/contract changes, dependency/build changes, or broader refactors. DO **NOT** run them for comment-only edits, formatting-only changes, typo fixes, or similarly tiny updates. If tests fail due to my change — fix them, then report. If the cause is unclear — stop and report immediately.
- **Never report a suite that didn't run as passing.** Testcontainers tests fail at startup with no Docker daemon — if that happens, say so plainly; don't score a skipped or errored suite as green.
- **A step isn't done until its own tests are written and green.** No step starts on a red or missing test.
- **No silent assumptions.** If you hit an unknown, a missing detail, or multiple valid implementation paths, stop immediately and ask a focused open or choice question before continuing. Do not guess; this is a hard rule.
- **`DECISIONS.md`** (repo root) tracks non-obvious decisions that would be hard to re-derive from the code alone. Read it at the start of any non-trivial task; add to it when one is made. Division of labour: `PLAN.md` holds the design decided up front, `DECISIONS.md` holds calls made *during* implementation.

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
