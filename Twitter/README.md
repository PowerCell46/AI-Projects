# Twitter

A Twitter-style app: a React frontend (`frontend/`) and four Spring Boot services (`twitter_api_gateway`,
`twitter_tweet_service`, `twitter_timeline_service`, `twitter_mail_service`). This file explains how to start the
backend with Docker Compose. Each service has its own `CLAUDE.md` with details.

## Compose files

| File | What it starts | Use it when |
| --- | --- | --- |
| `docker-compose.infra.yml` | Infra only: 2 Postgres, Kafka + Kafka UI, MinIO, Mongo, Redis, Mailpit | You run the services from the IDE or `./mvnw` and need what they talk to |
| `docker-compose.yml` | The infra above (via `include`) plus the gateway, tweet, timeline and mail services, built from their Dockerfiles | You want the whole backend in containers |

Needs Docker with Compose 2.20 or newer (`include` support). Run every command from this folder.

## Setup (once)

```sh
cp .env.example .env
```

Compose refuses to start while a required value is empty. Fill in at least:

- `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` (the gateway's Postgres)
- `TIMELINE_DATASOURCE_USERNAME`, `TIMELINE_DATASOURCE_PASSWORD` (the timeline service's Postgres; `TIMELINE_POSTGRES_DB` is optional)
- `REDIS_PASSWORD`
- `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` (the secret needs 8+ characters)
- `JWT_SECRET` and `INTERNAL_API_SECRET` (32+ bytes each; the gateway and timeline service refuse to start without them)
- `MAIL_FROM` (the mail service refuses to start without it)

The remaining lines in `.env.example` are optional and fall back to the defaults in each service's
`application.properties`. Leave an optional line out of `.env` entirely rather than setting it empty, because an
empty value overrides the default.

## Infra only (local development)

```sh
docker compose -f docker-compose.infra.yml up -d --wait   # start and wait until healthy
docker compose -f docker-compose.infra.yml ps             # check status
docker compose -f docker-compose.infra.yml down           # stop, keep the data
docker compose -f docker-compose.infra.yml down -v        # stop and delete the data
```

To start only some of them, name the services, e.g. `up -d kafka redis mailpit`.

Everything is published on `127.0.0.1` only:

| Service | Host port |
| --- | --- |
| Postgres (gateway) | 5432 |
| Postgres (timeline) | 5433 |
| Kafka | 9094 |
| Kafka UI | 7777 |
| MinIO API / console | 9000 / 9001 |
| Mongo | 27017 |
| Redis | 6379 |
| Mailpit SMTP / web UI | 1025 / 8025 |

Then run each service from its folder with the env exported:

```sh
cd twitter_api_gateway          # likewise twitter_tweet_service, twitter_timeline_service, twitter_mail_service
set -a; . ../.env; set +a
./mvnw spring-boot:run
```

The ports are 8080 (gateway), 8081 (tweet), 8082 (mail) and 8083 (timeline). Run the gateway against this Postgres by also
setting `API_GATEWAY_DATASOURCE_USERNAME` and `API_GATEWAY_DATASOURCE_PASSWORD` in `.env` to the same values as
`POSTGRES_USER` and `POSTGRES_PASSWORD`. To send mail to Mailpit instead of Gmail, set `MAIL_HOST=localhost`, `MAIL_PORT=1025`,
`MAIL_SMTP_AUTH=false` and `MAIL_SMTP_STARTTLS=false`.

## Full stack

```sh
docker compose up -d --build   # build the four images, start everything
docker compose ps
docker compose logs -f gateway # follow one service
docker compose down            # stop, keep the data
docker compose down -v         # stop and delete the data
```

The first build downloads the Maven dependencies and takes a few minutes. Only the gateway is published, on
`http://localhost:8080`; the other services are reachable inside the compose network only. The infra ports above
stay published too.

- Containers reach each other by service name (`kafka:9092`, `postgres-api-gateway`, `mongo`, ...). The `environment` block of
  each service in `docker-compose.yml` overrides the matching values from `.env`, so `localhost` values there do
  not matter.
- The services read the rest of their settings from `.env` (`env_file`).
- Mail goes to Mailpit, so read the confirmation email at `http://localhost:8025`.

Do not run the full stack and services started from the IDE at the same time: they use the same ports.

## Frontend

```sh
cd frontend
npm install
npm run dev   # http://localhost:5173, proxies /api to the gateway on :8080
```

Start the gateway first, either from the full stack or from the IDE. See `frontend/CLAUDE.md` for tests and the phone
setup.

## End-to-end tests

`e2e/` has its own `docker-compose.e2e.yml` with throw-away credentials and different ports, so it does not touch
the stacks above. Run it with `npm test` in `e2e/`.

## Troubleshooting

- `... must be set` on `up`: the named variable is empty or missing in `.env`.
- A container exits straight after starting: run `docker compose logs <service>`. A missing `JWT_SECRET`,
  `INTERNAL_API_SECRET`, `MINIO_*` or `MAIL_FROM` is the usual cause.
- `port is already allocated`: another process or compose project already uses that port. Stop it, or stop the
  full stack if you are running services from the IDE.
- Changed a Dockerfile or service code: `docker compose up -d --build`.
