# SignalFlow Mail Service — what's left

The **topic-news notification emails** phase (2026-09-24) has shipped and is green. Its design,
step-by-step gates and `/grill-me` interview record are no longer carried here. Read them from git
history (`git log -p -- PLAN.md`). Calls made during implementation live in `DECISIONS.md`, the e2e
catalog in `TESTING.md`, and the audit in `exploit-report-2026-09-24-topic-news-notification-emails.md`.
That report's status note records which findings were fixed. The rest are open by decision and appear
under *Accepted gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Accepted gaps — revisit when the named trigger lands

- **`data` is inserted into the email as raw HTML.** Whatever reaches it is live markup sent from our
  Gmail account; once an LLM writes it, a prompt-injected topic prompt could ship phishing links under our
  sender reputation. **Trigger:** the real LLM replaces the mock → allowlist sanitizer (e.g. OWASP Java
  HTML Sanitizer) before insertion.
- **Gmail sending quota** (~500 recipients/day personal, ~2,000 Workspace). A popular topic's fan-out hits
  it; the `550 5.4.5` form is classified permanent and dead-letters, the `4xx` form retries then
  dead-letters. No quota-aware pausing. **Trigger:** the quota is actually hit → a transactional provider,
  or pause-and-resume.
- **One duplicate email per crash/Redis blip between send and mark** — the at-least-once window, only on a
  later Kafka redelivery.
- **Blocking retries hold a partition** for up to ~4 min in production (2s→60s cap, 8 retries) on a
  transient failure. Desirable backpressure during an SMTP outage; a single bad-but-transient record also
  stalls its partition. **Trigger:** measurable notification latency complaints → `@RetryableTopic`
  non-blocking retries.
- **No unsubscribe link / `List-Unsubscribe` header.** Unsubscribe lives in the SPA. **Trigger:**
  approaching bulk-sender thresholds (~5k/day to Gmail/Yahoo) → a signed-token unsubscribe endpoint on the
  gateway.
- **HTML-only emails, no `text/plain` alternative.** Slightly worse spam scoring. **Trigger:**
  deliverability problems.
- **Redis has no password** on the private network; AOF `everysec` can lose ~1s of dedupe keys on a crash
  (→ possible duplicates on redelivery only). **Trigger:** Redis shared or exposed beyond the internal
  network.
- **`topic-news.notification-requested-dlt` records are only logged, no replay tool.** Replay is safe
  (claims are released on failure). **Trigger:** the first record that needs replaying.
- **No length cap on `topicName`/`categoryName`/`data`** (`exploit-report-2026-09-24-topic-news-notification-emails.md`
  #2). Kafka's own per-record cap (~1 MB default) is the only limit before a value renders straight into an
  outgoing email. Mirrors the gateway's own accepted gap for the same fields on `TopicNewsEventDTO`.
  **Trigger:** a version drift between the two services' DTOs, or the first oversized value actually shows
  up → `@Size` on `topicName`/`categoryName`, a cap or truncation on `data`.
- **Boot `4.2.0-M1` is a pre-release milestone**, not GA (`exploit-report-2026-09-24-topic-news-notification-emails.md`
  #3) — shared with `signal_flow_interest_topic_service`'s own existing choice, not introduced by this
  phase. No patch/security-support guarantee until GA. **Trigger:** the Boot 4.2 GA release ships → upgrade.
