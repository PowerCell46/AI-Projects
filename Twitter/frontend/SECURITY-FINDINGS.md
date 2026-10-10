# Security findings — frontend

## Audit — 2026-10-04

**Scope:** `Twitter/frontend/src` (auth flow, feed, saved posts, compose modal with image upload, likes, view
reporting), `index.html`, `vite.config.ts`, `npm audit`. Where the SPA trusts the backend (picture URLs, image
content types, cookie flags, CSRF posture, views endpoint) the gateway, tweet service and timeline service code was
read to confirm the contract; those services have their own audits. Builds on `twitter_mail_service/docs/SECURITY-AUDITS.md`, which
predates the feed, compose, likes, saved and views code; that code is the new ground here.

2 findings fixed or confirmed not a hole (1, 4); the open ones (2, 3) are kept privately.

### 1. Low — no Content-Security-Policy and no referrer policy (fixed 2026-10-10; was open from 2026-10-01)
- **Location:** `index.html` (no CSP, no `<meta name="referrer">`, Google Fonts `<link>`).
- **Mechanism:** the SPA and the API share one origin (relative `/api/...`, cookie auth). If any script were ever
  injected, there is no CSP to limit it, and the cookie is `HttpOnly` so the damage would be actions as the user,
  not token theft. The third-party fonts stylesheet is CSS that Google could use to restyle the login form. The
  `/confirm?token=…` URL is sent as `Referer` to Google Fonts in browsers whose default policy is not
  `strict-origin-when-cross-origin`. Verified today: `index.html` has neither mitigation.
- **Demonstration:** `curl -sI http://localhost:5173/ | grep -i content-security-policy` returns nothing. In an
  older or policy-configured browser, open `http://localhost:5173/confirm?token=abc` and read the `Referer` on the
  `fonts.googleapis.com` request in DevTools, Network.
- **Fix (applied 2026-10-10):** `<meta name="referrer" content="same-origin">` in `index.html`; the fonts are served
  from `public/fonts`; `nginx/security-headers.conf` sends a CSP (`script-src 'self'`, `style-src 'self'` plus inline
  style attributes, `img-src 'self' blob:`), `Referrer-Policy` and `nosniff` on the pages, `/assets/` and `/fonts/`.
  Checked in Chromium against the built image: no violations, no request to another origin. HSTS is sent since 2026-10-10.

### 2. (moved out of the public repo, 2026-10-10)

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

1 finding (5) moved out; it is kept privately.

### 5. (moved out of the public repo, 2026-10-10)

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

No findings of its own; the open earlier ones are kept privately.

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

1 finding fixed (6); the open ones are kept privately.

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
  needs a real tweet id. `tweetId` is user input that reaches API paths. The server side holds: the tweet service binds `tweetId` and `replyId` as
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
- **Status: fixed 2026-10-07.** `useOpenTweetId` returns the id only when it is a UUID, and `TabPanels` redirects any
  other `/tweets/...` address to `/feed` (a bare `..` cannot be neutralised by encoding: the URL parser treats `%2E%2E` as
  a dot segment too). `ENDPOINTS` also encodes every id segment now (`api/endpoints.test.ts`), which closes the path part for ids. Tests: `TabPanels.test.tsx`
  (`should_open_the_feed_when_the_tweet_id_is_not_a_uuid`), `endpoints.test.ts`.

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
- **Authorization / IDOR on replies:** `canEdit` and `canDelete` in `ReplyCell` only decide which buttons show. The
  server decides: `updateContentIfAuthor(replyId, tweetId, callerId, ...)` is one conditional write, and `delete` allows
  the reply's author or the post's author and answers `ReplyNotFoundException` (404) otherwise, so there is no
  "exists but forbidden" oracle. The caller id comes from the gateway's `X-User-Id`, never from the body. A forged
  `PUT`/`DELETE` from another account is refused (code read, not run).
- **Caching and state races:** the page keeps `sentReplies`, `editedReplies` and `removedReplyIds` in memory only; the
  `<section key={openTweetId}>` in `TabPanels` remounts the page per post, so one post's replies, drafts and removed ids do
  not carry into another. `useTweetDetails` drops a response that lands after unmount (`isCancelled`). The like and
  save buttons keep one request in flight (`useOptimisticToggle`). A 404 on delete hides the reply locally; the
  server also answers 404 for "not yours", so the UI can show a reply as gone that still exists. That is a display
  quirk, not a security issue (nothing was deleted).
- **View counting:** `data-tweet-id={post.id}` comes from the server response, and the reporter deduplicates ids per
  page and per viewer on the server (see 2026-10-04). Finding 6 cannot be used to report a chosen id: a post must parse
  as a post first.
- **Information disclosure:** failure paths show fixed strings (`Couldn't send. Try again.`, `POST NOT FOUND`, ...).
  Server messages are not rendered on this page (`describeSendFailure` ignores `ApiError.messages`).
- **Injection (server-side), CSRF, secrets, dependencies, caching of untrusted keys, ReDoS:** none new here. CSRF rests
  on the `SameSite=Strict` cookie as before, now also covering `POST`/`PUT`/`DELETE` on replies. The regexes touched
  are `bidi.ts` and none are new. `npm audit`: 0 vulnerabilities.

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
- **Information disclosure:** the SPA's `UserProfile` type leaves out `birthdate`. `email` shown in the edit sheet
  comes from the signed-in user's own session.
- **Injection (server-side), SSRF, secrets, dependencies, caching, races:** no sinks in these files. No new
  dependencies were added.
