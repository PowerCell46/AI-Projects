# SignalFlow Interest Topic Service — plan

Phase 1 (data model, endpoints, news generation job, outbox publisher, exploit-hunter audit) shipped
2026-09-23. Phase 2 (real news generation via OpenRouter, mail sanitizer) shipped 2026-09-24 - manual
smoke check confirmed a real `topic_news` row (clean HTML, working source links) and a correctly
rendering delivered email. See `CLAUDE.md` for what this service is and how to run it, `DECISIONS.md`
for why non-obvious calls were made, `TESTING.md` for the e2e catalog.

Below is what's still open.

---

## Known gaps (accepted, not oversights)

- **Single instance assumed.** No scheduler locking; two instances would double-pay AI calls and could
  double-publish. **Trigger:** a second instance → ShedLock or `SKIP LOCKED`. *(DECISIONS.md)*
- **Deleting a topic drops its news, including unsent `PENDING` rows.** *(DECISIONS.md)*
- **`ddl-auto=update`, no versioned migrations.** **Trigger:** a second deployed environment or the first
  destructive schema change.
- **News rows kept forever.** **Trigger:** table size becomes a concern.
- **AI failures lose that day's news for the topic.** **Trigger:** the real provider proves flaky.
- **Serial generation.** **Trigger:** a run no longer finishes well before the next, or the topic count
  grows past a few hundred → bounded parallelism.
- **Grounded content isn't fact-checked.** A false or injected web page can still put false claims and real
  `https` links into an email — sanitized, not verified. **Trigger:** the first bad email reported → a
  source-domain allowlist or a review step.
- **No spend cap in code.** The control is OpenRouter's per-key credit limit. **Trigger:** topic growth or an
  unexpected bill.
- **Only the mail service sanitizes `data`.** This service and the gateway store and relay it raw.
  **Trigger:** any new consumer that renders `data` (e.g. a frontend news view) → sanitize at that sink, or
  move sanitizing to the source.
- **Truncated answers (`finish_reason=length`) are stored.** **Trigger:** truncation shows up in real emails
  → raise `max-tokens` or reject.
