# SignalFlow Mail Service — what's left

Two phases have shipped and are green: **topic-news notification emails** (2026-09-24) and **backlog
cleanup** (2026-09-25 - Boot downgrade, `TopicNewsNotificationEventDTO` size caps, the `text/plain` email
alternative, Redis auth, DLT replay mode). Their design, step-by-step gates and interview records are no
longer carried here - read them from git history (`git log -p -- PLAN.md`). Calls made during
implementation live in `DECISIONS.md`, the e2e catalog in `TESTING.md`. Each phase's `exploit-hunter` audit
is not kept as a standalone file once every finding is fixed or logged - `DECISIONS.md` is the durable
record of what each one found and how it was resolved. Findings left open by decision appear under
*Accepted gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Designed, not yet started

Design settled via `/grill-me` (2026-09-25). Same rule as any other phase once picked up: **no step starts
on a red or missing test, each step ends green.** Neither is scheduled — each still waits on its own
trigger below.

### Gmail sending quota — quota-aware pause-and-resume

**Trigger:** the quota is actually hit (~500 recipients/day personal, ~2,000 Workspace). Explicitly **not**
in scope: switching off Gmail to a transactional provider (SES/SendGrid/etc.) — considered and deferred;
revisit only if a 24h pause turns out operationally painful at the observed fan-out volume.

Standing bug, bundle into step 1 regardless of when this starts: `classifyPermanence()` currently treats
Gmail's daily-quota bounce (`550 5.4.5`) as *permanent* — same bucket as a genuinely bad recipient. It's
actually transient; the window resets.

1. Detection: match SMTP enhanced status code `5.4.5` (+ bounce text) via a new
   `QuotaExceededMailDeliveryException`, carved out of the existing `≥500 = permanent` bucket in
   `TopicNewsMailServiceImpl`. Other `5xx` stays permanent, `4xx` stays transient. Unit tests: existing
   classification suite plus `5.4.5` → quota / other `550` → still permanent cases.
2. Kafka wiring: add the new exception to `notRetryableExceptions` — skip the ~4 min blocking retry (pointless
   against an hours-long quota window) and dead-letter immediately via the existing recoverer, same as
   permanent today but its own exception type.
3. Pause: on detection, pause the Kafka listener container
   (`KafkaListenerEndpointRegistry`/`MessageListenerContainer.pause()`) and write `resume-at = now +
   app.mail.quota.pause-duration` to a dedicated Redis key with a conditional (NX) write — first detection
   wins if several records hit quota near-simultaneously under
   `NOTIFICATION_REQUESTED_LISTENER_CONCURRENCY`.
4. Resume: `@Scheduled` poll of that Redis key; once elapsed, `container.resume()`, clear the key, log
   INFO. WARN-level log on pause (with resume-at). No metrics — matches this service's current
   observability level.
5. Restart safety: on boot, check Redis for an active pause window; if present, start the container
   already paused instead of consuming immediately.
6. Config: `app.mail.quota.pause-duration=${MAIL_QUOTA_PAUSE_DURATION:24h}`.
7. Tests: step 1's classification is a plain unit test. Steps 3-5's orchestration is a component test —
   real Testcontainers Redis + Kafka, a mocked `JavaMailSender` bean to deterministically trigger the quota
   exception (Mailpit can't be told to bounce with a specific code, and CLAUDE.md bars fake SMTP infra in
   integration tests). Doesn't touch `TopicNewsNotificationRequestedListenerIntegrationTest` or
   `TESTING.md`.
8. Recovery of the dead-lettered quota records stays the existing manual `dlt-replay` run mode — no new
   automation added.

### Per-topic unsubscribe link / `List-Unsubscribe` header

**Trigger:** approaching bulk-sender thresholds (~5k/day to Gmail/Yahoo). Scope is **per-topic**
unsubscribe (matches the existing subscription model), not a global opt-out.

Cross-repo split: `signal_flow_api_gateway` mints a signed, purpose-scoped unsubscribe URL (reusing its
existing JWT-signing infra) and serves a new *unauthenticated* one-click endpoint
(`List-Unsubscribe-Post: List-Unsubscribe=One-Click`) — that design is out of this file's scope and needs
its own `/grill-me` in that repo. This service only renders whatever URL it's given; it gains no new
signing/HMAC code and no new shared secret.

Steps here, once the gateway side lands:

1. Add a nullable `unsubscribeUrl` field to `TopicNewsNotificationEventDTO` (this service's hand-copied
   wire-contract half — keep in lockstep with the gateway's copy). Nullable, not `@NotBlank`: a rolling
   deploy can leave in-flight Kafka messages from an old gateway build without the field, and those must
   still send (without the link) rather than dead-letter on validation.
2. `TopicNewsEmailRenderer`: add an `UNSUBSCRIBE_URL` token. The renderer only does flat token
   substitution today (no conditional blocks), so the footer gets two fixed variants — with-link /
   without-link — and the renderer picks one based on whether the URL is present. Wire into both the
   `.html` and `.txt` templates' existing footer line.
3. `TopicNewsMailServiceImpl.send()`: set `List-Unsubscribe` (URL form only — no `mailto:`, since nothing
   in SignalFlow handles inbound email) plus `List-Unsubscribe-Post: List-Unsubscribe=One-Click` when the
   URL is present; omit both when absent.
4. Tests: `TopicNewsEmailRendererTest` (both footer variants + token rendering),
   `TopicNewsMailServiceImplTest` (headers set only when URL present), plus a
   `TopicNewsNotificationServiceImplTest` validate/claim case for the null-field path. Add present/absent
   scenarios to `TopicNewsNotificationRequestedListenerIntegrationTest` and update `TESTING.md` in the same
   change, per the hard rule.

---

## Accepted gaps — revisit when the named trigger lands

- **One duplicate email per crash/Redis blip between send and mark.** This is the at-least-once window, and
  it only shows up on a later Kafka redelivery. **Permanent:** inherent to SMTP plus at-least-once delivery,
  and can't be closed from inside this service.
- **Blocking retries hold a partition** for up to ~4 min in production (2s→60s cap, 8 retries) on a
  transient failure. That's the backpressure we want during an SMTP outage, but a single bad-but-transient
  record also stalls its partition. **Trigger:** measurable notification latency complaints →
  `@RetryableTopic` non-blocking retries. Its schedule then has to cover outages (think ~1h total), not
  blips, or an outage dead-letters the whole backlog.
- **Redis AOF stays `everysec`**, not `always`. Can lose ~1s of dedupe keys on a crash, which can only
  cause duplicates on redelivery, never lost delivery. **Permanent:** `always` would cost an fsync per
  write to close a window whose only failure mode is an already-accepted duplicate.
