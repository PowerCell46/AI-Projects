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
- Free ports: 5273, 8180, 8125, 5452 (below, each overridable). Stop the full stack and any IDE-run services first.

## Run

```sh
npm test            # build the images, start the stack, run all specs, tear the stack down
npm run test:fast   # skip the rebuild (`E2E_NO_BUILD=1`); use after the first run
npm run test:headed # same as test, with a visible browser
npm run stack:down  # `down -v` on the e2e project, for a manual cleanup
```

The first run builds the four Maven images and takes several minutes; `global-setup.ts` builds first (900 s
timeout, skipped by `E2E_NO_BUILD=1`) and then runs `up -d --wait` (300 s). Traces are kept for failures
(`trace: 'retain-on-failure'`); open them with `npx playwright show-report`. `E2E_SKIP_TEARDOWN=1` leaves the stack
running for inspection.

## Ports (host side)

| What | Default | Override |
| --- | --- | --- |
| Frontend dev server (Playwright `webServer`) | 5273 | `E2E_FRONTEND_PORT` |
| Gateway | 127.0.0.1:8180 | `E2E_GATEWAY_PORT` |
| Mailpit API/UI (SMTP is compose-internal) | 127.0.0.1:8125 | `E2E_MAILPIT_PORT` |
| Postgres (gateway) | 127.0.0.1:5452 | `E2E_POSTGRES_PORT` |

Export an override when the default is taken, for example `E2E_GATEWAY_PORT=8280 npm test`. The compose file, the
Playwright config and the fixtures all read the same variables, so one export keeps every URL consistent.

Everything else (timeline Postgres, Mongo, Kafka, MinIO, Redis, and the mail, tweet and timeline services) is
reachable inside the compose network only.
