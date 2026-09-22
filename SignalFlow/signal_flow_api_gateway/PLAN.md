# SignalFlow API Gateway — what's left

The **auth** phase (2026-09-21) and the **subscriptions** phase (2026-09-22) have both shipped and are
green. Their design, step-by-step gates and `/grill-me` interview records are no longer carried here —
read them from git history (`git log -p -- PLAN.md`). Calls made during implementation live in
`DECISIONS.md`, the e2e catalog in `TESTING.md`, the latest audit in
`exploit-report-2026-09-22-subscriptions.md`. Three of that audit's findings — the cap's check-then-act
race, a non-UUID `sub` reaching a controller, and the missing request body cap — have since been fixed
and were dropped from it (its status note records what closed each). Its four remaining findings are
open by decision and appear under *Accepted gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Fix — real defects, ranked

Nothing open.

## Build — deferred by decision, not oversight

- **Topic-existence validation.** Nothing confirms an `interestTopicId` names a real topic — the gateway
  holds no topics and calls no `InterestTopicService`. Closes the "any UUID is subscribable" gap below.
- **The by-topic fan-out read path.** "All subscribers of topic X" is what the daily notification job
  wants, but exposing it means service-to-service auth, and this gateway only knows how to authenticate a
  human holding a `SameSite=Strict` cookie. A phase of its own; the index it needs already exists (the
  unique constraint leads with `interest_topic_id` for exactly this reason). Subscriber counts stay
  unexposed until then, so nothing leaks yet.
- **Request forwarding.** `spring-cloud-gateway-server-webmvc` (the blocking variant — reactive was
  rejected, it rules out JPA for throughput this project doesn't need). Nothing built so far has to
  change to accommodate it. When this lands and starts forwarding to
  `signal_flow_interest_topic_service`: enforce `ADMIN` on its topic/category write endpoints and
  require authentication (any role) on `GET /interest-topics` — that service has no Spring Security of
  its own and trusts every caller (see its `PLAN.md`'s Known gaps).
- **The SPA's subscription screens.** `../frontend/` has shipped — single origin, nginx proxying `/api/`
  and Vite's `server.proxy` in dev, no CORS config anywhere as planned — but it calls `/api/v1/auth/*`
  only. Subscribe/unsubscribe/list are still to wire; all three endpoints exist.
- **Email confirmation.** Register activates immediately today, so anyone can register with an address
  they do not own.

## Accepted gaps — revisit when the named trigger lands

- **No rate limiting anywhere.** Login brute-force is capped only by bcrypt-12 (~4 guesses/sec/core), and
  register is `permitAll` with no throttle at all — which the subscription cap now amplifies: one
  anonymous signup yields a user row *plus* up to `max-per-user` subscription rows. Doing it properly
  wants shared state (Redis) once there is more than one instance. **Trigger:** a second instance, or
  abuse in the wild.
- **Claims are stale for up to an hour, including for writes.** Disabling an account or changing a role
  takes effect only when the current token expires, and since the subscriptions phase that window covers
  writes, not just the read-only `/me`. Direct consequence of "no DB read on an authenticated request."
  **Trigger:** needing to cut off an account immediately.
- **No server-side logout revocation.** A stolen token lives until it expires (≤1h). Same trigger.
- **Any UUID is subscribable.** Bounded by the per-user cap, which a `SELECT ... FOR UPDATE` on the
  caller's user row now holds under concurrency too; closes with topic-existence validation above.
- **The service's `catch (DataIntegrityViolationException)` assumes the unique constraint.** A
  subscription insert can also break the `user_id` FK, and both surface as the same exception — a user
  whose row had been deleted would be told "already subscribed." Unreachable today: no user-delete path
  exists. **Trigger:** the first one that does; discriminate on SQLState `23505` vs `23503` then.
- **Register leaks whether an email is registered** (explicit `409`, chosen over a silent success).
- **`ddl-auto=update`, no versioned migrations.** Schema drift between environments is possible.
  **Trigger:** a second deployed environment, or the first destructive schema change.
- **Downstream trust model** (identity headers over a private network) is unimplemented and unverified.
  **Trigger:** the first downstream service.
