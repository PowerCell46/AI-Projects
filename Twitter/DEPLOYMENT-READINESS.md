# Deployment readiness: what is left

Updated 2026-10-11 (`ae79f96`, branch `main`). Only open items remain.

## Accepted gaps (revisit when relevant)

- Git history still holds the old audit documents (including `70fea9d`); removing them needs a history
  rewrite with a force-push.

## P3: nice to have

- **Other:**
  - Timeline: no dead-letter replay tool; `ix_feed_entries_user_author` may be redundant; every
    fan-out row writes 4 indexes.
  - Tweet: `countByAuthorId` is O(author's tweets); outbox sends and deletes one message at a time.

