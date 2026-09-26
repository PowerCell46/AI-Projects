## What this is

Playwright end-to-end tests exercising the whole system (browser UI → frontend →
gateway → topics/mail services → Postgres/Kafka/Redis/Mailpit), fully containerized
via `docker-compose.e2e.yml`. This is separate from each sub-project's own tests:
no service owns "does the whole system work together."

## Running the project

Needs a running Docker daemon. `npm install`, then `npx playwright install
--with-deps chromium` (once), then `npm test`.

`npm test` runs global setup (`docker compose -p signal-flow-e2e -f
docker-compose.e2e.yml up -d --build --wait`), the spec files, then global teardown
(`docker compose ... down -v`). Use `npm run test:fast` (or `E2E_NO_BUILD=1`) to skip
rebuilding images when images are already built. Set `E2E_SKIP_TEARDOWN=1` to leave the
stack running after a run for debugging; tear it down manually afterwards with `npm run stack:down`.

The stack runs on ports shifted from the normal dev defaults so it doesn't collide
with a locally running dev setup: frontend `8090`, gateway `8091`, topics `8092`,
mail `8093`, gateway Postgres `5442`, topics Postgres `5443`, redis `6381`, mailpit
SMTP `1125` / API `8125`. Kafka has no host port — every app runs in a container
here, so it only listens on `kafka:9092` internally. Tests navigate the frontend at
`http://localhost:8090` and call the API through it (`/api/*` via nginx), the same
path the SPA uses.

The compose file has no named volumes — each run starts from empty Postgres
instances (both services also use `ddl-auto=create`), and `down -v` guarantees the
next run does too. The nightly crons (news generation, subscription
reconciliation, inbox cleanup) are overridden to Feb-29 dates so they never fire
mid-run; `OPENROUTER_API_KEY` is a dummy value the tests never exercise.

To watch the browser instead of running headless: `npm run test:headed`. To also slow
each action down so it's actually watchable (headed alone still runs at full speed):
`npm run test:slow` — one worker, 1000ms of `slowMo` between actions. The delay is
configurable per run: `SLOWMO=1000 npx playwright test --headed --workers=1`.

## Writing tests

- One spec file per user-facing flow family (`auth`, `subscriptions`, `feed`); keep
  specs independent — each test registers its own unique user (`newUser()`) and
  creates its own uniquely-named topics (`createTopicViaApi()`), since the database
  is shared across parallel workers.
- Feed counts and the topic list are global, not per-user — never assert exact
  totals. In global views (`ALL`, `NOT_SUBSCRIBED`) our topics can sit beyond page
  one, so page to exhaustion (`collectViewTitles`) and assert set inclusion, or
  stop at our card (`revealTopicCard`) before toggling. Only the per-user
  `SUBSCRIBED` view is small and complete enough for direct assertions.
- The UI is optimistic (card labels flip before the API commits; tab switches
  render the old list until the refetch lands) — never assert right after a bare
  click. Use the network-synchronized helpers: `clickSubscriptionToggle`,
  `selectFilterTab`, `loginAndWaitForFeed`. They fail loudly with the HTTP status
  when the app itself errors, instead of timing out downstream.
- Prerequisite state goes through the app's own API (see `tests/fixtures.ts`):
  register users via `POST /api/v1/auth/register`, create categories/topics as the
  seeded admin (`admin@e2e.local`, via `DatabaseLoader`) through the gateway's
  forwarded routes. No SQL seeding.
- Test credentials must satisfy the gateway's DTO rules: 8-72 chars with upper +
  lower + digit, TLD email (`TEST_PASSWORD` complies).
- Prefer the app's existing DOM structure/classes/roles over adding `data-testid`
  attributes — this project doesn't modify frontend source for tests.
- The suite runs with `reducedMotion: 'reduce'`, which the app honors by skipping
  its success-curtain animation — auth navigation is immediate, no curtain waits.
- No CI wiring yet.
