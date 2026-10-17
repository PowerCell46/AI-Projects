# Backlog

What is still open for the mail service: a standing bug, two designed-but-unstarted features, and gaps accepted
on purpose. Carried over from the closed-out `PLAN.md` (`git log -p -- PLAN.md`). Shortcuts that are easy to break
are in `CLAUDE.md`.

Anything picked up from here: **no step starts on a red or missing test, and each step ends green.**

## Standing bug

- **`classifyPermanence()` treats Gmail's daily-quota bounce (`550 5.4.5`) as permanent**, the same bucket as a
  genuinely bad recipient. It is transient: the window resets. Fix it in step 1 of the quota work below, whenever
  that starts.

## Designed, not yet started (design settled via `/grill-me`, 2026-09-25)

### Gmail sending quota: quota-aware pause and resume

**Trigger:** the quota is actually hit (about 500 recipients/day personal, about 2,000 Workspace). Out of scope:
switching from Gmail to a transactional provider (SES, SendGrid); revisit only if a 24 h pause proves painful at
the observed fan-out volume.

1. **Detection.** Match SMTP enhanced status `5.4.5` (plus bounce text) with a new
   `QuotaExceededMailDeliveryException`, carved out of the `>=500 = permanent` bucket in
   `TopicNewsMailServiceImpl`. Other `5xx` stays permanent, `4xx` stays transient. Unit tests: the existing
   classification suite plus `5.4.5` as quota and another `550` as still permanent.
2. **Kafka wiring.** Add the exception to `notRetryableExceptions`, so the roughly 4 minute blocking retry is
   skipped (pointless against an hours-long window) and the record is dead-lettered at once.
3. **Pause.** On detection, pause the listener container (`KafkaListenerEndpointRegistry` /
   `MessageListenerContainer.pause()`) and write `resume-at = now + app.mail.quota.pause-duration` to a dedicated
   Redis key with a conditional (NX) write, so the first detection wins when several records hit the quota at
   once under `NOTIFICATION_REQUESTED_LISTENER_CONCURRENCY`.
4. **Resume.** A `@Scheduled` poll of that key; once elapsed, `container.resume()`, clear the key, log INFO.
   Log WARN on pause with the resume time. No metrics, matching this service's observability level.
5. **Restart safety.** On boot, if Redis holds an active pause window, start the container already paused.
6. **Config.** `app.mail.quota.pause-duration=${MAIL_QUOTA_PAUSE_DURATION:24h}`.
7. **Tests.** Step 1 is a plain unit test. Steps 3 to 5 are a component test with real Testcontainers Redis and
   Kafka and a mocked `JavaMailSender` bean that throws the quota exception (Mailpit cannot bounce with a chosen
   code, and `CLAUDE.md` bars fake SMTP infra in integration tests). It does not touch
   `TopicNewsNotificationRequestedListenerIntegrationTest` or `TESTING.md`.
8. **Recovery** of dead-lettered quota records stays the existing manual `dlt-replay` run mode.

### Per-topic unsubscribe link and `List-Unsubscribe` header

**Trigger:** approaching bulk-sender thresholds (about 5k/day to Gmail or Yahoo). Scope is per-topic
unsubscribe, matching the subscription model, not a global opt-out.

The gateway mints a signed, purpose-scoped unsubscribe URL (reusing its JWT signing) and serves a new
unauthenticated one-click endpoint (`List-Unsubscribe-Post: List-Unsubscribe=One-Click`). That side needs its own
`/grill-me` in the gateway. This service only renders the URL it is given, with no new signing code or secret.

1. Add a nullable `unsubscribeUrl` to `TopicNewsNotificationEventDTO` (this service's hand-copied half of the wire
   contract; keep it in lockstep with the gateway's). Nullable, not `@NotBlank`, so in-flight messages from an old
   gateway build still send without the link instead of dead-lettering.
2. `TopicNewsEmailRenderer`: add an `UNSUBSCRIBE_URL` token. The renderer does flat substitution only, so the
   footer gets two fixed variants (with link, without) and the renderer picks one. Wire both into the `.html` and
   `.txt` templates' footer line.
3. `TopicNewsMailServiceImpl.send()`: set `List-Unsubscribe` (URL form only, no `mailto:`) plus
   `List-Unsubscribe-Post: List-Unsubscribe=One-Click` when the URL is present; omit both when absent.
4. Tests: `TopicNewsEmailRendererTest` (both footers and the token), `TopicNewsMailServiceImplTest` (headers only
   when the URL is present), a null-field case in `TopicNewsNotificationServiceImplTest`, and present/absent
   scenarios in `TopicNewsNotificationRequestedListenerIntegrationTest` with a `TESTING.md` update in the same
   change.

## Accepted gaps (revisit when the named trigger lands)

- **One duplicate email per crash or Redis blip between send and mark.** This is the at-least-once window and only
  shows on a later Kafka redelivery. **Permanent:** inherent to SMTP plus at-least-once delivery.
- **Blocking retries hold a partition** for up to about 4 minutes (2 s to a 60 s cap, 8 retries) on a transient
  failure. That is the backpressure wanted during an SMTP outage, but one bad-but-transient record also stalls its
  partition. **Trigger:** measurable notification latency complaints, then `@RetryableTopic` non-blocking retries.
  Their schedule must cover outages (about 1 h total), or an outage dead-letters the whole backlog.
- **Redis AOF stays `everysec`**, not `always`. A crash can lose about 1 s of dedupe keys, which can only cause a
  duplicate on redelivery, never a lost delivery. **Permanent:** `always` costs an fsync per write to close a
  window whose only failure is an already-accepted duplicate.
- **A consumer started before its topic exists picks the topic up only after the Kafka metadata refresh (about 5
  minutes).** Seen in the Phase 4 smoke check on 2026-10-14: the email arrived late. **Trigger:** emails late or
  missing right after a fresh deploy, then create the topic before the consumer starts or shorten the refresh
  interval.
