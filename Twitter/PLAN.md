# Twitter — plans

Each service owns its own backlog:

- **`twitter_api_gateway/PLAN.md`**: the entry point and user service. Phases 1–5 (auth + email
  confirmation → profile (MinIO) → follows → tweet routing → people to follow) done, designed via `/grill-me`
  2026-09-29 (phase 5 via `/plan-backend` 2026-10-04).
- **`twitter_tweet_service/PLAN.md`**: tweets in Mongo (create with up to 4 images, read, edit text, delete),
  `tweet.created` / `tweet.deleted` via an outbox. Designed via `/grill-me` 2026-09-30. Built 2026-09-30 (steps 1–10 done); gateway
  phase 4 may start. Phases 2–3, replies (API, then UI; brief in git history, `docs/replies-design.md` at `8238824^`), designed via `/grill-me`
  2026-10-06; phase 2 (the API) is built and hardened (steps 11-22, done 2026-10-07), phase 3 (the UI) is built and hardened (steps 23-30, done 2026-10-07, visual check waived); they also hold the
  timeline, gateway, frontend and e2e steps.
- **`frontend/PLAN.md`**: the SPA, starting with the "Hadal Descent" auth flow (login, register, `/confirm`,
  `/resend`) (brief in git history, `600a3aa^`). Designed via `/grill-me`
  2026-10-01; built 2026-10-01 (all 12 steps done: unit, component and Playwright e2e tests green). Phase 2, the feed
  (`/feed`, `/saved`, compose, views, `NEW POSTS`; brief in git history, `95d594a^`), designed via `/grill-me` and built
  2026-10-04 (13 steps, with a like stub in the gateway and `savedByMe` in the timeline service). Phase 3, the tab row
  and People at `/users` (brief in git history, `8238824^`) with back-fill on follow across the tweet service, the
  gateway and the timeline service (steps 14–24), designed via `/grill-me` and built 2026-10-04 (visual check waived). Phases 4–5, the profile
  view (`frontend/profile-view-design.md`; API in the gateway, tweet and timeline services, then the UI; steps 25–40),
  designed via `/grill-me` 2026-10-08; phase 4 (the API, steps 25–29) built 2026-10-08, phase 5 (the UI, steps 30–40) built 2026-10-08 (visual check waived).
- **`twitter_mail_service/PLAN.md`**: sends the emails over SMTP from Kafka, deduped in Redis. Designed via
  `/grill-me` 2026-10-01. Phase 1 (the confirmation email, `user.confirmation-requested`; steps 1–9) built
  2026-10-01, including the Playwright e2e that reads the link from the real email. Phase 2 (the follow email,
  `user.followed`; steps 10–13) built 2026-10-02: the gateway's `user.followed` change is committed (`85019be`).
- **`twitter_timeline_service/PLAN.md`**: the feed (fan-out on `tweet.created`, 7-day retention), saved tweets,
  unique views and likes (phases 4–5: the like endpoints and counter, the likes UI and `/liked`), in Postgres. Designed via `/grill-me` 2026-10-03; three phases, all built 2026-10-03 (the feed, saved tweets, views). Its plan also holds
  the gateway steps (internal endpoints, `user.unfollowed`, routes) and the tweet-service steps (internal batch
  read, `views` removed).

---

e2e doesn't have README.md like SignalFlow

in the docker compose we must have a setup with nginx acting like a load balancer and two instances of the api gateway service

frontend has to be added to the docker compose + Dockerfile for it (nginx configuration) ✅ **Done** (2026-10-10): `frontend/Dockerfile`, `frontend/nginx/` and the `frontend` service on `127.0.0.1:5173`. ✅ **Done** (2026-10-10): two gateway instances (`gateway`, `gateway-2`) behind nginx round robin; the outbox poller uses `SKIP LOCKED` and the cleanup job and seeder an advisory lock (`twitter_api_gateway/DECISIONS.md`). Not run end to end yet

make claude generate a graph of the architecture of the project