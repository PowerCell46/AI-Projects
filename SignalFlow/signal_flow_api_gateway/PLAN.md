# SignalFlow API Gateway — what's left

The **auth** (2026-09-21), **subscriptions** (2026-09-22) and **interest-topic routing** (2026-09-23)
phases have shipped and are green. Their design, step-by-step gates and `/grill-me` interview records are
no longer carried here. Read them from git history (`git log -p -- PLAN.md`). Calls made during
implementation live in `DECISIONS.md`, the e2e catalog in `TESTING.md`, and the audits in
`exploit-report-2026-09-22-subscriptions.md`, `exploit-report-2026-09-23-topic-routing.md` and
`exploit-report-2026-09-23-reconciliation.md`. Each report's
status note records which findings were fixed. The rest are open by decision and appear under *Accepted
gaps* below with their triggers.

This file now holds only what is still to do, fix or improve. Same rule as before for anything picked up
from it: **no step starts on a red or missing test, and each step ends green.**

---

## Phase: subscription reconciliation (shipped, 2026-09-23)

Nothing confirms that a subscription's `interestTopicId` names a real topic, and deleting a topic leaves
its subscriptions behind. Instead of checking on every subscribe, a daily job re-checks every subscribed
topic id against the topic service and hard-deletes subscriptions whose topic doesn't exist. That closes
both gaps with one mechanism, and subscribe keeps no runtime dependency on the topic service.

### Design

- **Subscribe is unchanged.** A made-up topic id still gets 201 and disappears at the next run. That's
  acceptable because the SPA only offers real topics, so only hand-crafted requests hit it, and a bogus id
  never matches any news, so it can't trigger a notification. The notification side needs no changes.
- **Schedule: daily at 05:00 Europe/Sofia.** It's set by two properties:
  `app.subscriptions.reconciliation.cron` (default `0 0 5 * * *`) and `app.subscriptions.reconciliation.zone`
  (default `Europe/Sofia`). The zone is explicit rather than the server default, so moving the container
  doesn't move the run.
- **Re-check everything each run, with no status column.** A per-row CHECKED flag would never re-check a row
  whose topic is deleted later. Re-checking all distinct subscribed topic ids closes the orphan gap too, and
  needs no schema change. Real ids are bounded by the topic count, and junk ids by the per-user cap times the
  number of users.
- **Batching.** Distinct topic ids are read with keyset pagination
  (`WHERE interest_topic_id > :after ORDER BY interest_topic_id LIMIT :size`), which stays correct while the
  same run deletes rows; offset paging would skip ids. The batch size is
  `app.subscriptions.reconciliation.batch-size`, default 200, which keeps each request under the topic
  service's 8 KB body cap.
- **Topic service endpoint: `POST /internal/v1/interest-topics/existing`.** The body is `{"ids": [...]}`
  and the response is `200 {"existingIds": [...]}`. It's a POST because 200 UUIDs in a GET query string
  would blow Tomcat's 8 KB header limit. It sits outside `/api/v1/**`, so the gateway's routes never expose
  it; the topic service is still unauthenticated and reachable only from the gateway.
- **Gateway client.** A service pair for the lookup, built on Boot's `RestClient.Builder` against
  `app.interest-topic-service.url`. It inherits the global 2s/10s timeouts and the HTTP/1.1 pin.
- **Deletion.** For each batch, `ids - existingIds` are hard-deleted (`DELETE ... WHERE interest_topic_id IN
  (...)`), one transaction per batch. Only subscriptions to topics confirmed missing are touched, and a
  topic id can't be re-created, so the delete never races a valid subscription.
- **Failure: stop the run and delete nothing further.** A non-2xx response, a timeout or a refused
  connection ends the run. Batches already confirmed keep their deletes. "No answer" is never read as "doesn't
  exist". The run logs a `warn`, and a success logs one INFO summary (ids checked, subscriptions deleted).
- **Multiple instances** would each run the job. That's harmless because the deletes give the same result
  either way, only wasted work. It's logged under *Accepted gaps* next to the topic service's scheduler gap.

### Scenarios

- **Topic service, endpoint:** returns only the ids that exist, returns an empty list when none exist, 400
  for a missing or malformed `ids`, and 413 over the body cap.
- **Gateway, repository:** keyset paging returns each distinct topic id once, across pages, even while rows
  are deleted between pages. The delete removes exactly the given topic ids' subscriptions.
- **Gateway, client (WireMock):** sends the expected request shape and parses `existingIds`. Non-2xx, a
  timeout and a refused connection each surface as a failure, never as "none exist".
- **Gateway, reconciliation service:**
  - Subscriptions to missing topics are deleted and existing ones kept.
  - An empty table makes no call.
  - Batches split at the configured size.
  - A failure on batch *n* stops the run, with batches before *n* deleted and nothing after.

### Steps

1. **Topic service: the existence endpoint, plus its body cap.** Build `POST /internal/v1/interest-topics/existing`
   with controller, service and repository (`findExistingIds`). Also raise the topic service's body cap for
   topic writes to match the gateway's 32 KB. Today a 4000-character CJK prompt passes the gateway and then
   gets 413 downstream (exploit report 2026-09-23 #3 was only half fixed). **Gate:** its e2e scenarios are
   green, and its `TESTING.md` is updated. ✅ **Done 2026-09-23.**
2. **Gateway: repository queries.** Add keyset distinct-ids and delete-by-topic-ids. **Gate:** the repository
   scenarios are green. ✅ **Done 2026-09-23.**
3. **Gateway: topic-service client.** Add the service pair on `RestClient`. **Gate:** the WireMock client
   scenarios are green. ✅ **Done 2026-09-23.**
4. **Gateway: reconciliation service and scheduled job.** Add `@EnableScheduling` and a `jobs` package,
   mirroring the topic service's, which means adding `/jobs` to CLAUDE.md's package list. **Gate:** the
   reconciliation scenarios are green, and the full suite is green. ✅ **Done 2026-09-23.**
5. **Docs.** Close the "any UUID is subscribable" gap here and the topic service's two gaps ("nothing lets
   the gateway confirm a topic exists", "deleting a topic orphans the gateway's subscriptions"). Add the
   multi-instance job gap. **Gate:** docs are consistent. ✅ **Done 2026-09-23.**
6. **`exploit-hunter` over the internal endpoint and the job.** Report only. **Gate:** report written, and
   each finding is either fixed or logged. ✅ **Done 2026-09-23.** `exploit-report-2026-09-23-reconciliation.md`
   has 2 Low findings, both logged under *Fix* below.

**Out of scope:** a live existence check at subscribe time, the by-topic fan-out read path, and ShedLock.

### Interview record

| # | Question | Answer |
|---|---|---|
| 1 | Live check at subscribe, or later? | A daily job; subscribe stays unchecked |
| 2 | Per-row CHECKED/UNCHECKED status? | No, re-check all distinct topic ids each run (also covers topics deleted later) |
| 3 | When does it run? | Every day at 05:00 |
| 4 | Invalid subscriptions? | Hard delete, gone for good |
| 5 | Notification side changes? | None needed, since bogus ids never match any news |
| 6 | Timezone for 05:00? | Explicit `Europe/Sofia` |
| 7 | Safety check against a mass delete? | Not built; logged under *Accepted gaps* as a potential case |

---

## Fix — real defects, ranked

1. **A `null` in the topic service's `existingIds` aborts the reconciliation with an uncaught NPE**
   (`exploit-report-2026-09-23-reconciliation.md` #1, Low). It fails safe, since nothing is wrongly deleted,
   but it skips the stop-and-warn path. Fix: the client throws `InterestTopicLookupFailedException` on a null
   element. It must not filter the nulls out, because that would delete subscriptions.
2. **`app.subscriptions.reconciliation.batch-size` above ~208 silently disables the job**, because every lookup
   then gets 413 from the topic service's 8 KB internal cap (same report, #2, Low). Fix: reject a batch size
   over 200 at startup, and optionally log a 4xx-caused stop at ERROR.

(Long non-Latin topic prompts getting 413 from the topic service, exploit report 2026-09-23 #3, was fixed
2026-09-23 in step 1 of the reconciliation phase.)

## Build — deferred by decision, not oversight

- **The by-topic fan-out read path.** "All subscribers of topic X" is what the daily notification job
  wants, but exposing it means service-to-service auth, and this gateway only knows how to authenticate a
  human holding a `SameSite=Strict` cookie. A phase of its own; the index it needs already exists (the
  unique constraint leads with `interest_topic_id` for exactly this reason). Subscriber counts stay
  unexposed until then, so nothing leaks yet.
- **The SPA's subscription screens.** `../frontend/` has shipped — single origin, nginx proxying `/api/`
  and Vite's `server.proxy` in dev, no CORS config anywhere as planned — but it calls `/api/v1/auth/*`
  only. Subscribe/unsubscribe/list are still to wire; all three endpoints exist.
- **The SPA's topic/category screens.** A topic and category list for every user, plus admin
  create/edit/delete. Unblocked, since all eight endpoints are routed through the gateway.
- **Email confirmation.** Register activates immediately today, so anyone can register with an address
  they do not own.

## Accepted gaps — revisit when the named trigger lands

- **No rate limiting anywhere.** Login brute-force is capped only by bcrypt-12 (~4 guesses/sec/core), and
  register is `permitAll` with no throttle at all — which the subscription cap now amplifies: one
  anonymous signup yields a user row *plus* up to `max-per-user` subscription rows. Doing it properly
  wants shared state (Redis) once there is more than one instance. Topic routing adds unthrottled
  `GET /api/v1/interest-topics?size=100` for any user, each a downstream DB query that can hold a thread for up
  to the 10s read timeout. **Trigger:** a second instance, or abuse in the wild.
- **Claims are stale for up to an hour, including for writes.** Disabling an account or changing a role
  takes effect only when the current token expires, and since the subscriptions phase that window covers
  writes, not just the read-only `/me`. Since topic routing, that also means a demoted ADMIN keeps topic and
  category create/edit/delete for the rest of the window. Direct consequence of "no DB read on an authenticated request."
  **Trigger:** needing to cut off an account immediately.
- **No server-side logout revocation.** A stolen token lives until it expires (≤1h). Same trigger.
- ~~**Any UUID is subscribable.**~~ **Closed 2026-09-23** by the subscription reconciliation phase: subscribe
  still accepts any UUID (bounded by the per-user cap, held under concurrency by a `SELECT ... FOR UPDATE` on
  the caller's user row), but a bogus id lives only until the next 05:00 run.
- **The reconciliation job runs on every instance.** No scheduler lock, so N instances make N full passes at
  05:00. Harmless, since the deletes give the same result either way; only wasted lookups. Same shape as the
  topic service's single-instance scheduler gap. **Trigger:** a second instance → ShedLock or a
  `pg_try_advisory_lock` guard.
- **The service's `catch (DataIntegrityViolationException)` assumes the unique constraint.** A
  subscription insert can also break the `user_id` FK, and both surface as the same exception — a user
  whose row had been deleted would be told "already subscribed." Unreachable today: no user-delete path
  exists. **Trigger:** the first one that does; discriminate on SQLState `23505` vs `23503` then.
- **Register leaks whether an email is registered** (explicit `409`, chosen over a silent success).
- **`ddl-auto=update`, no versioned migrations.** Schema drift between environments is possible.
  **Trigger:** a second deployed environment, or the first destructive schema change.
- **Downstream trust model** (identity headers over a private network) is unimplemented and unverified.
  The topic service is forwarded to with credentials stripped and no identity, because it consumes none; it
  trusts the gateway on network isolation alone. Every other client header is forwarded verbatim, including
  `X-User-Id`/`X-User-Role` (exploit report 2026-09-23 #2), so when identity headers land the gateway must
  strip the whole reserved prefix on the way in before adding its own. **Trigger:** the first downstream that
  needs caller identity.
- **Reconciliation trusts the topic service's answer completely.** If
  `INTEREST_TOPIC_SERVICE_URL` points at the wrong topic service, such as one with an empty database, it
  validly answers "none exist". The 05:00 run then hard-deletes every subscription, with no undo. A guard would
  check every batch first, then delete, and abort without deleting if more than about 50% of checked topic ids
  come back missing. Not built by decision. **Trigger:** a second deployed environment (where a mis-pointed
  URL becomes plausible), or the first accidental mass delete.
