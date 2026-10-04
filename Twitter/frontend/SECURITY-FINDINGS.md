# Security findings — frontend

## Audit — 2026-10-04

**Scope:** `Twitter/frontend/src` (auth flow, feed, saved posts, compose modal with image upload, likes, view
reporting), `index.html`, `vite.config.ts`, `npm audit`. Where the SPA trusts the backend (picture URLs, image
content types, cookie flags, CSRF posture, views endpoint) the gateway, tweet service and timeline service code was
read to confirm the contract; those services have their own audits. Builds on `exploit-report-2026-10-01.md`, which
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
