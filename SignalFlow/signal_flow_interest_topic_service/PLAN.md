# SignalFlow Interest Topic Service — plan

Phase 1 (data model, endpoints, news generation job, outbox publisher, exploit-hunter audit) shipped
2026-09-23. See `CLAUDE.md` for what this service is and how to run it, `DECISIONS.md` for why
non-obvious calls were made, `TESTING.md` for the e2e catalog.

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
