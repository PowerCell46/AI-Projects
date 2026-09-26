## Goal

Add a Playwright e2e suite at `SignalFlow/e2e/` mirroring `UrlShortener/e2e`, covering register, login, logout, subscribe/unsubscribe, plus feed filters/pagination and form validation-error cases — all against a fully containerized whole-system stack with fresh empty databases per run.

## Success Criteria

- `npm test` in `SignalFlow/e2e/` boots the whole stack, runs all specs, and tears everything down, green from a clean Docker state.
- Every spec passes on repeat runs with no manual cleanup (each run starts from empty DBs).
- Specs run in parallel (`fullyParallel`) without interfering with each other.
- The e2e stack can run while the normal dev stack is up (no host-port collisions).
- Root `docker-compose.yml` and dev workflow are untouched.

## Approach

Mirror the UrlShortener e2e shape exactly (config, global setup/teardown driving a dedicated compose file, one spec per flow family, DOM-preferring selectors), adapted for SignalFlow's auth + multi-service reality: empty DBs plus fixtures that build prerequisite state through the app's own API (register user, admin creates category/topic), never SQL.

## Key decisions

- **Full stack in `docker-compose.e2e.yml`** (postgres ×2, kafka, redis, mailpit, gateway, topics, mail, frontend), even though v1 asserts no emails. Rejected a minimal stack (no mail/redis/mailpit): it saves one Spring build but abandons "does the whole system work together" and forces recomposition when email assertions land.
- **Gateway service named `app`** in e2e compose, because `frontend/nginx.conf` hardcodes `proxy_pass http://app:8080/api/`.
- **Kafka advertises `kafka:9092` internally** (all apps run in containers here), unlike dev compose's `localhost:9094` for host-run apps.
- **Missing `Dockerfile`s for topics + mail** modeled on the gateway's (temurin 25, Maven-wrapper build, non-root user) — prerequisite for containerized e2e.
- **Readiness via Actuator**: add `spring-boot-starter-actuator` to gateway + topics poms (mail already has it) so `--wait` and healthchecks are honest. Gateway also gets a narrow `permitAll` for `GET /actuator/health`, since its `anyRequest().authenticated()` would otherwise 401 the container healthcheck. Rejected the zero-code alternative (healthcheck expecting 401 from `/me`) as brittle.
- **No static seeding**: `ddl-auto=create` + no named volumes + `down -v` gives empty DBs; admin comes from `ADMIN_EMAIL`/`ADMIN_PASSWORD` via the existing `DatabaseLoader`; fixtures create the rest via API with unique names/emails per test.
- **Nightly crons neutralized** in e2e env (Feb-29 schedules) so news generation / reconciliation / inbox cleanup never fire mid-run; `OPENROUTER_API_KEY` is a dummy value.
- **Chromium only**, `reducedMotion: 'reduce'` in Playwright config so `App.tsx` skips the ~2s success curtain deterministically (it honors `prefers-reduced-motion`).
- Test credentials comply with the DTO rules (8+ chars, upper + lower + digit, TLD email).

## Steps

1. **Container prerequisites.** Add `Dockerfile`s to `signal_flow_interest_topic_service/` and `signal_flow_mail_service/` (copy gateway's, adjust nothing except paths); add Actuator to gateway + topics poms with the `/actuator/health` permitAll on the gateway. Verify each image builds and boots.
2. **E2e harness.** Scaffold `SignalFlow/e2e/` (`package.json`, `tsconfig.json`, `playwright.config.ts`, `global-setup.ts`, `global-teardown.ts`, `docker-compose.e2e.yml`, `CLAUDE.md`) on the UrlShortener pattern: own project name, all host ports shifted off dev defaults (apps, both Postgres instances, kafka, redis, mailpit), hardcoded test-only secrets, `COOKIE_SECURE=false`.
3. **Fixtures.** Shared helpers: unique-user registration via API, admin login + category/topic creation via gateway routes returning IDs, unique naming everywhere for parallel safety.
4. **`auth.spec.ts`.** Register → lands on `/` authenticated; login; logout → `/login` with protected `/` redirecting; validation errors (short/weak password, bad email, duplicate email → 409) asserting inline UI errors.
5. **`subscriptions.spec.ts`.** Fixture-created topic; Subscribe toggle on `TopicCard` (`aria-pressed`, Subscribed label); unsubscribe back; subscribed counts update.
6. **`feed.spec.ts`.** ALL / SUBSCRIBED / NOT_SUBSCRIBED filter tabs with counts; pager loads more; empty-feed state with zero topics.
7. **Docs.** `e2e/CLAUDE.md` (run/debug/teardown, incl. `E2E_SKIP_TEARDOWN=1`, headed/slow scripts) mirroring UrlShortener's.

## Validation Plan

- `docker build` of each new/changed image succeeds.
- `npm test` green from clean state (highest-risk step: full Spring builds + Kafka startup + healthchecks on first run).
- Immediate second `npm test` green (proves fresh-DB idempotence, no residue).
- `E2E_SKIP_TEARDOWN=1 npm test` then manual `npm run stack:down`; `docker ps` shows nothing left.
- With dev infra running (`docker compose up` at root), `npm test` still passes (no port collisions).
- One headed spot-check (`npm run test:headed`) of the auth spec for UI-selector sanity.

## Risks / Open Questions

- First-run build time (three Spring Boot images on JDK 25) will be slow; subsequent runs are cached. No mitigation needed beyond noting it.
- Kafka single-node in compose is the flakiest piece; `--wait` with `depends_on: service_healthy` contains it, same as dev compose.
- Non-goals for v1: notification-email assertions via Mailpit, admin UI flows, CI wiring, any change to root `docker-compose.yml` or the dev workflow.
