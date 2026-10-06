# Frontend decisions

Calls made while building, kept short. `PLAN.md` holds the design.

## Feed step 3 - one `ApiError`, two senders

`src/api/http.ts` holds `ApiError` (the renamed `AuthApiError`), `send` and `sendAuthenticated`. `send` is what the
auth module uses: a 401 there means wrong credentials or no session, so it is left alone. `sendAuthenticated` wraps
`send` and calls the handler `AuthProvider` registers on a 401, then rethrows, so callers still see the failure.
The API-module rule of one `<Resource>ApiError` per module gives way to the plan's single `ApiError`: every module
shares one failure shape and one 401 rule.

## Feed step 3 - shared list types live in `tweetPage.ts`

`feed.ts` and `savedTweets.ts` return the same `TweetPage`, so the item types, the page request, the query builder and
the picture-URL mapping sit in `src/api/tweetPage.ts`. Picture paths (`/api/v1/files/{id}`) get the API origin at the
API boundary (`toPictureUrl`), so components only ever see a usable URL or `null`.

## Feed step 3 - alpha tokens are named for their job

The feed's alphas on the login pale are named by role (`--pale-post-body`, `--pale-action-icon`, ...). The ones the
brief lists under `.52` are not new tokens: they reuse `--pale-secondary` (Q16). The washes and the 55 % glow border
that the brief lists on the accent and the alarm got `--glow-wash`, `--glow-border` and `--alarm-wash`.

## Feed step 4 - helpers sit in `src/utils`, one file per concern

`relativeTime`, `avatar`, `bidi`, `composeChecks` and `tweetList` (merge, dedupe, "newer"), each with its test file.
Calls the plan left open: a username with no letter or digit (`___` passes the registration pattern) gets the
initials `?`; a post stamped ahead of the browser clock reads `NOW`; timestamps are compared by hand because the
backend writes 0, 3, 6 or 9 fraction digits and the strings do not sort; the pick check names the image count first,
then the type, then the size, in the plan's order.

## Feed step 5 - post cell layout and a few small calls

`PostCell` lives in `src/components/shared/PostCell/` (the list that renders it is shared by `/feed` and `/saved`);
`Avatar`, `PostImages` and `PostActions` (with `FillIcon` and `useOptimisticToggle`) nest inside it. It takes `now`
as a prop, so the one-minute tick of step 7 re-renders the times without the cell owning a timer.
- The like and save buttons are named by a screen-reader-only word (`Like`, `Save`) next to the visible count, not by
  `aria-label`, so the count is part of the like button's name.
- The like count text uses `--pale-secondary` (`.52`), not the icon's `.42`: it is text, so Q16's 4.5:1 applies.
- The press scale sits on a wrapper around the icon, so the button's CSS does not reach into `FillIcon`'s classes.
- Display names use `--display-name`, an alias of the login's white `--input-text`.
- The image grid items are sized boxes with the picture filling them, so a picture's own proportions never set a row
  height; the 3-image layout's tall left item spans two rows.

## Feed step 6 - shell, header and menu

- `Shell` (layout route: header + `<main>` + `<Outlet>`) wraps `/feed` and `/saved` inside `ProtectedRoute`. Until
  step 7 both pages are a visually hidden `<h1>` ("Feed", "Saved tweets"), so the menu link has a real target.
- The `POST` button has no handler yet (it opens the compose modal in step 9). Its accessible name is `Post`, so it
  survives the label being hidden below 600 px.
- Touch targets win over the brief's sizes: the `POST` button and the avatar trigger are at least 2.75rem square (the
  brief has 34 px and 32 px); the avatar itself stays 2rem inside the trigger.
- The menu panel is `role="menu"` with only `menuitem` children; the `SIGNAL LOST` alert sits beside it in the same
  panel, so the menu role stays valid. Opening focuses the first item (the last one on `ArrowUp` from the trigger);
  Escape, Tab and choosing `SAVED TWEETS` close it and return focus to the avatar, an outside click only closes it.
- `FeedPage.test.tsx` and `FeedPage.css` are gone: the placeholder's logout tests live in `UserMenu.test.tsx`,
  unchanged in what they assert apart from reaching `LOG OUT` through the menu.
- `UserMenu.tsx` is about 170 lines because it holds the open/close, focus and arrow-key logic of the menu; it is
  cohesive, so it is not split yet. Extract a hook if a second menu needs the same behaviour.

## Feed step 7 - the post list

- `PostList` (with `usePostList`, `useBottomSentinel`, `useMinuteClock`, `PostListStatus`) lives in
  `src/components/shared/PostList/`; `PostCell` moved inside it, its only consumer. Both pages pass their fetcher and
  their two texts; `/saved` also gets its mono `SAVED TWEETS` heading, `/feed` keeps a hidden `Feed` heading.
- The first page is read in an effect on mount, not by waiting for the sentinel, so it does not depend on a hidden tab
  delivering an observer callback. The effect carries two documented lint disables (set-state-in-effect, deps).
- The sentinel observer is rebuilt after every page and every status change, because a browser only reports a change:
  a sentinel still near the bottom after an append would otherwise never ask for the next page.
- After a failure the list asks for nothing by itself; only `TRY AGAIN` (same cursor) continues. Otherwise a visible
  sentinel would retry in a loop.
- The live region says `n more posts loaded` for appended pages only (not the first page) and is emptied when the next
  fetch starts, so two pages of the same size are both announced.
- Test infra: `src/test/intersectionObserver.ts` is the controllable double the plan names (records options and
  targets, `intersect(target, ratio)` reports inside `act`); `src/test/setup.ts` stubs it before every test, because
  some test files call `vi.unstubAllGlobals()`.

## Feed step 8 - the views reporter

- `src/utils/viewReporter.ts` holds `ViewReporter` (queue, once-per-id set, 5 s batch timer, chunks of at most 50,
  `flush`) and one shared `viewReporter` instance. It is module-level on purpose: "once per post per page load" spans
  `/feed` and `/saved`, which are two mounts of the same page load.
- `useViewTracking` (next to `PostList`) watches each list item with one observer at threshold `0.5`, times a
  1 s dwell per item and calls `record`; `visibilitychange` (hidden) and `pagehide` flush with `keepalive`. The hook
  takes the reporter as a parameter so its tests use a fresh one.
- A failed batch is dropped and its posts stay marked as reported, as the plan says.
- Accepted edge: a post taller than twice the viewport can never be half visible, so it is never counted.

## Feed step 9 - compose modal

- `ComposeModal` lives in `Shell/ComposeModal/` and is mounted only while open, so every open starts with an empty
  draft. Its concerns are split into hooks: `useImageAttachments` (picks, checks, preview URLs, released on remove and
  close), `usePublish`, `useFocusTrap`, `useScrollLock`.
- The shell owns the open state and the posts published this session (`ownPosts`, handed to the pages through outlet
  context, `useShellContext`). `PostList` shows them above the loaded items and drops the copy it already has when the
  server delivers the same id. `/saved` ignores them. The profile picture is now read in the shell (`useProfilePicture`
  moved up from `UserMenu`) because the new post needs it too.
- Focus returns to `POST` by an explicit ref, not by remembering the previously focused element: Safari does not focus
  a button on click.
- While a publish is in flight, Cancel and Escape do nothing, so a post cannot be abandoned half sent; the request
  ends by itself (published or failed). A whitespace-only draft counts as empty, so closing it asks nothing.
- `Publish` uses `aria-disabled` rather than `disabled`, like the other buttons, so focus is not lost when it is
  pressed. The attach control is a `<label>` around a visually hidden file input, so it is keyboard reachable and its
  focus ring shows on the label.
- Several files picked at once are taken in order until one is refused; the ones before it stay attached.
- The scroll lock is a `data-scroll-locked` attribute on `<html>` with its rule in `index.css`, not inline styles.
- `ComposeModal.tsx` is about 190 lines because it wires the draft, the discard question and the keyboard; it is not
  split further because each piece is already a hook.
- Test infra: `src/test/setup.ts` stubs `URL.createObjectURL` / `revokeObjectURL` (jsdom has none);
  `src/test/postListHelpers.ts` gained `reachListBottom`.

## Feed step 10 - NEW POSTS

- `useNewPosts` (next to `PostList`) reads the first page every 60 s while the tab is visible and keeps what is newer
  than the newest **loaded** post and not on screen. The top is the newest loaded post, not the top of the displayed
  list, because the reader's own posts sit above it and would hide other people's posts that fall between the two.
  `findNewerItems` therefore takes the newest loaded item and the shown items separately.
- Checking starts only once the first page has landed (also for an empty feed), never after a failed first load, and
  one check at a time. A failed check is silent. Returning to a hidden tab restarts the 60 s timer; it does not check
  at once, as the plan says "paused while hidden" and nothing more.
- Pressing the button prepends the new posts and scrolls to the top (instant, no smooth scroll, so reduced motion needs
  no extra rule). When a check returned a whole page (20) as new, a gap is possible, so the list is reloaded from the
  top instead: the one place the list is replaced. A reload bumps a generation counter, so a page that was on its way
  from before it is discarded when it lands.
- The reader's own posts are merged into the loaded list by the feed's time order (`mergeNewestFirst`), not always put
  on top: my post lands below a newer post from someone else. `/saved` has none, so its order is untouched.
- The button's sticky offset comes from `--header-height` (index.css), which the header now uses for its own height,
  so the two cannot drift apart.
- Accepted gap: the button's count is not announced to screen readers; only the button appears. Add a polite
  announcement ("n new posts available") if the accessibility review asks for it.

## Feed step 11 - e2e

- The 11 scenarios are `e2e/tests/feed-ui.spec.ts`, one test each, in the plan's order. Fixtures gained
  `postTweet(..., image)` (a real 1x1 PNG, `TINY_PNG`), `feedTweetIds`, `expectFeedToContain` and `openAs`, which hands
  the API account's session cookie to the browser so a test does not type the login every time. The typed login stays
  covered by `auth.spec.ts`.
- `auth.spec.ts` asserted the old placeholder (`@username` heading, `LOG OUT` button). Those two assertions now use the
  account menu and the `LOG OUT` menu item; nothing else in the file changed.
- Scenario 7 installs Playwright's clock before the page loads and fast-forwards 60 s once, after the API shows the new
  post in the reader's feed, so it does not race the fan-out.
- Result: 28 tests (the 4 older specs plus the 11 new ones, plus auth) green 3 times in a row, each from a fresh stack
  (`docker compose up --build`, then down). The first run rebuilt the gateway and timeline images.

## Feed step 12 and 13 - manual check and style review

- Step 12: the user waived the visual sign-off ("continue, fix design and scalability later"). A demo stack was seeded
  (29 posts, mixed text and images) and then torn down; nothing was found or fixed.
- Step 13 style review fixed every clear violation (one field per line, one argument per line, 120 columns, chains,
  `item` -> `tweet`, `isSubmittable`, shared image-type constant, coupling comments). Testing Library and Playwright
  queries (`getByRole('x', { name })`) and `expect(...).toX()` chains stay on one line; splitting ~200 of them hurt
  readability.
- Accepted judgement calls, not changed: single-consumer helpers stay in `src/utils/` (`bidi`, `relativeTime`,
  `ownPost`, `platform`, `viewReporter`); one shared `ApiError` in `http.ts`; `ownPosts` travels in outlet context;
  `ComposeModal` (183 lines) and `UserMenu` (163 lines) are not split further; the `600px` breakpoint is new beside
  the auth screens' `720px`; the compose textarea has `outline: 0` because the glow rule is its focus indicator.
- Still over 120 columns: the SVG path in `FillIcon.tsx`, and test titles in `e2e/tests/feed.spec.ts` and `views.spec.ts`
  (older specs, not part of this phase).

## Frontend plan step 17 - back-fill journeys

- Scenarios 1 and 3 are in `e2e/tests/backfill.spec.ts`; scenario 2 replaced the re-follow assertion in
  `feed.spec.ts` (renamed `should_drop_the_authors_tweets_on_unfollow_and_bring_them_back_with_the_next_tweet_after_a_refollow`).
  `unfollow` moved from `feed.spec.ts` to `fixtures.ts`, since both specs use it.
- Specs changed because a follow now brings older tweets: only that re-follow test. Every other spec follows before
  the author posts, or reads nothing the back-fill adds; the whole suite (32 tests) passed unchanged.
- Scenario 3 relies on Kafka's per-key order: Ana's and Cy's `user.followed` events share the key (Bob), so Cy's
  back-fill showing up means Ana's has run, and by then the unfollow has committed, so her check undid it.
- Run 3× in a row with `npm test` (images rebuilt each time): 32 passed, 32 passed, 32 passed.

## Frontend plan step 18 - shared paging hook

- `usePagedList<T extends Identified>(fetchPage, describeAppend)` lives in `src/hooks/usePagedList.ts` with `PAGE_SIZE`
  and `LoadStatus`; it returns everything `usePostList` returned except the merged `items`. `usePostList` is now a thin
  wrapper that merges the reader's own posts in. `describeAppend` is a parameter so the people list can say "people".
- `src/api/paging.ts` holds `Identified`, `Page<T>`, `PageRequest` and `pageUrl`; `TweetPage` is `Page<TweetItem>`.
- `appendUnique` and `prependUnique` are generic but stay in `utils/tweetList.ts`, so `tweetList.test.ts` is unchanged.
  Move them to their own module when the people list needs them and the name starts to mislead.
- The only test edit: `PostList.test.tsx` imports `PageRequest` from `api/paging` instead of `api/tweetPage` (one import
  line, no re-export kept just for a test).

## Frontend plan step 19 - API and helpers

- `truncateBio(bio: string | null): string | null` (`utils/bio.ts`) returns `null` for "no bio", which the card turns into
  `data-empty`. The cut follows the plan literally: take the first 50 code points, back up to the last space inside them,
  add `…`. A bio whose 51st character is a space therefore loses its last word too; a smarter edge rule was not invented.
- `formatFollowers(count)` (`utils/followers.ts`) uses integer tenths, so rounding down never meets a floating-point
  surprise. The `FOLLOWER` / `FOLLOWERS` word stays with the card (step 21).
- `fetchPeople` returns `Page<Person>` with the picture URL on the API origin and `bio` passed through (`null` allowed);
  `followUser` / `unfollowUser` resolve with nothing on `204`. All three use `sendAuthenticated`, so a `401` signs out.
- `ENDPOINTS.users` and `ENDPOINTS.follow(username)` (encoded like `ENDPOINTS.user`).

## Frontend plan step 20 - tab row and panels

- `TabPanels` (`components/shared/TabPanels`) is the layout route of `/feed` and `/users`; its two child routes have no
  element, because it reads the tab from the address (`useActiveTab`) and renders the pages itself. Both panel wrappers
  are always in the DOM, since the tabs' `aria-controls` point at them; only the page inside mounts lazily, on the
  tab's first visit, and stays mounted until the layout unmounts. `PeoplePage` is only its hidden `People` heading until
  step 21.
- `Shell` owns the tab row and the navigation (`navigate(path)`, a pushed entry); `TabRow` is presentational
  (`activeTab`, `onSelect`). Clicking the active tab does not call `onSelect`; an arrow moves focus and selects at once,
  wrapping.
- The indicator renders only after its first measurement, so that first placement has nothing to transition from; later
  changes (tab, resize, `document.fonts.ready`) animate. Its offset and width are inline styles, being measured values.
- Scroll memory tracks `window.scrollY` on `scroll` events instead of reading it at the switch: by then the leaving panel
  is hidden and the browser has already pulled the page up to the shorter content.
  A capture-phase `click` and `keydown` listener refreshes the same value first (timeline plan step 33): a press that
  switches the tab can land before the browser has delivered the latest `scroll` event, and the stale position was
  then saved. The e2e scroll test sends its tab presses as events, because Playwright's own click sometimes scrolls
  the page first (the tab row is sticky), which moves the position the app rightly remembers.
- `data-entering` is `true` on the active panel once the tab has changed at least once, `false` before, so the entrance
  does not play on the first render; a panel going from `hidden` to shown restarts the CSS animation by itself.
- Header: `position: sticky; top; z-index` moved to `.shell-top`, as planned. The header itself became
  `position: relative; z-index: 1`, which the plan did not name: its `::after` rule line needs a positioned parent, and
  the account menu must open over the tab row, which is later in the DOM.
- The tab labels and the indicator fade in with the header's content and out with it on sign-out (own `tab-row-*`
  keyframes, the same 900 ms / 700 ms), so the row does not pop in beside a fading header. The plan only named the
  sign-out half. Revisit at the visual check.
- `--pale-bio` is not added yet; it arrives with the card in step 21.

## Frontend plan step 21 - people list, card and follow button

- Moved to shared places, posts unchanged: `Avatar` → `components/shared/Avatar` (new `size`: `small` | `large`, as
  `data-size`; large is 44 px, 40 px at ≤ 600 px), `PostListStatus` → `components/shared/PostListStatus`,
  `useBottomSentinel` and `useOptimisticToggle` → `src/hooks`. The foot-of-list rule (`bottomStateOf`) left `PostList`
  for `utils/bottomState.ts`, so both lists share it.
- `useOptimisticToggle(initial, send, onFailure?)`: `onFailure` gets the value that could not be set. Without it the
  revert stays silent, as likes and saves have it.
- `PeoplePage` → `PeopleList` (paging through `usePagedList`, the sentinel, `PostListStatus`, a live region) →
  `PersonCard` (`<li>`, not an article: the card is not a post) → `FollowButton`. The list's live region says
  `n more people loaded`, with `1 more person loaded` for one, as posts do.
- Follow button state lives in `usePersonFollow` (the toggle, the count, the announcements) and `useFollowPhase` (the
  3 s `armed` / `failed` looks). The count is the server's `followersCount` ± 1, by how the reader's follow now
  differs from `followedByMe`, so a revert restores it with no extra bookkeeping.
- Looks the plan left open: `UNFOLLOW?` drops the panel and uses `--pale` text, so a hovered, followed button stops
  looking followed while it asks; `TRY AGAIN` uses `--alarm` for text and border. A tap on `TRY AGAIN` repeats the failed
  change as it was: a failed unfollow is sent again at once, without arming. Blur and Escape disarm only an armed
  button; they leave `TRY AGAIN` alone.
- Each card carries its own polite live region next to the button, in `.person-card-action`, the element that also
  moves the button to its own row below 600 px (the card's CSS does not reach into the button's class).
- A successful follow or unfollow calls `onFollowChanged`. `Shell` owns a `followChangeCount` and puts both on the
  outlet context (`ShellContext`), because the feed already reads `ownPosts` from there; `PeoplePage` hands the callback
  down. Nothing reads the count yet: step 22 does.
- Sign-out: the cards play the posts' leave (own `person-list-leave` keyframes in `PeopleList.css`, same 700 ms and
  delays). They have no entrance of their own; the panel's entrance covers it.
- `--pale-bio` (.62) added. Tests that open `/users` (`Shell.test.tsx`, `TabPanels.test.tsx`) now mock `fetchPeople`;
  the step 20 test "only the hidden heading until step 21" became "the heading and the people".

## Frontend plan step 22 - TWEETS after following

- `PostListStatus` takes an optional `emptyAction` (`label`, `onClick`); only the empty state shows it, so `/saved` is
  unchanged. Its button class is now `post-list-status-button` (it was `-retry`), since `TRY AGAIN` and `FIND PEOPLE`
  share the look. The status wraps and centres its text, because the empty line with a button is long on a phone.
- `PostList` takes `emptyAction` and `reloadEmptyKey`. When the key changes (never on the first render) and the list is
  empty (`isEnd` with no items, own posts counted), the first page is read again (`useReloadEmptyList`). A list with
  posts keeps its place; one still loading or failed is left alone, since it already has a read of its own.
- The key is the follow count as of the last time the tab was shown (`useFollowCountWhenShown`, in `FeedPage`): the
  feed is hidden when a follow happens, so the value stays behind and catches up in the render that shows the tab.
  That makes the reload happen on coming back, a few seconds after the follow, when the back-fill has had time to land,
  and only once per follow change. A feed that mounts after a follow (first opening of TWEETS) reads once, as it
  always did. The count is `Shell`'s `followChangeCount` from step 21.
- `FIND PEOPLE` calls `navigate(ROUTES.people)`, a pushed entry like a tab click, so Back returns to TWEETS.
- The text is `NOTHING HERE YET — FOLLOW SOMEONE TO SEE THEIR POSTS`; the tests of `FeedPage` and `Shell` that named
  the old `NOTHING HERE YET` follow it.

## Frontend plan step 23 - e2e UI journeys

- Scenarios 4–11 are in `e2e/tests/people-ui.spec.ts`, one test each (scenario 9 holds the arrow keys, Back and the
  reload in one test, since they share a page and a session). Cards are found by scrolling PEOPLE until the username
  shows (`findPersonCard`), as the plan said, because the database is shared with the parallel specs.
- Scenario 6 starts on `/feed` and uses `FIND PEOPLE`, so the feed is already mounted and empty when the follow
  happens; that is what exercises the reload on coming back (a feed first opened after the follow would just read).
- Scenario 8 answers only the follow request with `500` (`page.route`) and expects `TRY AGAIN`, the count back at `0`,
  then `FOLLOW` after the 3 s hold, with no clock control: the default expect timeout of 10 s covers it.
- Scenario 10 waits until all 25 of Bob's posts are in Ana's feed through the API before it scrolls, so the page is tall
  enough and the back-fill is not still arriving.
- No existing spec changed: `getByText('NOTHING HERE YET')` in `feed-ui.spec.ts` matches the longer empty text as a
  substring.

## Frontend plan step 24 - style review, audit, docs

- Style review (read-only agent against `frontend-code-style`): fixed the rule violations (helper above the props in
  `TabRow.tsx`, base animation rule before the state rules in `TabRow.css`, `outletContext` next to the return in
  `Shell.tsx`, the effect before the handlers in `useFollowPhase.ts`, crammed test lines) and the cheap guideline misses
  (`isEmpty` in `PostList`, the `bio.ts` comment). The rest is under "Left open" in `PLAN.md`.
- Security audit (four read-only agents: frontend, gateway, tweet service, timeline): no Critical or High. Two Mediums
  were fixed here (the by-author index, and the follow check's answer, `200 {"following": ...}`, which changed the
  planned `204`/`404` contract; recorded in the gateway's and timeline service's `DECISIONS.md`). The Lows are under
  "Left open". The frontend's own part is `SECURITY-FINDINGS.md`, "Audit — 2026-10-04 (phase 3 ...)".
- Closed phase 2's gap "A new user can't follow anyone" and the gateway's "No frontend caller yet" (deleted from their
  gap lists). `CLAUDE.md` now describes the tabs, the follow button and the follow signal.
- Exit run: build, lint (0 findings), 866 frontend tests; `mvn verify` 3× green in the tweet service (267 tests), the
  gateway (844) and the timeline service (496); Playwright 40/40 three times in a row from a fresh stack.

## Likes UI plan step 37 - one count formatter

- `formatFollowers` (`utils/followers.ts`) is now `formatCount` (`utils/count.ts`), unchanged rule (rounded down, K / M,
  one decimal below ten), because like counts read the same way as follower counts (Q23). `PersonCard` keeps its
  `FOLLOWER` / `FOLLOWERS` word. The entry on `formatFollowers` above describes the same function under its old name.

## Likes UI plan step 41 - the like count rule

- A post shows `formatCount(max(0, likes - (isLikedInitially ? 1 : 0) + (isOn ? 1 : 0)))`: the server's count already
  holds your like when the post loaded liked, so your own change is applied on top. The `max` keeps a count read
  mid-race (`likes: 0` with `likedByMe: true`) from ever showing `-1`. A failed like or unlike reverts silently, a `404`
  included.
