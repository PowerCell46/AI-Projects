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
