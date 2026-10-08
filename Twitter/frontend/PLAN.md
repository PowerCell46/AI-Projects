# Twitter Frontend — plan

**Status: phases 4–5 (profile view: API, then UI; `profile-view-design.md`) designed via `/grill-me` on 2026-10-08
(Q40–Q59), not started.** Phase 3 (tab row, People, back-fill on follow) designed via `/grill-me` on 2026-10-04 (Q24–Q39) and built
2026-10-04 (steps 14–24; the visual check was waived).** Phase 2 (feed) was designed via `/grill-me` (Q1–Q23) and built 2026-10-04. Phase 1 (the "Hadal Descent"
auth flow) was built 2026-10-01; its plan is in git history (`git show 95502e1:./PLAN.md` from this folder) and its
still-open items are under "Carried over" below. The replies UI and the tweet page are phase 3 of
`../twitter_tweet_service/PLAN.md` (steps 23-30), built 2026-10-07.

Visual sources of truth: phase 2 `feed-design.md` (removed in `95d594a`; `git show 95d594a^:./feed-design.md` from this
folder), phase 3 `feed-design-addition.md`. This plan records where the build departs from or extends a brief;
everything not mentioned here is built as the brief says.

Steps that touch other projects are marked **[tweet]**, **[gateway]**, **[timeline]** or **[e2e]**. They follow that
project's `CLAUDE.md` and standing rules (`java-code-style`, `java-junit`, `TESTING.md`, `DECISIONS.md`, `mvn verify` 3×).

**Hard rule:** no step starts on a red or missing test, each step ends green. Invoke `frontend-code-style` before
touching any `.ts`/`.tsx`/`.css`.

## Status

| Phase | Scope | Done | Audit |
|---|---|---|---|
| 2 | Feed (steps 1–13) | 2026-10-04 | `SECURITY-FINDINGS.md`, "Audit — 2026-10-04" |
| 3 | Tab row, People, back-fill on follow (steps 14–24) | 2026-10-04 | `SECURITY-FINDINGS.md`, "Audit — 2026-10-04 (phase 3 ...)"; `SECURITY-AUDITS.md` in the gateway; audit reports in the tweet and timeline services |

| 4 | Profile API (steps 25–29) | 2026-10-08 | `SECURITY-FINDINGS.md`, "Audit — 2026-10-08" |
| 5 | Profile UI (steps 30–40) | — | — |

Likes UI (real counts, `/liked`, the stub removed): phase 5 of `../twitter_timeline_service/PLAN.md`.

## Phases 4–5 — Profile view

Brief: `profile-view-design.md` (§-numbers below). Designed via `/grill-me` 2026-10-08 (Q40–Q59). Phase 4 makes the API
complete; phase 5 builds the UI on it and starts only after phase 4's final gate passes.

### What exists and what is missing

| Need | Today | Change |
|---|---|---|
| Profile read | `GET /api/v1/users/{username}`: bio, location, `createdAt`, counts, `followedByMe`, picture | none |
| Email (edit sheet) | `GET /api/v1/auth/me` → the session's `AuthUser` | none (read from `AuthContext`) |
| Save bio, location | `PUT /api/v1/users/me`, full replace (a missing field is wiped), `@Size` counts UTF-16 units, location 30 | `PATCH`, partial, code points, location 60 (step 25) |
| Photo | `PUT /api/v1/users/me/profile-picture`, JPEG / PNG / WEBP ≤ 5 MB | none (Q46) |
| One person's tweets | internal `by-author?since=&limit=` for back-fill only (ids and `createdAt`) | paged internal read (step 26) + timeline `author-tweets` (27, 28) |
| Tweet count | nothing | `GET /api/v1/tweets/count?authorId=` (step 26) |
| Menu entry | `SAVED TWEETS`, `LIKED TWEETS`, `LOG OUT` | `PROFILE` first (step 35) |

### Design in short

- **Route (Q40, Q49):** `/users/:username` for anyone; `EDIT` only when the username is yours (case-insensitive), `FOLLOW`
  otherwise (Q50). A third non-tab panel of `TabPanels`, like `/tweets/:tweetId`: feed and People stay mounted, Back
  lands on the same spot, no tab row. `useActiveTab` must not read `/users/x` as PEOPLE.
- **Ways in (Q41, Q42):** a post cell's author name and avatar are links (the cell's open-the-tweet click already skips
  links); a People card opens the profile on a click anywhere except `FOLLOW`, and its name is a real link for keyboard
  and screen readers. Menu: `PROFILE` first (Q55). Reply authors stay plain (gap).
- **Loading (Q43, Q44):** profile first; then the tweet count and the first page of `author-tweets/{id}` in parallel.
  `PostList` unchanged, `fetchPage` bound to the profile's id.
- **Masthead (Q51):** username once in the name slot, no handle line; initials from the username; empty bio / location
  omitted (§3); `JOINED OCT 2026`. Stats not clickable (Q56); `TWEETS · n` in the stats and the section header.
- **Save (Q45, Q47):** `PATCH` with only the changed fields; nothing changed → close, no request. A picked photo is a change:
  preview at once, uploaded on `SAVE` after the `PATCH`. Text saved but photo failed → sheet stays open, error by the
  photo row, the text counts as saved.
- **After a new photo (Q48):** masthead and header avatar update at once (setter through `Shell`'s outlet context); posts
  already on screen keep the old picture until a reload — **a deliberate shortcut**, recorded under Accepted gaps and
  in `CLAUDE.md`.
- **Discard (Q53):** `CANCEL` turns into `DISCARD CHANGES?` for 3 s; a second `CANCEL` or `Escape` discards; blur or timeout
  disarms (the `UNFOLLOW?` pattern).
- **Own posts (Q52):** on your own profile a post you publish shows at the top at once and the count goes up by 1
  (`ownPosts`). Count = the server's count read on open + your posts published while the page is open.
- **Not found (Q54):** `404` → `USER NOT FOUND` (`PostListStatus`, like `POST NOT FOUND`); other failures → `SIGNAL LOST` /
  `TRY AGAIN`.
- **Limits (Q46, Q58):** bio ≤ 160, location ≤ 60, both trimmed and counted in code points on both sides; photo JPG / PNG /
  WEBP ≤ 5 MB, hint `JPG, PNG OR WEBP · MAX 5 MB` (departs from §4's `JPG OR PNG · MAX 2 MB`). No cropping: centred
  square via `object-fit: cover` (§5).
- **Birthdate (Q59):** untouched; `PATCH` still accepts it, the UI neither shows nor sends it.

### What was built (phase 4, Profile API, steps 25–29)

- **Gateway `PATCH /api/v1/users/me` (Q45, Q46, Q58):** replaces the `PUT` (now `405`). A missing or `null` field is kept
  (a birthdate can't be cleared), a blank bio or location clears it, values are stripped. `@MaxCodePoints` (bio 160,
  location 60) counts code points after stripping; `User.location` is `varchar(60)`. Local databases need
  `ALTER TABLE users ALTER COLUMN location TYPE varchar(60);`.
- **Tweet service (Q43, Q44):** internal `GET /internal/v1/tweets/by-author/{authorId}/page?cursor=&size=` (whole tweets,
  newest first on `ix_tweets_author_created_id`, keyset cursor shared with replies as `KeysetCursor`, size 1–100,
  default 20, bad cursor `400`); `GET /api/v1/tweets/count?authorId=` → `{"count": n}`, unknown author `0`, non-UUID
  `400`. The back-fill read is unchanged. Tweet DTOs moved to `DTOs/response/tweets`.
- **Timeline:** `GET /api/v1/author-tweets/{authorId}?cursor=&size=` returns feed-shaped items (`assembleFetched`: likes,
  `likedByMe`, `savedByMe`, `replyCount`, author), the tweet service's cursor passed through; unknown or unconfirmed
  author `404` before any tweet is read; a tweet-service `400` becomes `400 Invalid cursor.`; downstream failures
  `502` / `504`. `DTOs/client` regrouped into `users`, `follows`, `tweets`.
- **Gateway route** `/api/v1/author-tweets/*` to the timeline service, in the Identity group (`X-User-Id` set, a forged
  one replaced, cookie and `Authorization` dropped).
- **Tests:** gateway, tweet and timeline `mvn verify` green 3× (scenario lists in each `TESTING.md`); `e2e/tests/profile.spec.ts`
  (9 API scenarios) and the whole e2e suite 67/67 green 3× from a fresh stack.
- **Audit (2026-10-08, `SECURITY-FINDINGS.md`):** one Medium (no rate limit, accepted gap below), three Lows (under
  Left open). Step 29's gate was waived for the Medium.

### Steps — phase 5, Profile UI

30. **Foundation.** `ROUTES.profile` (`/users/:username`) + `profilePath`; `ENDPOINTS.me`, `authorTweets`, `tweetCount`,
    every segment encoded; `UserProfile` gains `bio`, `location`, `createdAt`, the counts, `followedByMe`;
    `updateProfile` (`PATCH`, only the given fields), `uploadProfilePicture`, `fetchTweetCount`, `fetchAuthorTweets`.
    Unit-tested on the stubbed `fetch`. **Gate:** build, lint, test clean.
31. **Route and panel (Q49).** `TabPanels` renders `ProfilePage` as a third non-tab panel keyed by the username;
    `useOpenUsername` (a username that fails the registration pattern shows `USER NOT FOUND` without a request);
    `useActiveTab` no longer matches `/users/x`; scroll to top on open; Back restores the feed's place
    (`useTabScrollMemory`). **Gate:** component tests green (`TabPanels`, the hooks).
32. **Read view (§3, Q51, Q54, Q56).** Masthead, stats (full accessible text: "1,204 followers"), `TWEETS · n` header,
    `PostList` on `fetchAuthorTweets`, `END OF TWEETS`, `NO TWEETS YET`, `USER NOT FOUND`, `SIGNAL LOST`; omitted empty
    fields; ≤ 620 px wrap; contrast of `.40` against `--bg` checked (raise to `.52` as elsewhere if under 4.5:1).
    **Gate:** component tests green.
33. **Follow on other profiles (Q50).** `FollowButton` and `useFollowPhase` move from `PersonCard` to a shared place; the
    profile shows it in the `EDIT` spot; followers count = server + your change; `onFollowChanged` fires. People tests
    unchanged in behaviour. **Gate:** component tests green.
34. **Links in (Q41, Q42).** `PostCell` author name and avatar → `profilePath` links; `PersonCard` click-anywhere
    except `FOLLOW` (and the end of a text selection), name is a link. `CLAUDE.md`'s "cards are not links" rewritten.
    **Gate:** component tests green.
35. **Menu (Q55).** `PROFILE` first in `UserMenu` → your profile; arrow-key order and tests updated.
    **Gate:** component tests green.
36. **Edit sheet, text fields (§4, §5, Q45, Q53).** `role="dialog"`, `aria-modal`, focus trap, scroll lock (`data-scroll-locked`
    as compose), bio autofocus after ~80 ms, focus back to `EDIT`; `EMAIL` and `USERNAME` as static text with `LOCKED` and
    an accessible reason; bio (trimmed code-point counter, `--alarm` past 160, `aria-live` only on crossing) and location
    (≤ 60); dirty tracking, only changed fields sent, no change → close; pending `SAVE`; field errors under the field;
    `DISCARD CHANGES?` (the 3 s arm hook shared with step 33's `useFollowPhase`); masthead updated in place.
    **Gate:** component tests green.
37. **Photo (Q46, Q47, Q48).** `CHANGE PHOTO` → file picker; type and 5 MB checked on pick, error by the row; object-URL
    preview (revoked on close and replace); upload after the `PATCH` on `SAVE`; partial failure as in Design; `Shell` gets
    a picture setter in outlet context (replaces the read-once in `useProfilePicture`), so the header avatar follows.
    **Gate:** component tests green.
38. **Own posts and the count (Q52).** Your own profile passes `ownPosts`; the count adds the posts published while the
    page is open. **Gate:** component tests green.
39. **[e2e] UI journeys.** `profile-ui.spec.ts`: (1) `PROFILE` → your profile, empty bio and location omitted; (2) edit bio
    and location → masthead updates, survives a reload; (3) change only the location → the request body holds only
    `location`, the bio stays; (4) dirty `Escape` → `DISCARD CHANGES?`; (5) upload a photo → masthead and header avatar
    change; (6) click an author in the feed → their profile, `FOLLOW` works, Back → same post in view; (7) People card
    click → profile, `FOLLOW` on the card doesn't navigate; (8) `/users/nobody_here` → `USER NOT FOUND`; (9) 25 tweets →
    second page, `END OF TWEETS`, `TWEETS · 25`; (10) post from your profile → top of the list, count + 1.
    **Gate:** green 3× in a row from a fresh stack.
40. **Manual check and finish.** Visual check at 1440 / 620 / 320 px, reduced motion on and off, safe areas; style review
    of every touched file; `exploit-hunter` on the profile UI; `CLAUDE.md` (route, panel, links, the avatar shortcut under
    "Things that are easy to break"); `DECISIONS.md`; root `PLAN.md` line. **Gate:** the user signs off the visual check;
    no style violations; nothing above Low open.

### Accepted gaps (phases 4–5) — revisit when the named trigger lands

- **Shortcut: your posts already on screen keep your old picture after a photo change (Q48)** until a reload; only the
  masthead and the header avatar update. **Trigger:** a report of mismatched avatars → an author-picture override in the
  lists, or a refetch of the mounted lists.
- **Reply authors aren't links (Q41).** **Trigger:** wanting to visit a replier's profile.
- **Profile → tweet → Back reloads the profile from the top (Q49),** as `/saved` and `/liked` do. **Trigger:** long
  profiles where the lost place hurts → keep the profile panel mounted.
- **Follower and following counts aren't clickable (Q56);** the API lists exist. **Trigger:** wanting to see who
  follows whom → a list view designed.
- **No "remove photo"** (Q47); `DELETE /me/profile-picture` exists. **Trigger:** a user asks to go back to initials.
- **The tweet count is read on open** plus your own posts while the page is open; other changes show after a reload.
  One count query per open (indexed). **Trigger:** load concern → a stored counter fed by `tweet.created` /
  `tweet.deleted`.
- **The paged by-author read has no secret,** like the tweet service's other internal reads. **Trigger:** that service's
  network-trust trigger.
- **No `NEW POSTS` check on a profile.** **Trigger:** people keep a profile open to watch it.
- **No display names (Q51);** phase 2's gap holds.
- **Shortcut: no rate limit on the proxied read routes** (audit 2026-10-08, Medium; `SECURITY-FINDINGS.md`). One
  `author-tweets` request with `size=100` fans out to three services and four queries, and `/tweets/count` counts every
  tweet of an author; the feed has the same exposure. **Trigger:** before any public deployment → a per-user limit at
  the gateway on all proxied read routes.

### Out of scope (phases 4–5)

Brief §7; display names; follower / following lists; reply-author links; removing the photo; the cover picture (API
exists, no UI); deleting your own tweets (the API exists, no UI); a birthdate UI; search.

### Interview record (phases 4–5, `/grill-me`, 2026-10-08)

| Q | Question | Answer |
|---|---|---|
| 40 | Whose profiles can be opened? | Anyone's, `/users/:username`; `EDIT` only on your own |
| 41 | Ways in to someone's profile | A post's author, a People card; reply authors later |
| 42 | What exactly is clickable | Author name and avatar are links; a card opens on any click but `FOLLOW`, its name a link |
| 43 | Which service serves a person's tweets | Timeline `author-tweets`, built like the feed, on a new paged tweet-service read |
| 44 | Where the tweet count comes from | Counted on request by the tweet service |
| 45 | How `SAVE` talks to the server | `PATCH`, only changed fields; the `PUT` goes |
| 46 | Brief vs server limits | Location raised to 60 on the server; photo stays 5 MB, JPG / PNG / WEBP |
| 47 | When the photo uploads | On `SAVE`; no "remove photo" |
| 48 | What updates after a new photo | Masthead and header at once; posts after reload — written down as a shortcut |
| 49 | Back after a profile | Third non-tab panel; feed keeps its place |
| 50 | The `EDIT` spot on someone else's profile | The existing `FOLLOW` button |
| 51 | Name and handle with only a username | Username once; no full-name field |
| 52 | Posting from your own profile | Top of the list at once, count + 1 |
| 53 | Discard unsaved changes | `DISCARD CHANGES?` in place, 3 s |
| 54 | Unknown user | `USER NOT FOUND` |
| 55 | Menu item | `PROFILE`, first |
| 56 | Clickable stats | No |
| 57 | Phases and plan file | API phase 4, UI phase 5, in this file |
| 58 | Counting bio / location length | Code points on the gateway too |
| 59 | Birthdate visible to others | Leave it: a birthdate you set is meant to be public |
| — | `(my call)` | A malformed username shows `USER NOT FOUND` without a request; `null` in the `PATCH` keeps (so a birthdate can't be cleared); `{"count": n}`, unknown author `0`; unknown author on `author-tweets` `404`; 20 per page; `END OF TWEETS` / `NO TWEETS YET`; `JOINED OCT 2026`; `FollowButton` moves to shared; one arm hook for `UNFOLLOW?` and `DISCARD CHANGES?` |

Questions that needed re-asking: Q51 (the first wording used the brief's terms "display name" and "handle").

---

## Phase 3 — Tab row, People, back-fill on follow ✅ **Done** (2026-10-04)

Brief: `feed-design-addition.md` (§-numbers below). Inputs: the gateway's phase 5 handoff
(`../twitter_api_gateway/PLAN.md`) and the timeline service's gap "No back-fill on follow", whose trigger ("following
someone feels like nothing happened") this phase would make real (Q24). Steps 14–17 make back-fill work through the API;
steps 18–23 build the UI on top.

### What was built (phase 3, tab row, People, back-fill on follow)

- **Back-fill, tweet service (step 14):** `GET /internal/v1/tweets/by-author/{authorId}?since=&limit=` (newest first,
  `limit` 1–100, `since` required, only `id` and `createdAt`), index `ix_tweets_author_created_id {authorId: 1,
  createdAt: -1, _id: -1}` (the audit's fix: without `_id` the sort read every tweet of the author).
- **Back-fill, gateway (step 15):** `GET /internal/v1/users/{followerId}/follows/{followeeId}` behind the internal secret,
  answering `200 {"following": true|false}` for every well-formed pair (the audit's fix: a `404` can only mean a wrong
  secret or a missing route). Not proxied to the public edge.
- **Back-fill, timeline (step 16, Q33, Q34):** `UserFollowedListener` in the existing group on `user.followed`, with its own
  container factory and DLT `user.followed-timeline-dlt`; the newest 50 (`FEED_BACKFILL_SIZE`) of the last 7 days are
  inserted, then the follow is checked, and the rows are undone when it is gone (no window left); every step is
  idempotent, so a retry from the top is safe. A tweet deleted in between leaves an orphan row the feed skips.
- **E2E back-fill (step 17):** `backfill.spec.ts` (back-fill newest first; follow then unfollow at once leaves nothing,
  proven by a third user on the same partition); `feed.spec.ts`'s re-follow test now expects the older tweet back.
- **Routes and layout (Q25, Q26):** `/feed` = TWEETS, `/users` = PEOPLE (`ROUTES.people`) in one layout route,
  `TabPanels`, inside `Shell`; `/saved` has no tab row. Header and tab row stick as one block (`.shell-top`);
  `--tab-row-height`, `--pale-bio`, `--pale-tab-hover` are the only new tokens.
- **Tab row and panels:** `role="tablist"`, roving focus, arrows switch at once and wrap, a click pushes history, the
  active tab is read from the address; one measured indicator (`offsetLeft`, `offsetWidth`, re-measured on `resize` and
  after `document.fonts.ready`); both panels are in the DOM, their pages mount on first visit and stay mounted, so the
  feed keeps its list, its place (scroll kept per tab) and its 60 s `NEW POSTS` check; the incoming panel plays an
  entrance (`data-entering`).
- **Shared paging (Q37):** `usePagedList` (`src/hooks`) holds the paging both lists use; `PageRequest` / `pageUrl` live in
  `src/api/paging.ts`; the list helpers are generic. `Avatar`, `PostListStatus`, `useBottomSentinel` and
  `useOptimisticToggle` moved to shared places.
- **People list (Q27, Q36):** `GET /api/v1/users`, 20 per page, the feed's paging, bottom states `FETCHING MORE` /
  `SIGNAL LOST` / `END OF PEOPLE` / `NO ONE ELSE HERE YET`, live region `n more people loaded`; the cards sink on sign-out.
- **Person card (Q30–Q32):** avatar 44 / 40 px, username once (no `@handle`), bio cut by `truncateBio` (50 code points, at
  a word, `…`), an empty bio keeps its line, `formatFollowers` (K / M, rounded down, singular for 1); not a link.
- **Follow button (Q28, Q29):** optimistic with the count, one request at a time, last click wins; unfollowing takes two
  taps (`UNFOLLOW?`, 3 s, blur and Escape disarm); a failure goes back, shows `TRY AGAIN` for 3 s and announces it, and a
  tap repeats the change; the accessible name stays `Follow <username>` with `aria-pressed`.
- **TWEETS after following (Q35):** an empty feed says `NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS` with a
  `FIND PEOPLE` button to PEOPLE; coming back after a follow change reads an empty feed again (once per change), a feed
  with posts keeps its place and the 60 s check offers `NEW POSTS`. `Shell` carries the follow signal (`onFollowChanged`,
  `followChangeCount`) through outlet context.
- **Tests:** 866 Vitest tests (the card, the button's timers, the list, the tab panels, the empty-feed reload, the API
  and helpers); Playwright 40/40 three times in a row from a fresh stack (`backfill.spec.ts`, `people-ui.spec.ts`, the
  8 UI journeys, Q38); `mvn verify` green 3× in the tweet service (267), the gateway (844) and the timeline service
  (496). Scenario lists: the backend `TESTING.md` files; the frontend and e2e specs.
- **Audit:** no Critical or High; two Mediums found and fixed, the Lows are under Left open
  (`SECURITY-FINDINGS.md`, "Audit — 2026-10-04 (phase 3 ...)", and `SECURITY-AUDITS.md` in the
  gateway, and the audit reports in the tweet and timeline services). Closed: phase 2's "A new user can't follow anyone" and the gateway's "No
  frontend caller yet".
- **Waived:** step 24's visual check (see Left open).

### Accepted gaps (phase 3) — revisit when the named trigger lands

- **Back-fill is the newest 50 of the last 7 days (Q33).** **Trigger:** readers scroll past it and ask for more → a paged
  read.
- **Follow → unfollow → follow within seconds can lose the back-fill.** Posts go missing, never show wrongly; new posts
  still arrive. **Trigger:** a report of a missing back-fill → a per-pair sequence number on the follow events.
- **Back-filled posts below your top post show only on the next full load (Q35),** and a return to an empty feed within
  ~3–5 s of a follow shows it empty until the next `NEW POSTS` check. **Trigger:** "I followed someone and nothing
  happened" → a feed-changed signal from the timeline service.
- **A back-fill that exhausts its retries sits in `user.followed-timeline-dlt`,** like every other DLT; nothing replays
  it. **Trigger:** a DLT that isn't empty.
- **The tweet service's by-author read has no secret,** like its `ids` read. **Trigger:** that service's network-trust
  trigger.
- **Tab state and scroll positions live in memory:** a reload, a trip to `/saved` or a sign-out starts both tabs fresh.
- **Cards aren't one height on phones:** a long bio wraps to 2–3 lines (Q30). **Trigger:** the visual check finds it
  uneven → clamp the bio to one line.
- **Counts on cards are a snapshot** plus your own changes; other people's follows show after a reload. The list isn't
  refreshed after a follow, and an account confirmed later appears at the next fresh load (gateway handoff).
- **No search on People (§7.3);** the gateway's `q` gap holds.

### Out of scope (phase 3)

Brief §8; search; profile pages and clickable cards; hiding people you already follow; popularity order; a third tab;
refetching the people list; back-fill beyond 50 posts / 7 days; DLT replay tooling.

### Interview record (phase 3, `/grill-me`, 2026-10-04)

| Q | Question | Answer |
|---|---|---|
| 24 | Following brings none of the person's older posts — fix it in this phase? | Yes: back-fill first, as backend steps of this plan |
| 25 | PEOPLE's address | `/users` (the user's own answer; offered `/people` or `/feed?tab=people`) |
| 26 | Tab row on `/saved` | Hidden |
| 27 | People per page | 20, like the feed |
| 28 | Guard against an accidental unfollow | Two taps in place (`UNFOLLOW?`, 3 s) |
| 29 | A failed follow or unfollow | `TRY AGAIN` in the alarm colour for 3 s, announced |
| 30 | A card without a bio | Keep an empty line |
| 31 | Who cuts the bio | The browser |
| 32 | Follower count format | As listed: rounded down, K / M, singular for 1 |
| 33 | How much back-fill | The newest 50 of the last 7 days |
| 34 | Follow, then a quick unfollow | Check the follow with the gateway before keeping the rows |
| 35 | TWEETS after following | Keep your place; reload an empty feed |
| 36 | Texts | As proposed |
| 37 | Paging code for both lists | One shared hook |
| 38 | E2E list | As proposed |
| 39 | Phase cut | Steps 14–24 in this plan, with the calls below |
| — | `(my call)` | Insert, then check, then undo (no window left); timeline DLT `user.followed-timeline-dlt`; follow button 44 px tall; username once, no `@handle`; .40 / .42 text raised to .52; the header's `sticky` moves to a wrapper; arrow keys switch at once and wrap; cards not clickable; lazy panels kept mounted; one bottom-status component per panel; long bios may wrap on phones |

No question needed re-asking.

---

## What was built (phase 2, feed)

- **Backend, two small changes:** the gateway's like stub, `PUT`/`DELETE /api/v1/likes/{tweetId}` → `204`, stores
  nothing, no service layer (Q1, Q2); the timeline service's `savedByMe` on feed and saved items, one `IN` query per
  page (Q3).
- **Routes:** `/feed` and `/saved` behind `ProtectedRoute` in one layout route, `Shell` (header, compose modal,
  `<Outlet>`); both render `PostList` with a different `fetchPage`; menu = `SAVED TWEETS` + `LOG OUT` (Q4).
- **API layer (`src/api`):** `http.ts` with one `send`, one `ApiError` and a `401` handler the `AuthProvider` registers
  → `signOut` → `/login` (Q14); modules `feed`, `savedTweets`, `likes`, `views`, `tweets`, `users`; picture and image
  URLs built by the client.
- **Post cell:** avatar (real picture, else two letters on a hashed tint, Q13), username once with no `@handle` (Q6),
  relative time re-rendered by one shared minute tick (Q12), body with line breaks and bidi controls stripped (Q21),
  image grid with `Image 1 of 3` alt text (Q10), like and save with `FillIcon` and an optimistic toggle (one request at
  a time, last click wins, silent revert). Like count always shown (Q17); an unsaved post stays on `/saved` (Q5).
- **List:** one hook for both pages, pages appended and deduplicated by id, `500px` sentinel, auto-continue on empty
  pages, polite live region, bottom states `FETCHING MORE` / `END OF …` / `NOTHING HERE YET` / `SIGNAL LOST` (Q11).
- **Views:** a post half visible for 1 s is reported once per page load; batches every 5 s (≤ 50) and on hide with
  `keepalive`; the count is not shown (Q7).
- **`NEW POSTS` (Q19):** `/feed` polls the first page every 60 s (paused while hidden); tap prepends, or reloads from
  the top when the whole page is new.
- **Compose (Q8, Q9, Q18, Q20):** modal with focus trap, scroll lock and a `⌘↵`/`CTRL↵` shortcut, code-point counter,
  image previews with file checks, publish errors next to the button, `DISCARD POST?` confirmation; your post shows at
  the top of `/feed` at once (outlet context).
- **Header and menu:** your picture from `GET /users/{username}` once per shell mount; menu-button pattern with arrow
  keys, Escape and outside click.
- **Look (Q15, Q16):** the login's palette everywhere; faint text raised to `.52`; new tokens and Inter Tight 500 in
  `index.css` and `index.html`; `--header-height` shared by the header and `NEW POSTS`.
- **Tests:** 663 Vitest tests (unit, component, API modules on a stubbed `fetch`, an `IntersectionObserver` double in
  `src/test/setup.ts`); Playwright 28/28 including the 11 scenarios below, green 3× from fresh stacks; both backend
  projects `mvn verify` green 3×.
- **Audit:** no Critical, High or Medium; two Low and two Informational (see Left open).
- **Calls made without a question:** no edit or delete of your own posts; `POST` stays in the header on phones; no
  new dependencies. All logged in `DECISIONS.md` of the project concerned.

## Test strategy

- **Unit (Vitest):** relative time, initials and tint, bidi stripping, compose checks (code points, file rules),
  list merge/dedupe and "is newer" ordering, view batching, every new API module with a stubbed `fetch`.
- **Component (Vitest + RTL, jsdom, fake timers, API modules stubbed with `vi.mock`):** pagination states and
  auto-continue on short pages; like/save optimistic, revert and last-click-wins; menu keyboard and focus; compose
  focus trap, Escape/discard, `⌘/Ctrl+Enter`, previews, errors, prepend; `NEW POSTS` after advancing 60 s; views
  dwell and batching through the observer double; `401` → `/login`. DOM, ARIA and `data-*` state, not pixels.
  Under fake timers advance in stages (`src/test/stepFlowHelpers.ts`); never wait in real time.
- **E2E (Playwright, `../e2e`, `reducedMotion: 'reduce'`, fresh users per test, `expect.poll` for fan-out):**
  1. Post text + 1 image → at the top at once, image loads.
  2. Alice follows Bob; Bob's post is in Alice's feed.
  3. Save, reload → bookmark still filled; the post is on `/saved`.
  4. Unsave on `/saved` → stays with an empty bookmark; gone after reload.
  5. Like → heart filled, count `0` → `1`.
  6. 25 posts → scrolling loads the second page and ends at `END OF FEED`.
  7. Bob posts while Alice is on `/feed` → `NEW POSTS` appears (Playwright's `page.clock` fast-forwards 60 s); tap
     shows Bob's post.
  8. A post kept on screen gets its view counted (checked through `GET /api/v1/views`).
  9. Typing a post and pressing Escape asks `DISCARD POST?`.
  10. Menu: `SAVED TWEETS` → `/saved`; `LOG OUT` → login.
  11. Cookie cleared mid-session → the next action lands on `/login`.

  Fixtures gain an image upload in `postTweet`. The e2e stack rebuilds the gateway and timeline images (steps 1–2).
- **Manual gates:** visual check against the brief at 1440, 600 and 320 px, reduced motion on and off; iPhone
  Safari via `LAN-DEV-SERVER.md` (thumb reach of `POST`, compose keyboard, safe areas).
- **Exit:** `npm run build`, `npm run lint`, `npm test` clean; `npx playwright test` green **3× in a row** from a
  fresh stack; both backend projects' `mvn verify` green 3×.

## Accepted gaps — revisit when the named trigger lands

- **Likes were a stub (Q1, Q2):** closed by phase 5 of `../twitter_timeline_service/PLAN.md` (real counts, `likedByMe`,
  `/liked`).
- **No display names (Q6).** **Trigger:** the backend gets one → bring the `@handle` back.
- **Alt text is generic (Q10).** **Trigger:** the tweet service stores descriptions → a field in compose.
- **No `EDIT ACCOUNT` (Q4):** taken up by phases 4–5 (the profile view, menu item `PROFILE`).
- **View counts aren't shown (Q7).** **Trigger:** the tweet-details page.
- **Views are best-effort:** a failed batch and reports pending when the tab dies are lost.
- **`NEW POSTS` reads a full feed page every 60 s** per open tab, with both downstream calls. **Trigger:** load
  concern → a "count newer than" endpoint.
- **An open draft is lost on session expiry (Q14)** and on reload.
- **`POST` stays in the header on phones.** **Trigger:** the iPhone check finds the thumb reach bad → a bottom trigger
  below `600px`, same ghost treatment and upward fill (§8).
- **Client file checks trust the browser's MIME type;** a renamed file gets the server's `415` message instead.
- **Edited posts aren't marked** (`updatedAt` is ignored). **Trigger:** an edit UI.

## Carried over from phase 1 (still open)

- Visual check of the auth screens at 720 px and 320 px, reduced motion on and off.
- Footer `/login` ↔ `/register` switch remounts instead of animating (brief §5 of the auth design).
- `<meta name="referrer" content="same-origin">` before any deployment (exploit report #1, Low).
- CSP and self-hosted fonts with the first deployment (exploit report #2, Low).
- E2E with motion on (optional).

## Left open

- **Step 12 gate waived (2026-10-04):** the visual check at 1440 / 600 / 320 px, reduced motion on and off, and iPhone
  Safari (thumb reach of `POST`, compose keyboard, safe areas) was not signed off; the user will raise design and
  scalability follow-ups later. `LAN-DEV-SERVER.md`, which `CLAUDE.md` points to, does not exist.
- **No CSP and no referrer policy (Low, `SECURITY-FINDINGS.md` #1, same as exploit report #1/#2 of 2026-10-01):**
  `<meta name="referrer" content="same-origin">` now; a CSP and self-hosted fonts with the first deployment.
- **Gateway session cookie `Secure` defaults to off (Low, `SECURITY-FINDINGS.md` #2):** default to `true` before any
  deployment.
- **Path segments are not encoded in `ENDPOINTS` (Informational, #3).**
- **Style leftovers:** the SVG path in `FillIcon.tsx` and a few test titles in the older `e2e/tests/feed.spec.ts` and
  `views.spec.ts` exceed 120 columns.
- **Phase 3 audit (2026-10-04):** two Mediums found and fixed in step 24 (the tweet service's by-author sort defeated its
  index; the timeline read a gateway `404` for a wrong secret as "unfollowed" and deleted the back-fill, so the follow
  check now answers `200 {"following": ...}`). Still open, all Low unless noted:
  - **Frontend (`SECURITY-FINDINGS.md` #5):** `usePagedList` follows a cursor with no progress check, so a server that
    returns empty pages with a cursor that never advances, or no `nextCursor`, makes it refetch forever. Not reachable
    against the real gateway.
  - **Tweet service** (`twitter_tweet_service/SECURITY-AUDITS.md`): a `since` outside `java.util.Date`'s range
    answers `500` and logs a stack trace.
  - **Timeline** (`twitter_timeline_service/SECURITY-AUDITS.md`): a retry after an unfollow re-inserts rows and a
    dead-lettered event leaves them; no per-pair dampening of follow toggling (2 internal calls + 50 commits per
    event); `FEED_BACKFILL_SIZE` above 100 starts and then dead-letters every follow; events that can never succeed are
    retried ~4 minutes each; a far-past `occurredAt` skips the 7-day window; a forged self-follow event deletes the
    user's own tweets from their feed; a malformed event's text reaches the error log; the poison-event tests cover
    only a missing `occurredAt`.
  - **Gateway:** nothing found on the follow check (`twitter_api_gateway/SECURITY-AUDITS.md`).
- **Phase 4 audit (2026-10-08, `SECURITY-FINDINGS.md`):** Medium logged as a gap (above); open Lows: `PATCH /users/me`
  accepts a `birthdate` outside Postgres's range and `\u0000` in `bio` / `location`, which fail as a `409` / `500` instead
  of `400` (add a lower bound and a control-character check); the birthdate is visible to every logged-in user
  (the form should say so); the tweet and timeline services trust the network for `/internal/**` and `X-User-Id`.
- **Phase 3 style leftovers:** the sign-out leave animation is copied in `PeopleList.css` and `PostList.css`, and
  `tab-row-fade` / `tab-row-leave` repeat `header-content-fade` / `-leave`; `TabPanels` (shared) imports the two pages;
  `utils/bottomState.ts` imports a type from a component; the `merged.push(... ++ ...)` ternary in `utils/tweetList.ts`;
  long test titles in `e2e/tests/people-ui.spec.ts` and `backfill.spec.ts`, like the older specs.
- **Step 24 gate waived (2026-10-04):** the visual check (1440 / 600 / 320 px, reduced motion on and off, indicator travel, panel entrance, card grid,
  button row below 600 px, `UNFOLLOW?` / `TRY AGAIN` not resizing the card) was not signed off.
  `LAN-DEV-SERVER.md` still doesn't exist, so the iPhone check needs it written first or is waived.

## Out of scope

Everything in brief §9; edit and delete of your own posts; an image viewer; clickable links or mentions; profile
pages, follow UI, search; the tweet-details page; the edit-account page; frontend containerisation.

## Interview record (`/grill-me`, 2026-10-04)

| Q | Question | Answer |
|---|---|---|
| 1 | Likes have no backend — build them? | Yes, against a stub |
| 2 | Where does the stub live? | Gateway, `PUT`/`DELETE` that do nothing |
| 3 | How does the bookmark know a post is saved? | Timeline service adds `savedByMe` |
| 4 | Which menu items get pages? | `/saved` built; menu = SAVED TWEETS + LOG OUT |
| 5 | Unsave on `/saved` | Post stays, bookmark empties, gone on reload |
| 6 | No display name | Username once, no `@handle` |
| 7 | Report views? | Yes, count not shown (tweet details, later) |
| 8 | Own post after publishing | Shown at the top at once |
| 9 | Attached images | Previews with remove; errors on pick |
| 10 | Image descriptions | "Image 1 of 3" |
| 11 | Bottom-of-list texts | As proposed |
| 12 | Time format | As proposed, updates every minute |
| 13 | Profile pictures | Real pictures, letters as fallback |
| 14 | Login expires mid-session | Go to the login page |
| 15 | Two background blues | The login's everywhere |
| 16 | Too-faint small text | Raised to the login's `.52` |
| 17 | Like count at 0 | Always shown |
| 18 | Closing with a draft | Ask "Discard post?" |
| 19 | New posts while reading | `NEW POSTS` button, 60 s check |
| 20 | Compose texts | As proposed |
| 21 | Hidden direction characters | Stripped |
| 22 | E2E list | As proposed |
| 23 | Plan file | Fresh plan in `frontend/PLAN.md` |

Questions that needed re-asking: Q7, Q8, Q15 (the first wording explained mechanics instead of asking what the user
sees).
