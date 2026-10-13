# Deployment Steps

Deploying the Twitter stack to a remote server via Docker Compose + nginx (the `frontend` container terminates HTTPS with a Let's Encrypt certificate for `<SERVER_IP>.nip.io`).

Unlike the other projects there is no Caddy: nginx reads the certificate from `./certs`, so it has to be obtained once and renewed by hand (steps 4 and 8).

## 1. Copy the project to the server

From your local machine, inside the `Twitter` directory:

```sh
scp -r ./ 'root@178.104.31.249:/root/Twitter/'
```

## 2. SSH into the server

```sh
ssh root@178.104.31.249
```

## 3. Configure `.env`

The copied `.env` holds your local values. Replace them with production ones (start from `.env.example` if needed):

```sh
cd /root/Twitter
cp .env.example .env
nano .env
```

Make sure to set (compose stops and names whatever is missing):
- `PUBLIC_URL`: `https://<SERVER_IP>.nip.io` (no trailing slash; the links in emails are built from it)
- `JWT_SECRET` & `INTERNAL_API_SECRET`: 32+ bytes each
- `POSTGRES_USER` & `POSTGRES_PASSWORD`: gateway database
- `TIMELINE_DATASOURCE_USERNAME` & `TIMELINE_DATASOURCE_PASSWORD`: timeline database
- `REDIS_PASSWORD`: strong Redis password
- `MONGO_ROOT_PASSWORD`, `MONGO_APP_PASSWORD` (letters and digits only, `openssl rand -hex 24`) & `MONGO_KEYFILE_SECRET` (`openssl rand -base64 48 | tr -d '\n'`)
- `MINIO_ACCESS_KEY` & `MINIO_SECRET_KEY`: MinIO credentials (secret 8+ characters)
- `MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD` & `MAIL_FROM`: your SMTP server (add `MAIL_PORT` if it is not 587)
- `SEED_PASSWORD` and `SEED_PASSWORD_STILIYAN`, `_KRISTIAN`, `_GOSHO`, `_GABI`: 12-72 bytes with a lowercase letter, an uppercase letter and a digit, a different one for each. Not needed if `SEED_ENABLED=false`

## 4. Get the TLS certificate

Open ports 80 and 443 on the server. With the stack not running yet, port 80 is free for certbot:

```sh
apt install -y certbot
certbot certonly --standalone -d <SERVER_IP>.nip.io
```

Copy the certificate where nginx expects it. Both files must be readable by uid 101, the user nginx runs as:

```sh
mkdir -p certs
cp -L /etc/letsencrypt/live/<SERVER_IP>.nip.io/fullchain.pem certs/fullchain.pem
cp -L /etc/letsencrypt/live/<SERVER_IP>.nip.io/privkey.pem certs/privkey.pem
chown 101 certs/fullchain.pem certs/privkey.pem
```

## 5. Build and start the stack

The production overlay is required: it enables HTTPS, real SMTP, per-service secrets and hides the dev tools.

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d
```

## 6. Verify

Check container health:

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
```

Tail logs across all services:

```sh
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f
```

The application will be reachable at `https://<SERVER_IP>.nip.io` (port 80 redirects to HTTPS).

## 7. Local Development

Run the full stack locally (plain HTTP, Mailpit for mail) at `http://localhost:5173`:
```sh
docker compose up --build
```

If you only want to run the backing infrastructure (PostgreSQL, Kafka, MinIO, Mongo, Redis, Mailpit) while developing/debugging the Spring Boot services or frontend locally:
```sh
docker compose -f docker-compose.infra.yml up -d
```

## 8. Renew the certificate

Let's Encrypt certificates last 90 days. Port 80 is held by nginx, so stop it while certbot runs, then copy the new files and start it again:

```sh
cd /root/Twitter
docker compose -f docker-compose.yml -f docker-compose.prod.yml stop frontend
certbot renew
cp -L /etc/letsencrypt/live/<SERVER_IP>.nip.io/fullchain.pem certs/fullchain.pem
cp -L /etc/letsencrypt/live/<SERVER_IP>.nip.io/privkey.pem certs/privkey.pem
chown 101 certs/fullchain.pem certs/privkey.pem
docker compose -f docker-compose.yml -f docker-compose.prod.yml start frontend
```
