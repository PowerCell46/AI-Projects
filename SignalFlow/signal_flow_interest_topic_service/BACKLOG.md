# Backlog

Gaps accepted on purpose for the topic service, each with the trigger that should reopen it. Carried over from
the closed-out `PLAN.md` (`git log -p -- PLAN.md`). Calls already made are in `DECISIONS.md`; shortcuts that are
easy to break are in `CLAUDE.md`.

- **Single instance assumed.** No scheduler locking, so two instances would double-pay AI calls and could
  double-publish. **Trigger:** a second instance, then ShedLock or `SKIP LOCKED` (see `DECISIONS.md`).
- **Deleting a topic drops its news, including unsent `PENDING` rows** (see `DECISIONS.md`).
- **No versioned migrations.** `ddl-auto=update` adds tables and columns but never alters them (see
  `DECISIONS.md`). **Trigger:** a second deployed environment, or a column change on a database that holds data.
- **News rows are kept forever.** **Trigger:** table size becomes a concern.
- **AI failures lose that day's news for the topic.** **Trigger:** the real provider proves flaky.
- **Grounded content is not fact-checked.** A false or injected web page can still put false claims and real
  `https` links into an email: sanitized, not verified. **Trigger:** the first bad email reported, then a
  source-domain allowlist or a review step.
- **Only the mail service sanitizes `data`.** This service and the gateway store and relay it raw. **Trigger:**
  any new consumer that renders `data` (for example a frontend news view), then sanitize at that sink or move
  sanitizing to the source.
- **Truncated answers (`finish_reason=length`) are stored.** **Trigger:** truncation shows up in real emails,
  then raise `max-tokens` or reject.
