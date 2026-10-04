# Twitter Mail Service: plan

Sends Twitter's emails. It consumes the gateway's `user.confirmation-requested` and `user.followed` topics
(contracts in `../twitter_api_gateway/EVENTS.md`) and sends each email over SMTP, deduped in Redis. It is a port of
`../../SignalFlow/signal_flow_mail_service`; port from there rather than reinventing. Designed via `/grill-me` on
2026-10-01 (Q1–Q12 below).

**Status: both phases are built** — 1, the confirmation email (steps 1–9, 2026-10-01), and 2, the follow email
(steps 10–13, 2026-10-02). What is left is under **Left open**. The full original plan, with per-step gates and
scenario lists, was trimmed on 2026-10-02; the scenarios live on in `TESTING.md`.

**Hard rule for any further step:** no step starts on a red or missing test, and each step ends green. A phase exits
on `mvn verify` green 3× in a row.

---

## Design in short

- **Stack:** Java 25, Spring Boot 4.1.1, package `com.peter_gerdzhikov.twitter_mail_service`, port **8082**.
  webmvc and actuator (health only), kafka, mail, `angus-mail`, data-redis, validation, Lombok. No `/api/v1`, no
  security starter. SignalFlow's OWASP sanitizer and jsoup are left out (nothing here renders untrusted HTML).
- **Infra (root `../docker-compose.yml`):** `redis` (`--appendonly yes`, required `REDIS_PASSWORD`) and `mailpit`
  (SMTP `127.0.0.1:1025`, UI `127.0.0.1:8025`), both bound to `127.0.0.1`.
- **SMTP defaults are Gmail (Q9):** `smtp.gmail.com:587`, auth and STARTTLS on, 10s timeouts. `MAIL_FROM` has no
  default, so the app refuses to start without it. To send to Mailpit, point `MAIL_HOST/PORT/SMTP_AUTH/SMTP_STARTTLS`
  at it by hand.
- **The pipeline is written once and shared by both emails (Q11):**
  - `MailInboxService`: the Redis note that an email was sent. `claim(key, token)` → `CLAIMED`/`ALREADY_SENT`/`HELD`
    (`SET NX` of `PROCESSING:<token>`, claim TTL 5m); `markSent(key, ttl)`; `release(key, token)` is a
    compare-and-delete Lua script.
  - `MailEventValidationService` and `MailDispatchService` (+ `OutgoingMail`): bean validation, and claim → send →
    mark sent, releasing the claim and rethrowing on a failed send. A failed `markSent` is logged only. Extracted
    from the confirmation service in step 12 (`DECISIONS.md`).
  - `MailDeliveryService`: builds the MIME message (`text/plain` + `text/html`, UTF-8) and classifies failures.
    `MailParseException`, `MailPreparationException`, `AddressException` and SMTP ≥ 500 are permanent; the rest are
    transient. It never logs the exception or the address.
  - `EmailTemplate`: a classpath `.html` + `.txt` pair, single-pass `{{TOKEN}}` substitution, a startup check that
    each template uses exactly its declared tokens. HTML values are escaped; text values lose line breaks.
  - Kafka: one typed consumer factory and one error handler per topic (the gateway sends no type headers).
    Blocking exponential backoff (2s → 60s, 8 retries; the test profile uses 100ms → 200ms, 3), then a
    `DeadLetterPublishingRecoverer` with two templates keyed by value type (`byte[]` and the DTO). Invalid events and
    permanent failures are not retried. This service declares only the DLT topics, 3 partitions each.
  - Per email: a hand-copied validated DTO, a thin listener, a `…NotificationService`, and a template pair plus a
    pure renderer (no interface).
- **Confirmation email:** key `mail:confirmation:<eventId>`, SENT for 7d (`CONFIRMATION_MAIL_SENT_TTL`). Stale skip
  (Q3): if the injected `Clock` is at or after `expiresAt`, send nothing, log at INFO and return. Subject "Confirm
  your email"; button, raw URL, the expiry in `MAIL_ZONE` (Q6) and "If you didn't sign up, ignore this email."
  `confirmationUrl` is trusted as sent (Q7).
- **Follow email:** key `mail:followed:<followerId>:<followeeId>`, SENT for a 24h window (`FOLLOW_MAIL_WINDOW`, Q2;
  must be positive, checked at startup). Subject "`<follower>` followed you"; "Hi `<followee>`", "`<follower>`
  (@`<follower>`) is now following you" and an **Open Twitter** button to `APP_BASE_URL` + `/feed` (Q4).
- **Look (Q5):** fully dark like the SPA: table layout, inline CSS and `bgcolor` for Outlook, `color-scheme: dark`,
  the colours of `frontend/src/index.css`, a plain-text part too.
- **DTO validation** (`InvalidMailEventException`, dead-lettered without retries) mirrors the gateway: ids and
  instants `@NotNull`; emails `@NotBlank @Email @Size(max = 254)` plus the gateway's `@Pattern`; usernames
  `^[A-Za-z0-9_]{3,15}$`; `confirmationUrl` `@NotBlank @Size(max = 2048)`.
- **Logging:** never the payload, the address or the token; only `eventId`, user ids, field names and the SMTP code.
- **Config:** topic names read the gateway's env vars (`USER_CONFIRMATION_REQUESTED_TOPIC_NAME`,
  `USER_FOLLOWED_TOPIC_NAME`); durations are `Duration` strings. Consumer group `twitter-mail-service`,
  `auto-offset-reset=earliest`, listener concurrency 3.

## Test strategy

Testcontainers only (Kafka, Redis, Mailpit with `--smtp-allowed-recipients '.*@example\.com$'`, so a real `550` is
an address outside that domain), a spied `JavaMailSender` to count attempts, and a mutable clock. Docker is
required. Tests are deterministic: bounded Awaitility and no `Thread.sleep`; unique ids and `<uuid>@example.com`
recipients; a negative assertion uses a sentinel record under the same Kafka key; DLT assertions filter by the
test's own key; TTLs are read with Redis `TTL`. `TESTING.md` lists every e2e scenario and is kept by hand.

## Steps built

| # | Step | Done |
| --- | --- | --- |
| 1 | Skeleton, infra (redis, mailpit), test support | 2026-10-01 |
| 2 | Confirmation test catalog | 2026-10-01 |
| 3 | Mail inbox (`MailInboxService`, release Lua script) | 2026-10-01 |
| 4 | Templates (`EmailTemplate`, confirmation pair, `ConfirmationEmailRenderer`) | 2026-10-01 |
| 5 | Delivery (`MailDeliveryService`) | 2026-10-01 |
| 6 | Confirmation pipeline | 2026-10-01 |
| 7 | Failures and DLT | 2026-10-01 |
| 8 | Playwright e2e on real emails (Q10, Q12) | 2026-10-01 |
| 9 | Phase 1 hardening (`SECURITY-AUDITS.md`) | 2026-10-01 |
| 10 | Follow test catalog | 2026-10-02 |
| 11 | Follow template and `FollowEmailRenderer` | 2026-10-02 |
| 12 | Follow pipeline and Kafka (the Mailpit look check was waived) | 2026-10-02 |
| 13 | Phase 2 hardening (`SECURITY-AUDITS.md`) | 2026-10-02 |

---

## Left open

Not plan steps; things the build left behind. Each needs a decision or a small job.

1. **The follow email's look is unapproved.** Step 12's gate asked for a look in Mailpit and you waived it. The tests
   check the content, not the rendering. To check: run `docker compose up -d kafka redis mailpit`, start the service
   with `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false`, make one user follow
   another, and open `localhost:8025`.
2. **Start order leaves 1-partition topics (decision pending).** Started before the gateway, the mail service's
   listeners auto-create both topics with 1 partition; the gateway then grows them to 3, and the consumers only see
   the new partitions after their metadata refresh, about 5 minutes. A confirmation email was delayed that long on
   2026-10-02. Options: start the gateway first and document it (recommended, no code); declare the two main topics
   here too (contradicts "declares only the DLT topics" and duplicates the gateway's partition config); set a shorter
   `metadata.max.age.ms` (only shrinks the delay).
3. **No Playwright test for the follow email.** The e2e suite covers only the confirmation link, and phase 2 exited
   on `mvn verify`, so the follow path has never run through gateway → Kafka → mail service → Mailpit in compose.
   Add one spec to `../e2e`: two users, a follow through the API, and the "`<name>` followed you" email read from
   Mailpit by recipient.
4. **Stale gateway docs.** `../twitter_api_gateway/CLAUDE.md` ("the follow email is phase 2") and
   `../twitter_api_gateway/PLAN.md` ("Left: the mail service's follow email") should say it is built.
5. **Nothing from this work is committed.** `twitter_mail_service/` is untracked, and `../.env.example`,
   `../PLAN.md`, `../CLAUDE.md`, `../docker-compose.yml`, `../e2e/**` and the frontend docs are modified. Only the
   gateway's `user.followed` change is committed (`85019be`).
6. **`APP_BASE_URL` with a trailing slash gives `//feed`.** `.env.example` says not to end it with a slash;
   nothing enforces it. Decide: strip it in `FollowEmailRenderer`, or leave it documented.
7. **`CONFIRMATION_MAIL_SENT_TTL` is not checked.** The follow window now fails startup when zero or negative (`SECURITY-AUDITS.md`); the confirmation TTL has the same hole.

## Out of scope

- Other emails: tweet, reply or mention notifications, digests, welcome, password reset.
- Unsubscribe and notification settings, and an HTTP API beyond health.
- A DLT replay tool (Q8), user time zones, i18n, and a light email theme.
- Gmail quota handling.

## Accepted gaps — revisit when the named trigger lands

- **One duplicate email per crash or Redis blip between send and mark.** The at-least-once window. **Permanent.**
- **Blocking retries hold a partition** for up to about 4 minutes while SMTP is down. **Trigger:** latency
  complaints → `@RetryableTopic`, with a schedule that covers outages. One recipient can cause it, not only an
  outage: an account on a domain that always answers `4xx` holds the partition its `followeeId` hashes to for about
  242s per follow event (`SECURITY-AUDITS.md`, phase 2).
- **Redis AOF is `everysec`**, so a crash can lose about 1s of dedupe keys, which can only cause duplicates.
  **Permanent.**
- **No DLT replay tool (Q8).** Dead-lettered records are only logged. **Trigger:** the first dead-lettered record
  someone wants delivered → port SignalFlow's `dlt-replay` profile.
- **`confirmationUrl`, `followeeEmail` and both usernames are trusted as sent (Q7).** A writer to Kafka could make
  the account send any link or email any address, and can read the follow graph and recipient addresses from
  `user.followed` and its DLT (`SECURITY-AUDITS.md`). **Trigger:** Kafka reachable beyond a private network → a prefix check
  against `CONFIRMATION_LINK_BASE_URL`, plus Kafka TLS/SASL (the gateway's existing gap).
- **Gmail sending quota** (about 500 recipients a day), shared by both emails. **Trigger:** it is hit → SignalFlow's
  quota pause-and-resume, including the `5.4.5`-is-transient fix.
- **No volume cap on follow emails.** The 24h window stops repeats for one pair, not fan-out: one account that
  follows N users, or N accounts that follow one user, send N emails (`SECURITY-AUDITS.md`). The cap belongs on the gateway's
  follow endpoint (its "no rate limiting" gap). **Trigger:** abuse, or the Gmail quota is hit.
- **A failed send leaves no window.** A recipient whose mailbox bounces is retried on every new follow event, so a
  follow/unfollow loop costs one SMTP attempt, one bounce and one DLT record each (`SECURITY-AUDITS.md`). **Trigger:** bounce
  complaints or a sender-reputation hit → a short negative window after a permanent failure.
- **No unsubscribe or opt-out for follow emails**, and no `List-Unsubscribe`. **Trigger:** a complaint, or
  approaching the bulk-sender thresholds → a notification setting in the gateway plus a signed one-click link.
- **A follow email can arrive after an unfollow**, because the event was already queued. **Trigger:** a complaint.
- **Expiry is shown in one fixed zone (Q6).** **Trigger:** users outside that zone → a time zone on the account,
  carried in the event.
- **The follow email links to `/feed`, not to the follower's profile (Q4).** **Trigger:** the SPA gets a profile page.
- **A fully dark HTML email can be inverted or flattened** by some clients' dark mode (Outlook, the Gmail app).
  **Trigger:** rendering complaints → a light variant.
- **Dev compose has fixed credentials and plaintext SMTP to Mailpit** (bound to `127.0.0.1`), the `Dockerfile` uses
  floating `eclipse-temurin` tags, and the e2e Postgres is published on every interface. **Trigger:** any shared
  environment → generated secrets, pinned digests, no published e2e Postgres port.
- **`/actuator/health` shows details** (`show-details=always`: working directory, free disk, Redis version) on port
  8082, which no compose file publishes. **Trigger:** reachable from outside the internal network →
  `when-authorized` with a security starter, or bind the management port to loopback.
