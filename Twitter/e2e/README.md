# End-to-end tests

Playwright (desktop Chromium only) against a throw-away stack: `docker-compose.e2e.yml` starts Postgres, Mongo,
Kafka, MinIO, Redis, Mailpit and the four services under the `twitter-e2e` Compose project, and Playwright serves
the frontend itself (`npm run dev` in `../frontend`). The specs register their own users in an empty database
(`SEED_ENABLED=false`); nothing here touches the dev or full stacks.

## Prerequisites

- Docker with Compose 2.20 or newer.
- Node 20.19 or later.
- `npm install` here and in `../frontend` (the dev server needs the frontend's dependencies).
- `npx playwright install chromium` once (add `--with-deps` on Linux).
- Free ports: 5273, 5274, 8180, 8125, 5452 (below, each overridable). Stop the full stack and any IDE-run services first.

## Run

```sh
npm test            # build images, start stack, run all specs headless, tear stack down
npm run test:fast   # skip image rebuild (`E2E_NO_BUILD=1`); use on subsequent runs
npm run test:headed # run with a visible browser
npm run test:slow   # visible browser, 1 worker, 1000ms delay between actions (watchable)
npm run stack:down  # `down -v` on the e2e project, for manual cleanup
```

### Fast vs. slow execution
- **Fast run (`npm run test:fast`)**: When service images are already built, setting `E2E_NO_BUILD=1` skips the multi-minute Maven container rebuilds and starts straight into `up -d --wait`.
- **Visible browser (`npm run test:headed`)**: Runs all specs in a visible Chromium browser window at normal execution speed.
- **Slow motion / watchable (`npm run test:slow`)**: Runs tests in a visible browser with a single worker (`--workers=1`) and a 1000ms delay (`SLOWMO=1000`) between actions so each step can be watched visually. You can adjust the delay per run, e.g. `SLOWMO=500 npx playwright test --headed --workers=1`.
- **Debugging & inspection (`E2E_SKIP_TEARDOWN=1`)**: Leaves the stack running after tests finish so you can inspect state or rerun single specs against the active environment. Clean up manually with `npm run stack:down`.

The first run builds the four Maven images and takes several minutes; `global-setup.ts` builds first (900 s
timeout, skipped by `E2E_NO_BUILD=1`) and then runs `up -d --wait` (300 s). Traces are kept for failures
(`trace: 'retain-on-failure'`); open them with `npx playwright show-report`. `E2E_SKIP_TEARDOWN=1` leaves the stack
running for inspection.

After `up --wait`, `global-setup` runs the container smoke check (`container-smoke.ts`) against the production
frontend image: the SPA serves, a deep link falls back to `index.html`, `/api` proxies to the gateway (401 as JSON),
a 2 MB body passes nginx's upload cap (the gateway's own 8 KB cap rejects it as JSON), and the security headers are
present. A failure tears the stack down like a failed start.

## Ports (host side)

| What | Default | Override |
| --- | --- | --- |
| Frontend dev server (Playwright `webServer`) | 5273 | `E2E_FRONTEND_PORT` |
| Frontend container (nginx smoke check) | 127.0.0.1:5274 | `E2E_FRONTEND_CONTAINER_PORT` |
| Gateway | 127.0.0.1:8180 | `E2E_GATEWAY_PORT` |
| Mailpit API/UI (SMTP is compose-internal) | 127.0.0.1:8125 | `E2E_MAILPIT_PORT` |
| Postgres (gateway) | 127.0.0.1:5452 | `E2E_POSTGRES_PORT` |

Export an override when the default is taken, for example `E2E_GATEWAY_PORT=8280 npm test`. The compose file, the
Playwright config and the fixtures all read the same variables, so one export keeps every URL consistent.

Everything else (timeline Postgres, Mongo, Kafka, MinIO, Redis, and the mail, tweet and timeline services) is
reachable inside the compose network only.
