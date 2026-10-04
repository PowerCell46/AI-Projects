# Twitter Frontend — plan

**Status: phase 2 (feed) designed via `/grill-me` on 2026-10-04 (Q-numbers in parentheses); built 2026-10-04.** ✅ **Done** (2026-10-04)
Phase 1 (the "Hadal Descent" auth flow) was built 2026-10-01; its plan is in git history (`git show 95502e1:./PLAN.md`
from this folder) and its still-open items are under "Carried over" below.

Visual source of truth: `feed-design.md` (§-numbers below). This plan records where the build departs from or extends
the brief; everything not mentioned here is built as the brief says.

Two steps touch other projects, marked **[gateway]** and **[timeline]**. They follow that project's `CLAUDE.md` and
standing rules (`java-code-style`, `java-junit`, `TESTING.md`, `DECISIONS.md`, `mvn verify` 3×).

**Hard rule:** no step starts on a red or missing test, each step ends green. Invoke `frontend-code-style` before
touching any `.ts`/`.tsx`/`.css`.

## Status

| Phase | Scope | Done | Audit |
|---|---|---|---|
| 2 | Feed (steps 1–13) | 2026-10-04 | `SECURITY-FINDINGS.md`, "Audit — 2026-10-04" |

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

- **Likes are a stub (Q1, Q2):** nothing stored, every post loads at `0`, a reload forgets your like.
  **Trigger:** a likes service → a read for counts and `likedByMe`, the `LIKED TWEETS` menu item and page.
- **No display names (Q6).** **Trigger:** the backend gets one → bring the `@handle` back.
- **Alt text is generic (Q10).** **Trigger:** the tweet service stores descriptions → a field in compose.
- **No `EDIT ACCOUNT` (Q4).** **Trigger:** an account screen is designed (the backend already exists).
- **View counts aren't shown (Q7).** **Trigger:** the tweet-details page.
- **Views are best-effort:** a failed batch and reports pending when the tab dies are lost.
- **`NEW POSTS` reads a full feed page every 60 s** per open tab, with both downstream calls. **Trigger:** load
  concern → a "count newer than" endpoint.
- **A new user can't follow anyone** — no profile, search or follow UI — so their feed is only their own posts.
  **Trigger:** profiles or search.
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

## Out of scope

Everything in brief §9; edit and delete of your own posts; an image viewer; clickable links or mentions; profile
pages, follow UI, search; the tweet-details page; the edit-account and liked-tweets pages; frontend containerisation.

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
