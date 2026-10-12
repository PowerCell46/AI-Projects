# Deployment readiness: what is left

Updated 2026-10-11 (`ae79f96`, branch `main`). Only open items remain.

## Accepted gaps (revisit when relevant)

- DLT replication factor is hardcoded to 1 (mail `KafkaTopicConfiguration.java:16`, timeline `:17`):
  make it a property when a second Kafka broker arrives.
- The mail service does not filter `@seed.local`: a later real follow of a demo user would email
  that address.
- Git history still holds the old audit documents (including `70fea9d`); removing them needs a history
  rewrite with a force-push.

## P3: nice to have

- **Other:**
  - Timeline: no dead-letter replay tool; `ix_feed_entries_user_author` may be redundant; every
    fan-out row writes 4 indexes.
  - Tweet: `countByAuthorId` is O(author's tweets); outbox sends and deletes one message at a time.
  - Mail: no `spring.data.redis.timeout`; no `Auto-Submitted` header or From display name; use a
    transactional SMTP provider instead of Gmail.
  - No account deletion or password reset (out of scope), but real emails will be stored.
  - The e2e setup is documented only in the root README in two lines.
  - Mail: two listeners share one consumer group — give each its own group (as the timeline service
    does) if rebalances there ever become a problem.
