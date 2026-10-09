Read the repo-level `../CLAUDE.md` first (frontend code rules and the `frontend-code-style` skill). This file covers
running and testing this app.

## What this is

The Twitter SPA: the "Hadal Descent" auth flow (`/login`, `/register`, `/confirm`, `/resend`), the feed
(`/feed`, `/saved`, `/liked`), the People list (`/users`), a post's details with its replies (`/tweets/:tweetId`) and profiles (`/users/:username`). Vite 8, React 19, TypeScript (strict), react-router-dom 7, plain
co-located CSS. Plans and design decisions: `PLAN.md`, `DECISIONS.md`; the visual briefs: `AuthenticationViewsDesigns.md`
(auth), `feed-design.md` (feed, in git history), `feed-design-addition.md` (tab row, People), `profile-view-design.md` (profile).

`/feed`, `/users`, `/saved` and `/liked` sit behind `ProtectedRoute` in one layout route, `Shell` (header, tab row, compose modal,
`<Outlet>`). `/feed` (TWEETS) and `/users` (PEOPLE) share one more layout route, `TabPanels`, which renders both pages as
`role="tabpanel"` sections; `/saved` and `/liked` have no tab row. `/feed`, `/saved` and `/liked` render `PostList` with a different `fetchPage`;
`/users` renders `PeopleList`. Both lists page through `usePagedList` (`src/hooks`). The shell hands the page what is
shared through outlet context (`useShellContext()`): the posts you published this session (`ownPosts`) and the follow
signal (`onFollowChanged`, `followChangeCount`).

`/tweets/:tweetId` is a third, non-tab panel of `TabPanels` (no tab row): the feed and People panels stay mounted and
hidden while it is open, so Back lands on the same spot. `TweetDetailsPage` shows the back link, the post (not
clickable), the reply composer and the replies (`useReplyThread`, oldest first, paged like the lists). A post cell opens
it on a click anywhere except on a button, link or image or at the end of a text selection; the reply link in the
action row is the keyboard and screen-reader way in.

`/users/:username` is a fourth panel of the same kind: `ProfilePage` for anyone's profile, keyed by the username, with
the feed and People mounted and hidden under it. `useOpenUsername` returns the name only when it matches the
registration pattern (it goes into API paths); anything else shows `USER NOT FOUND` without a request, and `useActiveTab`
does not read `/users/x` as PEOPLE. The profile loads first, then the tweet count and the first page of
`author-tweets/{id}` in parallel (`useProfile`, `ProfileTweets` on `PostList`). `EDIT` shows only when the username is
yours (case-insensitive), otherwise the shared `FollowButton`. Back from a profile lands on the same spot of the feed;
profile → tweet → Back reloads the profile from the top.

## Running it

- `npm install`, then `npm run dev`: the dev server on `:5173`. `/api` is proxied to the gateway on `:8080`, or to
  `GATEWAY_URL` when set. Start the gateway first (see `../twitter_api_gateway/CLAUDE.md`).
- Local confirmation links: the gateway queues the link in its `outbox` table and the mail service
  (`../twitter_mail_service`) emails it. Start `redis` and `mailpit` from the Twitter root, run the mail service
  with `MAIL_HOST=localhost MAIL_PORT=1025 MAIL_SMTP_AUTH=false MAIL_SMTP_STARTTLS=false`, and read the email at
  `http://localhost:8025`. Without the mail service, read the link from the table:
  `select payload::jsonb->>'confirmationUrl' from outbox order by created_at desc limit 1;`. The base is
  `CONFIRMATION_LINK_BASE_URL` in `../.env` (`http://localhost:5173/confirm`).
- On a phone: `LAN-DEV-SERVER.md`.

## Checks

- `npm run build` (typecheck plus bundle), `npm run lint` (oxlint), `npm test` (Vitest).
- Component tests run with jsdom, Testing Library and fake timers, and stub `src/api/*` modules with `vi.mock`. Never
  wait in real time. Under fake timers, advance the clock in stages (`src/test/stepFlowHelpers.ts`); `src/test/setup.ts`
  carries the `jest` global shim that user-event needs.
- jsdom has no `IntersectionObserver`: `src/test/setup.ts` installs a controllable double (`src/test/intersectionObserver.ts`)
  that tests drive by hand to show a sentinel or a post. It also stubs `URL.createObjectURL`, `URL.revokeObjectURL` and
  `window.scrollTo`. List helpers: `src/test/postListHelpers.ts`; a stubbed `fetch`: `src/test/fetchStub.ts`.
- Names are `should_..._when_...`. Test the DOM, ARIA and `data-*` state, not pixels.
- End-to-end: `../e2e` (Playwright against a Docker stack with the real gateway). `npm test` there builds and starts
  the stack, runs the suite and tears it down; `npm run test:fast` skips the image build. The UI specs for People are
  `tests/people-ui.spec.ts`: a card is found by scrolling PEOPLE until the username shows, because the database is
  shared with the specs running in parallel.

## Things that are easy to break

- The step is held in router location state, not the URL, so Back, `history.back()` and refresh share one path.
  A reload on a later step resets to step 1.
- `STEP_SWAP_MS` (`useStepTransition.ts`) must match the 400 ms opacity in `StepFlow.css`. The login arrival delay
  must match the arrival rule's 150 ms + 1300 ms in `Arrival.css`; `STAGE_LEAVE_MS` (`LoginPage.tsx`) must match the 500 ms
  fade in `DescentStage.css`, after which `signIn` mounts the feed, whose header rule and post entrance take over.
  Signing out runs it in reverse: `SIGN_OUT_LEAVE_MS` (`AuthContext.tsx`) must cover the 1200 ms header rule retract and
  post leave in `Header.css` and `PostList.css`; `ProtectedRoute` then hands the login page `isAscending`, which fades the
  stage in with the gauge rising from the seafloor (`ASCENT_START_DELAY_MS`, `LoginPage.tsx`).
- The form ignores Enter while a step is sliding in, so e2e helpers wait for `data-slide="idle"`.
- Validation rules mirror the gateway's `RegisterRequestDTO`; change both sides together.
- Every request goes through `src/api/http.ts`. A `401` from a non-auth endpoint calls the handler `AuthProvider`
  registers, which signs out and lands on `/login`; auth endpoints keep their own `401` meaning.
- Likes are real: a post opens with the server's `likedByMe` and `likes`, and `PostActions` shows `formatCount(max(0,
  likes − (liked on load ? 1 : 0) + (liked now ? 1 : 0)))`: the server's count plus your own change, never below `0`.
  Other people's likes on posts already shown appear after a reload. `/liked` (menu `LIKED TWEETS`, no tab row) lists
  your own likes with `PostList`; an unliked post stays there, heart empty, until a reload.
- Views: `ViewReporter` (`src/utils/viewReporter.ts`) is a module-level singleton that queues post ids (once per page
  load, after 1 s at half visible) and posts them every 5 s and on hide. Tests reset it.
- `--header-height` and `--tab-row-height` in `src/index.css` are shared by the header, the tab row and the sticky
  `NEW POSTS` button (its offset is both plus a gap); the header and the tab row stick as one block, `.shell-top`. The
  compose modal locks scrolling with `data-scroll-locked` on `<html>`.
- Tabs: the tab is read from the address (`useActiveTab`), and a tab click pushes a history entry. A panel's page mounts
  the first time its tab opens and stays mounted (hidden) until the layout unmounts, so the feed keeps its list and its
  60 s `NEW POSTS` check while PEOPLE is open. The indicator is measured (`offsetLeft`, `offsetWidth`) on a tab change,
  on `resize` and after `document.fonts.ready`. Scroll positions are kept per
  tab in memory from `scroll` events (refreshed on `click` and `keydown`, in the capture phase), because by the time of
  the switch the leaving panel is already hidden.
- The follow button holds two 3 s looks, `armed` (`UNFOLLOW?`, a second tap unfollows; blur and Escape disarm) and
  `failed` (`TRY AGAIN`): `PHASE_HOLD_MS` in `useFollowPhase.ts`. A person card's count is the server's
  `followersCount` plus your own change, so a revert restores it. Following goes through `useOptimisticToggle` (one
  request at a time, the last click wins) like likes and saves, but with a failure callback.
- A follow change reaches the feed through `Shell` (`followChangeCount`): `FeedPage` passes it, as it was the last time
  TWEETS was shown, to `PostList` as `reloadEmptyKey`, and an empty feed reads again when the tab comes back. Back-fill
  lands 3–5 s after a follow (the gateway outbox polls every 3 s), so a very quick return still finds it empty until
  the next `NEW POSTS` check.
- Signing out sinks the cards of both lists: `person-list-leave` (`PeopleList.css`) and `post-list-leave`
  (`PostList.css`) must stay inside `SIGN_OUT_LEAVE_MS`.
- Person cards are cut in the browser: `truncateBio` (50 code points, bidi controls stripped) and `formatCount`.
  A card opens the person's profile on a click anywhere except the follow button, a link or the end of a text selection
  (the same rule as a post cell), and its name is a real link, the way in for the keyboard and screen readers. A post
  cell's author (avatar and name, one link) opens the author's profile too; reply authors stay plain text. Cards show no
  `@handle`.
- The details route: `useOpenTweetId` returns the id only when it is a UUID (it goes into API paths, and a `..` would
  climb out of them); any other id on `/tweets/...` redirects to `/feed`. `ENDPOINTS` encodes every id segment as well.
  Opened from `/saved` or `/liked`, Back reloads that list (only the feed and People stay mounted). The page scrolls
  itself to the top on open; `useTabScrollMemory` keeps the leaving tab's position for the way back, and coming back
  plays no enter animation.
- Back from details keeps the feed's cells in step: `PostUpdatesProvider` (in `Shell`, `src/contexts`) holds what the
  details page changed per post (like state and count, save, reply count) and `PostList` lays it over the loaded
  posts; `PostCell` re-keys `PostActions` when the like or save values change, so its toggles start from the new
  values. **Shortcut:** the updates are kept until the shell unmounts, so a later reload of a list shows them over
  the server's newer numbers.
- The back link goes `navigate(-1)` when the router's `location.key` is not `default`, else to `/feed`.
- Replies: the post's count is the server's `replyCount` plus what you sent minus what you deleted this session
  (`countChange`). Your sent replies sit under the composer until a reload; other people's replies appear after a
  reload. Edits and deletes are overrides on the loaded list, so `usePagedList` is unchanged. `EDIT` and `DELETE` only
  decide which buttons show: the server decides who may.
- The profile: `PATCH /users/me` sends only the changed fields (nothing changed → the sheet closes, no request); bio ≤ 160
  and location ≤ 60 are counted in code points on both sides. A picked photo uploads on `SAVE` after the `PATCH`; if the
  text saved but the photo failed, the sheet stays open and the text counts as saved. On your own profile the count is
  the server's count read on open plus the posts you published after it opened (`useOwnProfilePosts`).
- **Shortcut (Q48):** after a new photo only the masthead and the header avatar update (`onProfilePictureChanged`
  through `Shell`'s outlet context). Posts already on screen keep the old picture until a reload.
