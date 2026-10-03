# Twitter Frontend — plan

**Status: phase 2 (feed) designed via `/grill-me` on 2026-10-04 (Q-numbers in parentheses); not started.**
Phase 1 (the "Hadal Descent" auth flow) was built 2026-10-01; its plan is in git history (`git show 95502e1:./PLAN.md`
from this folder) and its still-open items are under "Carried over" below.

Visual source of truth: `feed-design.md` (§-numbers below). This plan records where the build departs from or extends
the brief; everything not mentioned here is built as the brief says.

Two steps touch other projects, marked **[gateway]** and **[timeline]**. They follow that project's `CLAUDE.md` and
standing rules (`java-code-style`, `java-junit`, `TESTING.md`, `DECISIONS.md`, `mvn verify` 3×).

**Hard rule:** no step starts on a red or missing test, each step ends green. Invoke `frontend-code-style` before
touching any `.ts`/`.tsx`/`.css`.

## Design in short

### Pages and routes

- **`/feed`** and **`/saved`** (new), both behind `ProtectedRoute`, inside one layout route (the shell): the header,
  the compose modal and an `<Outlet>`. Both pages render the same post list; only the fetch differs (Q4). The brand
  `TWITTER` links to `/feed`.
- The placeholder `FeedPage` (`DescentStage`, `@username`, `LOG OUT`) is replaced; `LOG OUT` moves into the menu.
- `/saved` shows a mono `SAVED TWEETS` label above its list.
- **Menu (Q4):** `SAVED TWEETS` → `/saved`, `LOG OUT`. EDIT ACCOUNT and LIKED TWEETS are left out (accepted gaps).

### What the backend gives (as of 2026-10-04)

| Need | Call | Notes |
|---|---|---|
| Feed page | `GET /api/v1/feed?cursor=&size=` | `{items, nextCursor}`, newest first, own posts included, 7-day retention |
| Saved page | `GET /api/v1/saved-tweets?cursor=&size=` | same shape, `savedAt` order |
| Save / unsave | `PUT` / `DELETE /api/v1/saved-tweets/{tweetId}` | `204`, idempotent |
| Like / unlike | `PUT` / `DELETE /api/v1/likes/{tweetId}` | **stub, step 1** — `204`, stores nothing |
| Report views | `POST /api/v1/views` `{tweetIds}` | `204`, 1–50 ids |
| Publish | `POST /api/v1/tweets` multipart `content` + `images` | `201` `TweetResponseDTO` (no author) |
| Image bytes | `GET /api/v1/tweets/{tweetId}/images/{imageId}` | built by the client; the item lists only image ids |
| Own picture | `GET /api/v1/users/{username}` | `profilePictureUrl`; `me` has no picture |

- Item shape after step 2: `{id, views, savedByMe, content, createdAt, updatedAt, author: {id, username,
  profilePictureUrl}, images: [{id, sizeBytes, contentType}]}`.
- **A short or empty page is not the end** (deleted posts are skipped server-side); only `nextCursor: null` is.
- Picture URLs come back as `/api/v1/files/{id}`; the client prefixes `VITE_BASE_API_URL`.

### Post cell (§3)

- **Author row (Q6):** avatar, username once in the display-name style (Inter Tight 500, white), **no `@handle`**,
  time on the right.
- **Avatars (Q13):** the real picture when `profilePictureUrl` is set (square, `1px` border); otherwise two
  letters on a tint. Letters: the first two letters or digits of the username, uppercased (`peter_g` → `PE`). Tint:
  one of the brief's eight, picked from a hash of the user id, so a person always gets the same one.
- **Times (Q12):** `NOW` (<1 min), `5M`, `3H`, `2D` (<7 days), `12 SEP`, `12 SEP 2025` (other year — only possible
  on `/saved`). One shared one-minute tick re-renders them. `<time datetime="<ISO>">` carries the full value.
- **Body:** line breaks kept (`white-space: pre-wrap`), `dir="auto"`, links not clickable. **Bidi controls stripped
  (Q21)** before rendering: U+202A–U+202E and U+2066–U+2069 (closes the tweet service's "first consumer that renders
  `content`" gap). LRM/RLM, emoji and right-to-left scripts stay.
- **Images:** grid per §3, `object-fit: cover` into the fixed aspect boxes, `loading="lazy"`, no click action.
  **`alt="Image 1 of 3"`** etc. (Q10).
- **Like (Q1, Q2, Q17):** heart + count, the count **always shown, `0` included**. Every post loads at `0`, not
  liked (the stub has no read). A click flips at once and moves the count ±1, then calls the stub.
- **Save (Q3, Q5):** the bookmark's initial state is `savedByMe`. On `/saved` an unsaved post **stays in place** with an
  empty bookmark until the next load.
- **Optimistic toggles (§4):** flip immediately; requests for one post and one action go one at a time, and the last
  click wins (a click while a request is in flight is sent after it if the state differs). A failure reverts
  silently; a `401` goes to login (Q14).
- **Fill icon (§4):** one `FillIcon` component (outline path + clipped solid copy, rising `<rect>`), clipPath ids from
  `useId()`. Heart and bookmark differ only in path and active colour.

### List and pagination (§7, Q11)

- One list hook shared by both pages: items, cursor, status. A busy ref guards against parallel fetches; pages are
  **appended, deduplicated by id**. An `IntersectionObserver` sentinel with a `500px` bottom root margin triggers the
  next fetch. If a page adds nothing visible but has a cursor, the hook fetches the next one right away.
- Polite live region: "5 more posts loaded"; focus never moves on append.
- Bottom-of-list states, mono like the loading text:

| Situation | `/feed` | `/saved` |
|---|---|---|
| Loading | `■ FETCHING MORE` (blinking square, §7) | same |
| `nextCursor: null` | `END OF FEED` | `END OF SAVED TWEETS` |
| No items at all | `NOTHING HERE YET` | `NO SAVED TWEETS YET` |
| Load failed | `SIGNAL LOST` + `TRY AGAIN` button | same |

### Views (Q7)

- Report a post once it has been at least half visible for 1 s, **once per post per page load**, on both pages.
  Queue the ids; `POST /api/v1/views` every 5 s while the queue is non-empty (chunks of ≤50), and on
  `visibilitychange` → hidden / `pagehide` with `fetch(…, { keepalive: true })`. A failed batch is dropped.
- The `views` count is **not rendered**; it belongs on the tweet-details page (out of scope).

### New posts while reading (Q19)

- `/feed` only: every 60 s (paused while the tab is hidden) fetch the first page without a cursor. Items ordered
  before the current top post (keyset order `createdAt DESC, id DESC`) and not already shown count as new.
- If any: a sticky `NEW POSTS` button under the header (mono, `1px` pale border, **no `--glow`** — §9 limits it to
  four roles). Tap: prepend them and scroll to top. If the whole page (20) is new, a gap is possible, so the tap
  reloads the list from the top instead (the one exception to "never replace").

### Compose (§6, Q8, Q9, Q18, Q20)

| Element | Text |
|---|---|
| Eyebrow | `NEW POST` |
| Headline | "What's worth sending up?" |
| Placeholder | "write something" |
| Attach | `ATTACH IMAGE` |
| Counter | `n / 280` |
| Publish | `PUBLISH ⌘↵` (Mac), `PUBLISH CTRL↵` (else) |
| Cancel | `CANCEL` |
| Header button | `POST` (label hidden ≤600px) |

- **Counter:** Unicode code points of the trimmed text (the tweet service's rule). Past 280 it turns `--alarm` and
  Publish is disabled; Publish is also disabled when there is no text and no image.
- **Images (Q9):** previews under the text, each with a labelled remove button (object URLs revoked on remove and
  close). Checked on pick, error next to the attach control, in the server's wording uppercased without the period:
  5th image → `A TWEET CAN HAVE AT MOST 4 IMAGES`; not JPEG/PNG/WebP (browser MIME type) →
  `UNSUPPORTED IMAGE TYPE`; over 5,242,880 bytes → `THE UPLOADED FILE IS TOO LARGE`. The server's magic-byte check
  still decides.
- **Publish errors:** the server's message (uppercased, no period) next to Publish; network/`5xx` →
  `SIGNAL LOST — TRY AGAIN`. The draft stays.
- **Discard (Q18):** Cancel or Escape with an empty draft closes. With text or images, the button row becomes
  `DISCARD POST?` + `DISCARD` (`--alarm`, destructive) + `KEEP EDITING`; Escape there means keep editing. No
  browser dialog.
- **Your new post (Q8):** on `201` the modal closes, focus returns to `POST`, and the post is shown **at the top of
  `/feed` at once**, built from the reply + `me` + your own picture (`views` 0, `savedByMe` false). The shell keeps
  the posts published this session and hands them to the feed (outlet context); the list deduplicates them when the
  fan-out delivers the same id.
- Dialog requirements per §6: focus trap, `role="dialog"`, `aria-modal`, scroll lock, autofocus after 80 ms.

### Header and menu (§5)

- Avatar: your picture from `GET /users/{username}` once per shell mount, letters until it loads or if none.
- Menu: menu-button pattern (`aria-haspopup="menu"`, `aria-expanded`, `role="menu"`/`menuitem`), arrow keys,
  Escape, outside click closes, focus back to the avatar. `LOG OUT` keeps the placeholder's behaviour (`logout` →
  `signOut`; failure shows `SIGNAL LOST` in the menu).

### Session expiry (Q14)

- `src/api/http.ts` (extracted from `auth.ts`): one `send`, one `ApiError` (`AuthApiError` renamed; same `status`
  and `messages`, `0` on a network failure). A `401` from any **non-auth** endpoint calls the handler the
  `AuthProvider` registers → `signOut` → `ProtectedRoute` sends the user to `/login`. Auth endpoints keep their own
  `401` meaning (wrong password, anonymous `me`). An open draft is lost.

### Colours, type, units (Q15, Q16)

- **One palette for the whole app (Q15):** the feed uses the login's `--deep` (`#00081F`) wherever the brief says
  `--bg`, and the login's `--pale` (`#DCE6FA`) as the alpha base. The brief's `#050E22` and `#E2E9F7` are not used.
- New tokens in `src/index.css`: `--cell` `#07142C`, the menu surface `#071634`, `--like` `#FF3B5C`, the brief's
  alphas on the login pale, and the eight avatar tints.
- **Contrast (Q16):** brand `.50`, handles/times `.40`, modal meta and counter `.40` and loading text `.35` fail
  4.5:1 on `--deep`/`--cell` (4.4 / 3.3 / 3.2 / 2.7). All become `.52` (≈4.7:1), the login's `--pale-secondary`.
  Action icons at `.42` pass 3:1 on `--cell` (3.5) and stay. The textarea placeholder follows the login's
  `--pale-placeholder`.
- Inter Tight **500** is added to the Google Fonts link (display names).
- Units per the style skill: rem everywhere, px only for borders, outlines and breakpoints. The brief's `600px`
  breakpoint is used as written (the auth screens keep `720px`).

### Calls made without a question (object if any is wrong)

- Edit and delete of your own posts are not built (the brief has no control for them).
- The `POST` button stays in the header on phones (§8's fallback is a gap with a trigger).
- No new dependencies. jsdom has no `IntersectionObserver`; `src/test/setup.ts` gets a controllable test double.
- **[gateway]** the like stub has no service layer: there's no logic to delegate. Recorded in the gateway's
  `DECISIONS.md`.

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

## Steps

1. **[gateway] Like stub (Q2).** `PUT` and `DELETE /api/v1/likes/{tweetId}` → `204`, nothing stored. Integration
   scenarios: each returns `204`, repeat `204`, non-UUID `400`, no cookie `401` (added to the `Security`
   parameterized paths). `TESTING.md` and `DECISIONS.md` updated in the same change.
   **Gate:** `./mvnw verify` green 3×.
2. **[timeline] `savedByMe` on items (Q3).** `TweetItemAssemblyService.assemble` takes the viewer id; one `IN` query
   on `saved_tweets` per page (as `views`). Feed items true/false per item; saved-list items always true. Scenarios:
   exact key set (shape tests), per-item correctness with some saved, flips after unsave, saved list all true.
   `TESTING.md` updated. **Gate:** `mvn verify` green 3×.
3. **Foundation.** Tokens and font weight (Q15, Q16); `ENDPOINTS` and `ROUTES` (`/saved`); `src/api/http.ts` with
   `ApiError` and the `401` handler (Q14); API modules `feed`, `savedTweets`, `likes`, `views`, `tweets`, `users`,
   unit-tested on a stubbed `fetch`. Existing auth tests renamed to `ApiError`, otherwise unchanged.
   **Gate:** build, lint, test clean; the auth suite unchanged in behaviour.
4. **Pure helpers.** Relative time, initials and tint, bidi stripping, compose checks, list merge/dedupe/newer.
   **Gate:** unit tests green.
5. **Post cell.** Author row with avatar, body, image grid (0–4, alt text), action row, `FillIcon`, optimistic like
   and save. **Gate:** component tests green.
6. **Shell and header.** Layout route, brand, `POST` button (opens nothing yet), avatar with picture fetch, menu
   with `SAVED TWEETS` and `LOG OUT`; the placeholder's tests move to the menu. **Gate:** component tests green.
7. **Post list on `/feed` and `/saved`.** List hook, sentinel, auto-continue, the four bottom states, live region,
   minute tick, unsave-stays on `/saved`. **Gate:** component tests green.
8. **Views reporter.** Observer double, 1 s dwell, once per load, 5 s batches of ≤50, flush on hide.
   **Gate:** component tests green.
9. **Compose modal.** Copy, counter, previews and file checks, publish and errors, discard confirmation, keyboard,
   focus trap, scroll lock, prepend of your own post. **Gate:** component tests green.
10. **`NEW POSTS`.** 60 s poll on `/feed`, pause when hidden, prepend or reload-from-top. **Gate:** component tests
    green.
11. **E2E.** The 11 scenarios above. **Gate:** green 3× in a row from a fresh stack.
12. **Manual check.** 1440 / 600 / 320 px, reduced motion, iPhone. Fix what it finds (each fix with a test where one
    can catch it). **Gate:** the user signs off on the visual check.
13. **Finish.** Style review of every touched file, `exploit-hunter` on the feed, `CLAUDE.md` (routes, the observer
    double, the like stub), root `PLAN.md` line. **Gate:** no style violations; nothing above Low open.

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
