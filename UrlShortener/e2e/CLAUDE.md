## What this is

Playwright end-to-end tests exercising the whole system (browser UI → frontend →
backend → Mongo/Redis), fully containerized via `docker-compose.e2e.yml`. This is
separate from either sub-project's own tests: neither owns "does the whole system
work together."

## Running the project

Needs a running Docker daemon. `npm install`, then `npx playwright install --with-deps
chromium` (once), then `npm test`.

`npm test` runs global setup (`docker compose -p url-shortener-e2e -f
docker-compose.e2e.yml up -d --build --wait`), the spec files, then global teardown
(`docker compose ... down -v`). Set `E2E_SKIP_TEARDOWN=1` to leave the stack running
after a run for debugging; tear it down manually afterwards with `npm run stack:down`.

The stack runs on ports shifted from the normal dev defaults so it doesn't collide with
a locally running dev setup: mongo `27018`, redis `6380`, backend `8081`, frontend
`8082`. Tests navigate the frontend at `http://localhost:8082` and follow short links
directly at `http://localhost:8081/{code}` (the backend's own `BASE_URL`, matching how
the app actually returns short links to users — they aren't proxied through the
frontend).

The compose file has no named volumes — each run starts from an empty Mongo/Redis, and
`down -v` guarantees the next run does too.

To watch the browser instead of running headless: `npm run test:headed`. To also slow
each action down so it's actually watchable (headed alone still runs at full speed):
`npm run test:slow` — one worker, 1000ms of `slowMo` between actions. The delay is
configurable per run: `SLOWMO=1000 npx playwright test --headed --workers=1`.

## Writing tests

- One spec file per user-facing flow; keep specs independent — each test creates its
  own unique URL (`crypto.randomUUID()` query param) rather than relying on another
  test's data, since Mongo's storage is idempotent by URL.
- Prefer the app's existing DOM structure/classes over adding `data-testid` attributes
  — this project doesn't modify frontend source, only adds Docker/test infra around it.
- No CI wiring yet (no git remote to hook into).
