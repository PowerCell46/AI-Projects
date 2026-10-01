# Twitter — plans

Each service owns its own backlog:

- **`twitter_api_gateway/PLAN.md`**: the entry point and user service. Phases 1–3 (auth + email
  confirmation → profile (MinIO) → follows) done, designed via `/grill-me` 2026-09-29. Phase 4 (proxy
  `/api/v1/tweets/**` to the tweet service) planned 2026-09-30, and it starts after the tweet service's final gate.
- **`twitter_tweet_service/PLAN.md`**: tweets in Mongo (create with up to 4 images, read, edit text, delete),
  `tweet.created` / `tweet.deleted` via an outbox. Designed via `/grill-me` 2026-09-30. Built 2026-09-30 (steps 1–10 done); gateway
  phase 4 may start.
- **`frontend/PLAN.md`**: the SPA, starting with the "Hadal Descent" auth flow (login, register, `/confirm`,
  `/resend`, placeholder `/feed`) from `frontend/AuthenticationViewsDesigns.md`. Designed via `/grill-me`
  2026-10-01; built 2026-10-01 (all 12 steps done: unit, component and Playwright e2e tests green).
- **`twitter_mail_service/PLAN.md`**: not yet written. Needs its own `/grill-me`. It consumes
  `user.confirmation-requested` (contract in `twitter_api_gateway/EVENTS.md`).