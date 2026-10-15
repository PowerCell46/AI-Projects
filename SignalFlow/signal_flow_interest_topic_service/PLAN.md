# SignalFlow Interest Topic Service — plan

Shipped phases and audits are in git history. See `CLAUDE.md` for what this service is and how to run it,
`DECISIONS.md` for why non-obvious calls were made, `TESTING.md` for the e2e catalog.

Below is what's still open.

---

## Known gaps (accepted, not oversights)

- **Single instance assumed.** No scheduler locking; two instances would double-pay AI calls and could
  double-publish. **Trigger:** a second instance → ShedLock or `SKIP LOCKED`. *(DECISIONS.md)*
- **Deleting a topic drops its news, including unsent `PENDING` rows.** *(DECISIONS.md)*
- **`ddl-auto=create`: the database is wiped on every start, on purpose; no versioned migrations.**
  **Trigger:** a second deployed environment, or data that must survive a restart.
- **News rows kept forever.** **Trigger:** table size becomes a concern.
- **AI failures lose that day's news for the topic.** **Trigger:** the real provider proves flaky.
- **Grounded content isn't fact-checked.** A false or injected web page can still put false claims and real
  `https` links into an email — sanitized, not verified. **Trigger:** the first bad email reported → a
  source-domain allowlist or a review step.
- **Only the mail service sanitizes `data`.** This service and the gateway store and relay it raw.
  **Trigger:** any new consumer that renders `data` (e.g. a frontend news view) → sanitize at that sink, or
  move sanitizing to the source.
- **Truncated answers (`finish_reason=length`) are stored.** **Trigger:** truncation shows up in real emails
  → raise `max-tokens` or reject.
- **`TopicNewsRepositoryIntegrationTest` has the same test-isolation gap** `CategoryRepositoryIntegrationTest`/
  `InterestTopicRepositoryIntegrationTest` had (fixed 2026-09-25, `DECISIONS.md`) — no `@BeforeEach` cleanup
  of rows a different, non-rolled-back `@SpringBootTest` context may already have committed to the shared
  Testcontainers Postgres. Hasn't shown a failure yet. **Trigger:** it does → give it the same cleanup.
