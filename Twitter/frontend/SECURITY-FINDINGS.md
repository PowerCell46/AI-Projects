# Security findings — frontend

## Audit — 2026-10-04

**Scope:** `Twitter/frontend/src` (auth flow, feed, saved posts, compose modal with image upload, likes, view
reporting), `index.html`, `vite.config.ts`, `npm audit`. Where the SPA trusts the backend (picture URLs, image
content types, cookie flags, CSRF posture, views endpoint) the gateway, tweet service and timeline service code was
read to confirm the contract; those services have their own audits. Builds on `twitter_mail_service/docs/SECURITY-AUDITS.md`, which
predates the feed, compose, likes, saved and views code; that code is the new ground here.

No Critical, High or Medium findings. Two Low and two Informational. Nothing here is exploitable today without a
second fault elsewhere.

### 1. Low — no Content-Security-Policy and no referrer policy (still open from 2026-10-01)
- **Location:** `index.html` (no CSP, no `<meta name="referrer">`, Google Fonts `<link>`).
- **Mechanism:** the SPA and the API share one origin (relative `/api/...`, cookie auth). If any script were ever
  injected, there is no CSP to limit it, and the cookie is `HttpOnly` so the damage would be actions as the user,
  not token theft. The third-party fonts stylesheet is CSS that Google could use to restyle the login form. The
  `/confirm?token=…` URL is sent as `Referer` to Google Fonts in browsers whose default policy is not
  `strict-origin-when-cross-origin`. Verified today: `index.html` has neither mitigation.
- **Demonstration:** `curl -sI http://localhost:5173/ | grep -i content-security-policy` returns nothing. In an
  older or policy-configured browser, open `http://localhost:5173/confirm?token=abc` and read the `Referer` on the
  `fonts.googleapis.com` request in DevTools, Network.
- **Suggested fix (not applied):** add `<meta name="referrer" content="same-origin">` now; when the app gets a
  server or Caddy, send a CSP and self-host the fonts so `style-src 'self'` works.

### 2. Low — session cookie `Secure` flag defaults to off (gateway)
- **Location:** `twitter_api_gateway/src/main/resources/application.properties:27`
  (`app.cookie.secure=${COOKIE_SECURE:false}`), used in `utilities/CookieFactory.java:41`.
- **Mechanism:** the flag fails open: a production deploy that forgets `COOKIE_SECURE=true` sends the JWT cookie
  over plain HTTP too. `HttpOnly` and `SameSite=Strict` are set correctly. Noted while tracing the trust boundary
  the frontend relies on, and recorded here because the SPA has no way to detect or compensate for it.
- **Demonstration:** start the gateway without `COOKIE_SECURE`, then
  `curl -si -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"identifier":"u","password":"P@ssw0rd1"}' | grep -i set-cookie`
  shows the cookie without `Secure`.
- **Suggested fix (not applied):** default to `true` and let local dev opt out with `COOKIE_SECURE=false`.

### 3. Informational — resource ids are interpolated into URL paths without encoding
- **Location:** `src/api/endpoints.ts` (`savedTweet`, `like`, `tweetImage`, `backendPath`); compare `user`, which
  uses `encodeURIComponent`.
- **Mechanism:** `tweetId` and `imageId` come from the server's JSON, and `backendPath` prefixes `BASE_URL` to a
  server-built path (`/api/v1/files/` + UUID, `ProfileMapper.java:11`). No user input reaches them, so there is no
  path traversal or off-origin image today. If a future endpoint let a user set one of these values, `../` or a
  leading `//host` would go straight into a request or an `<img src>`.
- **Demonstration:** none that works against the current backend. In a test, `ENDPOINTS.like('../../auth/logout')`
  returns `/api/v1/likes/../../auth/logout`.
- **Suggested fix (not applied):** encode path segments, and have `backendPath` reject anything that does not start
  with a single `/`.

### 4. Informational — route guards and client-side upload checks are UX, not enforcement
- **Location:** `ProtectedRoute.tsx`; `src/utils/composeChecks.ts` (`checkPickedImage`).
- **Mechanism:** the 280-character, four-image, 5 MiB and MIME checks run in the browser only. The server enforces
  the same limits and detects the content type from magic bytes
  (`twitter_tweet_service/.../ImageSignatureValidator.java`), so a forged `Content-Type` on the upload does not
  change what is stored or served. Confirmed, not a hole.
- **Demonstration:** `curl -b cookie -F content=x -F 'images=@page.html;type=image/png' localhost:8080/api/v1/tweets`
  is refused by the server's signature check.

## Considered, clean or not applicable
- **XSS:** no `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function`, `document.write`, `srcDoc`,
  `target=`, `href=` or `window.open` in `src/` (grep). Post bodies, usernames and error messages render as React
  text nodes. `stripBidiControls` removes the bidi override controls from post bodies. Usernames are limited to
  `[A-Za-z0-9_]{3,15}` by `RegisterRequestDTO`, so they need no stripping.
- **Stored XSS through uploads:** tweet images are served with a server-detected content type (JPEG, PNG, WebP only)
  and `X-Content-Type-Options: nosniff` (`TweetController.java:76`), and Spring Security's default headers are on
  for the gateway. An uploaded HTML or SVG file cannot be served as a script on the shared origin.
- **CSRF:** the gateway disables CSRF protection and relies on the `SameSite=Strict`, `HttpOnly` cookie
  (`CookieFactory.java`, `SecurityConfiguration.java:71`). That holds for the cookie-only, same-origin design, which
  includes the multipart `POST /tweets`. It would break if the cookie were ever relaxed to `Lax` or `None`.
- **Open redirect / SSRF:** every `navigate`, `<Navigate>` and `<Link>` target is a `ROUTES` constant; no `next` or
  `returnTo` parameter is read. The browser makes no request to a user-supplied URL; image `src` values are the
  fixed API prefix plus server ids.
- **Session and token storage:** no `localStorage`, `sessionStorage` or `document.cookie` use in `src/`. The only
  router state is the email prefilled on `/resend`.
- **Authorization / IDOR:** every data call goes through `sendAuthenticated`; ownership and visibility are decided by
  the gateway, and the 401 handler only signs the UI out. The saved and like routes take ids but act on the caller's
  own state.
- **Resource limits:** the views report is deduplicated per viewer on the server
  (`TweetViewRepository.insertIfAbsent`, 50 ids per request, `ViewServiceImpl.java`), so replaying the endpoint from
  one account does not inflate counts.
- **Dependencies:** `npm audit` for `frontend/` reports 0 vulnerabilities; caret ranges with a committed lockfile.
- **Secrets:** none in `src/`; `dist/` is gitignored.
- **Injection (server-side), caching races, ReDoS:** no server-side code in this package. The only client regexes
  (`bidi.ts`, `avatar.ts`, `validation.ts`) are linear.
- **Account enumeration** (409 on register, 403 on unconfirmed login) is unchanged from the 2026-10-01 report and
  not repeated here.

## Audit — 2026-10-04 (phase 3: tab row, People, follow)

**Scope:** the tab row and panels (`components/shared/TabPanels`, `Shell/TabRow`, `hooks/useActiveTab.ts`,
`utils/tabs.ts`, route handling in `App.tsx` and `routes.ts`), the People list (`pages/PeoplePage/**`: `PeopleList`,
`PersonCard`, `FollowButton`, `usePersonFollow`, `useFollowPhase`), `api/users.ts` (`fetchPeople`, `followUser`,
`unfollowUser`), `api/endpoints.ts`, `api/paging.ts`, `api/pictureUrl.ts`, `utils/bio.ts`, `utils/followers.ts`,
`utils/bidi.ts`, `hooks/usePagedList.ts`, `useOptimisticToggle.ts`, `useBottomSentinel.ts`, `components/shared/Avatar`,
the empty-feed `FIND PEOPLE` action and reload logic (`FeedPage`, `PostList`, `useReloadEmptyList`), and the Shell
outlet context. The gateway's `UserController`/`FollowController`/`UserListServiceImpl`/`FollowCursorCodec`/
`ProfileMapper`, `RegisterRequestDTO` and `UpdateProfileRequestDTO` were read to confirm what the SPA can rely on;
they have their own audits. `npm audit`: found 0 vulnerabilities (the registry was reachable). `npm test`: 866 passed.

No Critical, High or Medium findings. One Low and zero Informational findings are new; the picture-URL and
path-segment hardening already recorded as finding 3 is still open and is extended below.

### 5. Low — `usePagedList` follows a cursor with no progress check, so a stuck or malformed page response loops forever
- **Location:** `src/hooks/usePagedList.ts:44` (`while (!hasAddedItems && !isEndRef.current)`) and `:57`
  (`isEndRef.current = page.nextCursor === null`). Used by the People list, the feed and saved posts.
- **Mechanism:** the rule "an empty or all-duplicate page with a cursor is followed at once" has no limit and no
  check that the cursor moved. Requests run back to back, one per round trip, with no delay, until the component
  unmounts or the response finally adds an item. Two ways to trigger it: (a) a response with `items: []` (or only ids
  already loaded) and a non-null `nextCursor` that does not advance; (b) a response that omits `nextCursor`.
  `undefined === null` is false, so the list does not end, `pageUrl` sends no `cursor`, and the first page is asked
  for again, forever. Both panels stay mounted while hidden (`TabPanels`), so the loop keeps running on the other
  tab and each iteration also re-renders the list state. It is client-side only and needs a faulty backend or a
  proxy that rewrites the JSON: the gateway as written emits `nextCursor: null` explicitly (no `NON_NULL` inclusion
  in the project) and its keyset cursors always advance (`UserListServiceImpl`, `FeedServiceImpl`). So this is
  missing defence in depth, not a hole today, but a backend bug here turns every open tab into a request flood
  against the authenticated endpoints and pins one CPU core.
- **Demonstration:** a throwaway vitest file (outside `src/`, deleted afterwards) rendered the hook with a fetcher
  that waits one timer tick per call and, to stop the run, returns `nextCursor: null` after the 300th call.
  Case (a), `{items: [], nextCursor: 'same'}`: 300 requests during one mount with no user action. Case (b),
  `{items: [{id: 'a'}], nextCursor: undefined}`: one request on mount, then 300 after a single `loadMore()`. With the
  fetcher answering immediately (no timer tick) and never stopping, the vitest worker aborted with SIGABRT (memory
  exhaustion), which is the same loop without a network between the iterations.
- **Suggested fix (not applied):** cap the follow-up loop (for example 5 consecutive pages that add nothing, then
  show the failed state with TRY AGAIN) and stop when the new cursor equals the one just sent; treat a missing
  `nextCursor` as `null` (`page.nextCursor ?? null`), or reject a response whose `nextCursor` is not a string or null.

### Update to finding 3 (still open): picture URLs and path segments in the new People code
- `fetchPeople` and `fetchUserProfile` run every server-supplied `profilePictureUrl` through `toPictureUrl`, which is
  `BASE_URL + path` with no check, and `Avatar` puts the result in `<img src>`. Another user's value reaches the
  card, so the exposure is wider than before, but the gateway builds it from a file id (`ProfileMapper.urlOf`,
  `/api/v1/files/` + UUID) and a user cannot set the string, so it is still not exploitable. Checked in a vitest
  scratch file: `toPictureUrl('//evil.example/p.png')` returns `//evil.example/p.png` and
  `toPictureUrl('https://evil.example/p.png')` returns `https://evil.example/p.png` when `VITE_BASE_API_URL` is empty
  (the default). A `javascript:` value in `img src` does not run script in current browsers.
- `ENDPOINTS.follow` and `ENDPOINTS.user` do use `encodeURIComponent`: `ENDPOINTS.follow('a/b?c#d')` gives
  `/api/v1/users/a%2Fb%3Fc%23d/follow`. It leaves `.` and `..` alone (`ENDPOINTS.follow('..')` is
  `/api/v1/users/../follow`, which the browser collapses to `/api/v1/follow`), but a username that short or made of
  dots cannot exist: `RegisterRequestDTO` allows only `^[A-Za-z0-9_]{3,15}$`, and `matches()` semantics reject a
  trailing newline. Same fix as finding 3: have `backendPath` accept only a single leading `/`.

## Considered, clean or not applicable (phase 3)
- **Stored XSS through bio or username:** `bio` is attacker-controlled free text (`UpdateProfileRequestDTO`, up to 160
  characters, any content) and is the only new field of that kind. It reaches the DOM once, as the React text child of
  `<p className="person-card-bio">` (`PersonCard.tsx`), after `truncateBio`. Usernames render as text children and in
  `aria-label` / aria-live strings (`Follow ${username}`, `Couldn't follow ${username}`, `Press again to unfollow ...`),
  which are plain strings. No `dangerouslySetInnerHTML`, `innerHTML`, `href`, `target` or `style` built from server
  data anywhere in `src/` (grep). The only inline `style` is the tab indicator, from measured pixel numbers.
- **Bidi and RTL spoofing:** `truncateBio` strips U+202A-202E and U+2066-2069 before truncating, and the bio sits in its
  own block with `dir="auto"`, so an RTL bio flips only that paragraph and cannot reorder the username, follower count
  or button next to it. Not stripped, by design: LRM/RLM (U+200E/F), U+061C (Arabic letter mark) and zero-width
  characters. In a scratch run `truncateBio('؜abc‮def‏x y')` kept U+061C and U+200F and dropped
  U+202E; those characters only reorder neighbouring neutrals and digits inside the bio itself. Usernames are not
  stripped because the server restricts them to `[A-Za-z0-9_]`. A bio of 60 combining marks is cut at 50 code points
  and `.person-card-bio` wraps with `overflow-wrap: anywhere`.
- **Follower counts:** rendered through `formatFollowers` as text; always floors, so a count never reads higher than the
  server's. A non-number from a faulty server would show as `NaN`, not as markup. The optimistic +1/-1 is display only.
- **Opaque paging cursor:** never parsed or rendered. It goes through `URLSearchParams` (`pageUrl`), so a cursor of
  `a&size=100#x=1 /../` is sent as `cursor=a%26size%3D100%23x%3D1+%2F..%2F` and cannot add parameters or change the
  path. The gateway re-encodes and compares the cursor (`FollowCursorCodec.decode`) and caps `size` at 100.
- **Request amplification from the follow button:** `useOptimisticToggle` keeps one request in flight, sends the latest
  wanted value only if it differs from the last confirmed one, and on any failure reverts and stops (no retry loop).
  Unfollow needs two taps (`armed` phase, 3 s), and `TRY AGAIN` repeats one request per tap. Rapid clicking cannot
  produce more than one request at a time per card. The empty-feed reload runs once per follow-count change and only
  when the feed is empty and ended (`useReloadEmptyList`), so it is bounded by user actions; `reload()` bumps a
  generation counter so a late response from before the reload is dropped.
- **Unbounded memory:** `loadedItems` is never trimmed, but it grows only by pages the user scrolls to (the sentinel
  rearms per page, 20 items) and is released on sign-out when `Shell` unmounts. Not a finding.
- **Sign-out race:** a `fetchPeople` or follow response that lands after sign-out is dropped (`isActiveRef` false
  after unmount) or only bumps a counter in an unmounted `Shell`. State is per mount, so a second user signing in on
  the same tab starts with empty lists and no previous `followedByMe`. During the 1.2 s leave animation the shell has
  `pointer-events: none`; a keyboard-triggered follow then would carry the already cleared cookie, get a 401, and the
  handler would end the session slightly early. No data is changed.
- **401 handler:** `sendAuthenticated` signs the UI out on a 401 from follow, unfollow and the People list, as for the
  other data calls. It does not retry the request and does not read any redirect target from the response.
- **History and URL tampering:** the active tab comes from `matchPath` on the pathname only (`tabOfPath`); there is no
  query or hash input, and an unknown path falls to `SessionRedirect`, which navigates to a `ROUTES` constant. The tab
  row and `FIND PEOPLE` navigate to `ROUTES` constants. `/users` (client route) and `/api/v1/users` (API) do not
  collide because only `/api` is proxied.
- **Information leaks:** the list excludes the caller (`UserRepository` list query) and shows only
  `followedByMe`/`followersCount`, which any logged-in user can already see; no email, birthdate or location reaches
  the list DTO. Nothing from the responses is written to storage, the URL or router state.
- **Dependencies:** `npm audit` reports 0 vulnerabilities (registry reachable).

## Audit — 2026-10-06 (phase 5: real likes, `/liked`)

**Scope:** the uncommitted likes UI work on top of `d79ce85`: `api/likes.ts` (`fetchLikedTweets`, `likeTweet`,
`unlikeTweet`), `ENDPOINTS.likes` / `ENDPOINTS.like`, `api/tweetPage.ts` (`likes`, `likedByMe`), `PostActions`
(`shownLikeCount`), `pages/LikedPage`, the `LIKED TWEETS` menu link, `utils/count.ts` (`formatCount`, renamed from
`followers.ts`), `utils/ownPost.ts`, `routes.ts` and `App.tsx`; plus a fresh pass over `index.html`, `vite.config.ts`,
the confirm-token flow and the 401 plumbing in `api/http.ts`. To confirm what the SPA relies on, the gateway's
`TimelineRoutesConfiguration` / `CallerIdentityFilters` and the timeline service's `LikeController`, `LikeServiceImpl`
and `LikeRecordingServiceImpl` were read; they have their own audit (`twitter_timeline_service/docs/SECURITY-AUDITS.md`).
`npm audit`: found 0 vulnerabilities (the registry was reachable). Not run: the Docker stack and a browser, so
nothing below was exercised against a live gateway.

No new findings of any severity. The new code adds no raw-HTML, URL, storage or redirect sink, and every old finding
below is unchanged.

### Status of earlier findings (re-checked today)
- **1 (Low, CSP and referrer policy): still open.** `index.html` still has no CSP, no `<meta name="referrer">` and the
  Google Fonts `<link>` is unchanged. Re-read today, not re-run in a browser.
- **2 (Low, `COOKIE_SECURE` defaults to false): still open.** `application.properties:27` is still
  `app.cookie.secure=${COOKIE_SECURE:false}`.
- **3 (Informational, unencoded path segments / `backendPath`): still open and one call wider.** The new
  `ENDPOINTS.like(tweetId)` interpolates the id the same way as `savedTweet` and `tweetImage`
  (`${API_V1}/likes/${tweetId}`). The id comes from the server's JSON (`TweetItem.id`), the timeline service binds it
  as a `UUID` path variable, and a user cannot choose it, so it is the same "no user input reaches it today" case.
  Same fix: encode the segment.
- **5 (Low, `usePagedList` has no progress check): still open, and `/liked` is a fourth consumer.** The loop at
  `usePagedList.ts:44` and `:57` is unchanged. `LikedPage` passes `fetchLikedTweets` to `PostList`, so a response with
  `items: []` and a non-advancing `nextCursor`, or one that omits `nextCursor`, now also loops on the liked list. The
  backend as written emits an explicit `nextCursor: null` and its cursor is `TimelineCursorCodec.encode(likedAt,
  tweetId)` of the last row, which always advances, so there is still no trigger today. Fixing the hook fixes all
  four lists.

## Considered, clean or not applicable (phase 5)
- **XSS and markup injection through the new fields:** `likes` and `likedByMe` are numbers and booleans. The count
  reaches the DOM only as `formatCount(...)` text inside `<span className="post-actions-count">`. A non-number from a
  faulty server would print `NaN` or the raw value as text, not markup. `LikedPage` renders fixed strings plus
  `PostList`, so the post body, username and images take the same React-text path as the feed. Grep of `src/`
  (non-test) for `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function`, `document.write`, `localStorage`,
  `sessionStorage`, `document.cookie`, `window.open`, `location.href|assign|replace`, `postMessage`, `href={` and
  `target=` found nothing. The only hits are `URL.createObjectURL` for the compose preview of a file the user picked,
  and `<img src>` values built from `ENDPOINTS.tweetImage` or `toPictureUrl`.
- **Open redirect / SSRF:** `ROUTES.liked` is a constant; the menu `Link` and the `/liked` route read no query
  parameter. `/liked`, `/saved` and the rest sit behind `ProtectedRoute`; unknown paths fall to `SessionRedirect`.
- **Authorization / IDOR on likes:** `PUT` and `DELETE /api/v1/likes/{tweetId}` and `GET /api/v1/likes` take the
  caller from `@CurrentUserId` (the `X-User-Id` header), never from the body or path, so one user cannot like, unlike
  or list for another. The gateway strips the caller's `Cookie`, `Authorization` and any `X-User-*` header before it
  sets `X-User-Id` from the verified JWT (`CallerIdentityFilters.forwardAsTheAuthenticatedUser`), so the header cannot
  be planted from the browser. The timeline service is not published in `docker-compose.yml` (only the gateway has
  `ports:`, bound to `127.0.0.1:8080`), so it cannot be reached with a forged header from outside the stack.
- **Like count integrity:** the primary key `(user_id, tweet_id)`, `insertIfAbsent` and a counter that moves only in
  the transaction of a row actually added or removed (`LikeRecordingServiceImpl`) mean one account cannot inflate a
  count by replaying `PUT`. The SPA's `shownLikeCount` (server count, minus your own like on load, plus your own change,
  floored at 0) is display only and never sent back.
- **Existence oracle:** `PUT /likes/{tweetId}` answers 404 for an unknown tweet and 204 for a known one. Tweet ids are
  random UUIDs and tweets have no private visibility in this app, so it reveals nothing a signed-in user could not
  already read. Not a finding.
- **Request amplification from the heart button:** `useOptimisticToggle` keeps one request in flight per post and
  sends the last wanted value only if it differs from the last confirmed one. A script can still call the endpoint
  directly. There is no rate limit on likes, each `PUT` costing one tweet-service call and a log line; this is already
  recorded as an accepted gap in `docs/likes-design.md` and `twitter_timeline_service/docs/SECURITY-AUDITS.md`, so it is not
  repeated as a finding.
- **CSRF:** `PUT` and `DELETE` on a cookie session depend on the same `SameSite=Strict`, `HttpOnly` cookie as every
  other call (see 2026-10-04); unchanged by the likes routes.
- **Confirm token:** `confirm(token)` sends the token in a JSON `POST` body (`api/auth.ts:57`), not a `GET`, and
  `ConfirmPage` reads it from `?token=` only. The URL keeps the token after the call, but it is single-use, so a
  copy from browser history is useless once confirmed. The `Referer` exposure to the fonts host is finding 1.
- **Dev server and build:** `vite.config.ts` proxies only `/api` to `GATEWAY_URL` (default `http://localhost:8080`),
  with no `server.host`, so the dev server listens on loopback unless started with `--host`. `.env` is gitignored
  (`Twitter/.gitignore:1`) and only `.env.example` is tracked. `dist/` is gitignored.
- **Dependencies:** `package.json` has three runtime dependencies (`react`, `react-dom`, `react-router-dom`) on caret
  ranges with a committed lockfile; `npm audit` reports 0 vulnerabilities.
- **Injection (server-side), caching, ReDoS:** none in this package. `count.ts` has no regex and only integer
  arithmetic on a number.

## Audit — 2026-10-07 (tweet details page, post cell, tab panels)

**Scope:** `pages/TweetDetailsPage/**` (`TweetDetailsPage`, `useTweetDetails`, `useReplyThread`, `ReplyThread`,
`ReplyCell`, `ReplyComposer`, `ReplyEditor`, `ReplyTextField`), `components/shared/PostList/PostCell/**` (`PostCell`,
`PostActions`, `FillIcon`, `PostImages`) and `components/shared/TabPanels/**` (`TabPanels`, `useTabScrollMemory`,
`useTabVisits`), with the code they call: `api/replies.ts`, `api/tweetDetails.ts`, `api/tweetPage.ts`,
`api/endpoints.ts`, `hooks/useOpenTweetId.ts`, `routes.ts`, `useViewTracking`. The tweet service's `ReplyController`
and `ReplyServiceImpl` were read to confirm what the SPA relies on; they have their own audit
(`twitter_tweet_service/docs/SECURITY-AUDITS.md`). `npm audit`: found 0 vulnerabilities. Not run: the Docker stack and a
browser, so the demonstration below is the URL arithmetic only, not a request against a live gateway.

No Critical, High or Medium findings. One Low is new, and it changes the premise of finding 3.

### 6. Low — the `/tweets/:tweetId` address is spliced into API paths without encoding (client-side path traversal)
- **Location:** `src/hooks/useOpenTweetId.ts:9` (reads the route param), `src/api/endpoints.ts` (`tweetDetails`,
  `replies`, `reply`), used by `api/tweetDetails.ts`, `api/replies.ts`, `TweetDetailsPage.tsx:36-37`.
- **Mechanism:** this is the first place where text the user (or a link someone sends them) controls reaches an
  unencoded path segment. `matchPath` returns `%2F` as `/`, and the browser's URL parser collapses `..` and `%2E%2E`
  segments before the request leaves. So `tweetId` is a path of the attacker's choosing under `/api/` (one `..` stays
  inside `/api/v1/`). `fetchTweetDetails` sends it as an authenticated `GET` with the session cookie, and
  `fetchReplies` as `GET .../replies?size=20`. It is Low because: the method is fixed to `GET` on the first call and
  the second adds `/replies`; the response is only parsed as a post (a different shape ends in the `failed` state and
  nothing is displayed or sent anywhere); the cookie is `SameSite=Strict`, and I found no `GET` endpoint with a side
  effect; and the reply writes (`POST`/`PUT`/`DELETE`) only run after the details call returned a valid post, which
  needs a real tweet id. It is the same class as finding 3, but that finding's premise ("no user input reaches them
  today") no longer holds for `tweetId`. The server side holds: the tweet service binds `tweetId` and `replyId` as
  `UUID` path variables, so a non-UUID segment is a 400 there.
- **Demonstration:** `matchPath('/tweets/:tweetId', pathname)` (react-router 7, run in node against `node_modules`),
  then the URL the browser would request:
  - `/tweets/..%2F..%2Fauth%2Flogout` gives `tweetId` `../../auth/logout` and a request to `/api/auth/logout`.
  - `/tweets/..%2Fusers%2Fme` gives `../users/me` and a request to `/api/v1/users/me`.
  - `/tweets/%2E%2E` gives `%2E%2E` and a request to `/api/v1/`; the replies call goes to `/api/v1/replies`.
  In a browser: be signed in, open `http://localhost:5173/tweets/..%2Fusers%2Fme`, and read the
  `GET /api/v1/users/me` in DevTools, Network (not run here).
- **Suggested fix (not applied):** encode every path segment in `ENDPOINTS` (`encodeURIComponent`, as `user` and
  `follow` already do), or reject a `tweetId` that is not a UUID in `useOpenTweetId` and show `POST NOT FOUND`.
  Together with finding 3's `backendPath` check, this closes the whole class.
- **Status: fixed 2026-10-07.** `useOpenTweetId` returns the id only when it is a UUID, and `TabPanels` redirects any
  other `/tweets/...` address to `/feed` (a bare `..` cannot be neutralised by encoding: the URL parser treats `%2E%2E` as
  a dot segment too). `ENDPOINTS` also encodes every id segment now (`api/endpoints.test.ts`), which closes finding 3's
  path part for ids; its `backendPath` part stays informational. Tests: `TabPanels.test.tsx`
  (`should_open_the_feed_when_the_tweet_id_is_not_a_uuid`), `endpoints.test.ts`.

### Update to finding 5 (still open): the reply thread is a fifth `usePagedList` consumer
`useReplyThread` passes `fetchReplies` to `usePagedList`, so a response with `items: []` and a non-advancing
`nextCursor`, or one that omits `nextCursor`, now also loops on the details page. The backend as written emits an
explicit `nextCursor: null` and `ReplyCursorCodec.encode(createdAt, id)` of the last row always advances, so there is no
trigger today. Fixing the hook fixes all five lists.

## Considered, clean or not applicable (tweet details, post cell, tab panels)
- **XSS and markup injection:** reply bodies, post bodies and usernames reach the DOM only as React text children
  (`<p dir="auto">{stripBidiControls(...)}</p>`, `{author.username}`) or inside plain-string `aria-label`s. Grep of
  `src/` (non-test) for `dangerouslySetInnerHTML`, `innerHTML`, `eval`, `new Function`, `document.write`,
  `localStorage`, `sessionStorage`, `document.cookie`, `window.open`, `location.href|assign|replace`, `href={` and
  `target=` found nothing. `FillIcon` builds `d`, `viewBox` and `clipPath` from constants and `useId`, never from data.
  Reply text is sent as JSON (`JSON.stringify({ content })`) and shown in a `<textarea>` `value`, so editing a hostile
  reply cannot inject markup either.
- **Bidi spoofing in replies:** `ReplyCell` runs the body through `stripBidiControls` and sets `dir="auto"`, as `PostCell`
  does for posts, so an RTL-override reply cannot reorder the username or the EDIT/DELETE buttons next to it.
- **Open redirect / SSRF:** `navigate(tweetPath(post.id))`, the reply `Link` and the BACK link only build
  `/tweets/<server id>` or `ROUTES.feed`; the prefix is fixed, so a server id cannot make a `//host` target. `navigate(-1)` is
  used only when the router has an earlier entry of this app. `<img src>` is `ENDPOINTS.tweetImage(tweetId, image.id)`
  (fixed prefix plus ids from the server) or the avatar path from `toPictureUrl` (finding 3's note applies).
- **Authorization / IDOR on replies:** `canEdit` and `canDelete` in `ReplyCell` only decide which buttons show. The
  server decides: `updateContentIfAuthor(replyId, tweetId, callerId, ...)` is one conditional write, and `delete` allows
  the reply's author or the post's author and answers `ReplyNotFoundException` (404) otherwise, so there is no
  "exists but forbidden" oracle. The caller id comes from the gateway's `X-User-Id`, never from the body. A forged
  `PUT`/`DELETE` from another account is refused (code read, not run).
- **Resource limits:** the 280-character cap and the empty check run in `canPublish` in the browser and again in
  `ReplyServiceImpl.validateText` (code points of the stripped text). There is no rate limit on creating replies; it is
  already an accepted gap (`replies-design.md:169`, `twitter_tweet_service/PLAN.md:242`), so it is not repeated.
  `ReplyComposer` and `ReplyEditor` allow one request in flight (`isSending`, `isSaving`); `ReplyCell` ignores a second
  delete while `isDeleting`.
- **Caching and state races:** the page keeps `sentReplies`, `editedReplies` and `removedReplyIds` in memory only; the
  `<section key={openTweetId}>` in `TabPanels` remounts the page per post, so one post's replies, drafts and removed ids do
  not carry into another. `useTweetDetails` drops a response that lands after unmount (`isCancelled`). The like and
  save buttons keep one request in flight (`useOptimisticToggle`). A 404 on delete hides the reply locally; the
  server also answers 404 for "not yours", so the UI can show a reply as gone that still exists. That is a display
  quirk, not a security issue (nothing was deleted).
- **View counting:** `data-tweet-id={post.id}` comes from the server response, and the reporter deduplicates ids per
  page and per viewer on the server (see 2026-10-04). Finding 6 cannot be used to report a chosen id: a post must parse
  as a post first.
- **Tab panels:** `useTabScrollMemory` and `useTabVisits` handle only numbers, the `TabId` union and DOM events. No
  storage, URL, network or markup sink. `useActiveTab` and `useOpenTweetId` read `pathname` only (no query, no hash).
  Hidden tab pages stay mounted; their effect on request volume is finding 5.
- **Information disclosure:** failure paths show fixed strings (`Couldn't send. Try again.`, `POST NOT FOUND`, ...).
  Server messages are not rendered on this page (`describeSendFailure` ignores `ApiError.messages`).
- **Injection (server-side), CSRF, secrets, dependencies, caching of untrusted keys, ReDoS:** none new here. CSRF rests
  on the `SameSite=Strict` cookie as before, now also covering `POST`/`PUT`/`DELETE` on replies. The regexes touched
  are `bidi.ts` and none are new. `npm audit`: 0 vulnerabilities.
- **Still open, unchanged, outside this scope:** findings 1 (no CSP or referrer policy in `index.html`) and 2
  (`COOKIE_SECURE` defaults to false) were not re-checked in this pass.

## Audit — 2026-10-08 (profile page, profile links, `users.ts` / `tweets.ts` / `authorTweets.ts`)

**Scope:** `src/pages/ProfilePage/` (view, masthead, stats, tweets, edit sheet and its hooks), the profile links in
`PostCell`, `PersonCard` and `UserMenu` (the PROFILE entry), `routes.ts`, `useOpenUsername`, `endpoints.ts`, and the api
modules `users.ts`, `tweets.ts`, `authorTweets.ts`, `tweetPage.ts`, `http.ts`, `paging.ts`. The matching backend routes
were audited the same day in `../SECURITY-FINDINGS.md`. Read from code; nothing was run.

No new findings. Nothing here is exploitable today.

### Status of earlier findings (re-checked today)
- **Findings 3 and 6 (ids and addresses spliced into API paths): fixed for these routes.** `endpoints.ts` now wraps every
  path value in `segment()` (`encodeURIComponent`), including `user(username)`, `authorTweets(authorId)` and
  `follow(username)`. `ProfilePage` also refuses to call the API unless the address matches `USERNAME_PATTERN`
  (`/^[A-Za-z0-9_]{3,15}$/`, anchored, no backtracking), so `/users/..%2F..` shows USER NOT FOUND without a request.
  `fetchTweetCount` builds its query with `URLSearchParams`.
- **Finding 5 (`usePagedList` follows a cursor with no progress check): still open, one more consumer.** `ProfileTweets`
  passes `fetchAuthorTweets` to `PostList`, which uses `usePagedList`. The backend only returns a cursor when a next
  page exists (`TweetServiceImpl.findPageByAuthor`), so it does not loop today.
- **Findings 1 and 2 (no CSP or referrer policy; `Secure` cookie flag): not re-checked here.** `index.html` is unchanged
  in this scope.

### Considered, clean or not applicable (profile UI)
- **XSS / markup injection:** `bio`, `location`, `username` and tweet content are all rendered as React text nodes
  (`ProfileMasthead`, `PostCell`, `PersonCard`). No `dangerouslySetInnerHTML`, `innerHTML` or `href` built from data
  anywhere in `src`. `location` is upper-cased with `toUpperCase()` before rendering, which does not change that.
- **Bidi spoofing:** bio and location go through `stripBidiControls` in `ProfileMasthead`, tweet bodies in `PostCell`.
  Usernames are limited to `[A-Za-z0-9_]` by the backend, so they need no stripping.
- **Open redirect / `javascript:` links:** the profile links are `<Link to={profilePath(username)}>` and
  `navigate(profilePath(...))`, which encode the username and stay inside the router. No external URLs, no
  `window.open` or `location.href`.
- **Picture URLs:** `toPictureUrl` prefixes `BASE_URL` to the path the gateway sends (`/api/v1/files/{uuid}`, built from
  a file id). It is only used as `<img src>`, so a bad value could at worst fail to load and fall back to the default
  avatar. Not attacker-controlled.
- **Stale profile shown for the wrong user:** `useProfile` keeps the old profile while a new one loads, but `TabPanels`
  mounts `ProfilePage` with `key={openUsername}`, so each username starts from fresh state.
- **Own-profile check:** `isOwnProfile` compares usernames and only decides whether the edit button shows. The server
  uses the JWT subject for `PATCH /users/me`, so a spoofed view cannot edit another profile.
- **Edit sheet:** sends only `bio` and `location` as JSON with `Content-Type: application/json` and
  `credentials: 'include'`; the cookie is `SameSite=Strict`, so a cross-site page cannot send it (CSRF as before).
  Server refusal text reaches the DOM only as text. Client-side length limits mirror the server and are UX only; the
  server enforces them.
- **Information disclosure:** the SPA's `UserProfile` type leaves out `birthdate`, but the gateway still sends it to
  every logged-in viewer; see finding 3 in `../SECURITY-FINDINGS.md`, which is where it is fixed. `email` shown in the
  edit sheet comes from the signed-in user's own session.
- **Injection (server-side), SSRF, secrets, dependencies, caching, races:** no sinks in these files. No new
  dependencies were added.
