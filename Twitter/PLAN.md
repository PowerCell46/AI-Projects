# Twitter — plans

Each service owns its own backlog:

- **`twitter_api_gateway/PLAN.md`**: the entry point and user service. Three phases: auth + email
  confirmation → profile (MinIO) → follows. Designed via `/grill-me` 2026-09-29.
- **`twitter_mail_service/PLAN.md`**: not yet written. Needs its own `/grill-me`. It consumes
  `user.confirmation-requested` (contract in `twitter_api_gateway/EVENTS.md`).
