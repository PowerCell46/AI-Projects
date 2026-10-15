# SignalFlow — cross-service improvements

Improvements that touch more than one service. Each service keeps its own `PLAN.md` for service-local work.
All four phases below shipped; their design, step gates and decision records are in git history
(`git log -p -- PLAN.md`). What's left here is the gaps accepted on purpose and what should trigger revisiting each.

**How to work through a new phase here.** Do the steps in order. Inside a step, tick each task `- [x]` as it
lands. A step is done only when its **Gate** passes: tick the gate and append ` ✅ **Done** (YYYY-MM-DD)`
to the step heading in the same change. **No step starts on a red or missing test, and each step ends
green.** Before any `.java` edit load `java-code-style` (and `java-junit` for tests); before any `.ts`/`.tsx`/
`.css` edit load `frontend-code-style`. Each service's own `CLAUDE.md` rules still apply.

## Status

| Phase | Scope | Done | Audit |
| --- | --- | --- | --- |
| 1 | An interest topic belongs to 1–3 categories | 2026-10-14 | 2026-10-15 |
| 2 | Tell the model which stories were already covered | 2026-10-14 | 2026-10-15 |
| 3 | An admin can delete a user account | 2026-10-14 | 2026-10-15 |
| 4 | Each topic has a news schedule | 2026-10-14 | 2026-10-15 |

## Accepted gaps

### Phase 1 — categories

- **Shortcut: no mixed-version events.** The event field changes from `categoryName` to `categoryNames`
  in one go, so all three services must be deployed together. A stale in-flight event is skipped by the
  gateway guard or rejected by the mail validation; it is not delivered. Acceptable because the databases
  are wiped on restart anyway. **Trigger:** keeping data across deploys, or deploying services
  independently, means adding the new field alongside the old one and removing the old one later.
- **No category filter in the feed.** Decided 2026-10-13: a good idea, but extra effort for now.
  **Trigger:** users ask to browse by category.
- **The admin list filters by one `categoryId`, not by several.** **Trigger:** an admin screen that needs
  "any of" or "all of" filtering.
- **Seed topics: all 10 have 2 categories, Artificial Intelligence has 3.** Updated 2026-10-14 with six extra seed categories (Security & Privacy, Consumer Products, Economy, Lifestyle, Science & Research, Arts & Culture) that no topic owns as its first category. The pairings are hand-picked; add a topic only where the extra category clearly fits.
- **The category order is alphabetical; there's no admin-chosen order or primary category.** **Trigger:**
  a need to emphasize one category.

### Phase 2 — already-covered stories

- **Shortcut: no code-side de-duplication.** Repeats are only discouraged in the prompt. A reworded
  repeat can still get through, and so can an identical one. Decided 2026-10-13: we rely on the model.
  **Trigger:** readers keep reporting repeats after this ships, which means comparing items in code (for
  example by source URL or headline similarity) and dropping matches.
- **Headlines are re-parsed from HTML on every run, not stored.** If the model ignores the required
  shape, extraction finds nothing and that day's hint is empty. Nothing fails; the count in the log
  line shows it. **Trigger:** the logged count is often 0 for topics with history, which means asking
  the model for structured (JSON) items and storing the headlines alongside the HTML.
- **The covered list is capped at 50 headlines of 200 characters each** (security audit, 2026-10-15). Normal use is
  about 35 headlines (7 days × 5 items), so the cap only bites on a larger lookback or item limit. The query returns
  newest first, so the oldest headlines are the ones left out, silently. **Trigger:** a lookback or item limit that
  regularly passes 50 headlines, which means raising the cap.
- **"Nothing new" days still send an email.** **Trigger:** readers find those emails noisy, which means
  recognising the note and not publishing that day.
- **Headlines only, no source links.** Decided 2026-10-13: the headline is enough. **Trigger:** vague
  headlines (for example "Stocks rallied") are often repeated anyway.

### Phase 3 — admin deletes a user

- **Shortcut: a deleted user's token keeps working until it expires** (up to `JWT_TTL`, 1 h). Reads
  succeed; subscribe answers 401. **Trigger:** a need to cut off access instantly, which means checking
  the user per request or keeping a revoked-token list.
- **The frontend doesn't treat a 401 from subscribe as "log in again".** It shows whatever it shows
  for a failed subscribe today. **Trigger:** deleted users actually hit this, or the frontend starts
  handling session expiry in general.
- **Queued emails aren't cancelled.** The window is a few seconds. Deleting a user also deletes their
  `notification_outbox` rows (security audit, 2026-10-15), so no address or AI text outlives the account.
- **No user list or admin UI.** You need to know the email. **Trigger:** more than one person
  administering accounts, or a need to browse users.
- **Admins can't be deleted through the app**, only in the database. **Trigger:** more than one admin.
- **No deactivate endpoint**, even though `User.enabled` exists. **Trigger:** a need to block an
  account reversibly.
- **The only audit trail is a log line.** **Trigger:** a need to answer "who deleted whom, when"
  after logs rotate.

### Phase 4 — news schedule

- **Shortcut: no mixed-version events.** Same as Phase 1: the new event fields ship in all three services
  together. **Trigger:** keeping data across deploys or deploying the services separately.
- **A missed or failed run is lost, not retried.** If the service is down, or the AI call fails, on a
  `WEEKLY` topic's Monday, that week has no issue. The next issue covers only its own 7 days. **Trigger:**
  a weekly issue goes missing, which means a same-day retry or a catch-up run on start.
- **The period comes from the preset, not from the last issue.** After a missed run or a schedule
  change, the period can leave a gap (see above) or overlap the previous issue. **Trigger:** readers
  notice gaps or repeats after schedule changes.
- **Only four presets.** `WEEKLY` is always Monday, and there's no every-2-weeks or monthly option.
  **Trigger:** a topic that needs another day or rhythm.
- **One time of day for every topic.** **Trigger:** a topic that needs news at a different hour.
- **No feed filter by schedule.** **Trigger:** users ask to browse by how often a topic updates.
