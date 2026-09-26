# Decisions

Calls made during implementation that aren't obvious from the code alone.

## Error copy is frontend-owned, not passed through

`auth-design.md` mandates specific error copy ("That email and password don't match an account.",
"An account already exists for that email.") that differs from the gateway's actual messages
(`Invalid email or password.` / `An account with this email already exists.`). `AuthForm` maps the
HTTP status (401 on sign in, 409 on register) to the design's copy itself rather than rendering
`response.json().messages`. Any other status (validation 400s, 5xx, network failure) falls back to
a generic "Couldn't reach the server. Try again."

## Redirect target on success

The design's "Out of scope" section excludes routing/session logic "beyond the redirect on
success." This used to be a `window.location.assign('/')` full reload; it is now an in-app
handoff — `AuthForm` passes the `AuthUser` returned by `login`/`register` up to `App`, which
sets auth state and `navigate('/')`s mid-transition so the feed mounts underneath the success
curtain. No extra `me()` round trip; `prefers-reduced-motion` skips the curtain and navigates
immediately.

## Success transition is a full-viewport vertical curtain, not the auth panel

The signin/register switch animates the panel horizontally inside the auth container, but the
success transition has to reveal the feed (a different page layout, and mobile has no panel),
so `SuccessCurtain` is a fixed full-viewport overlay owned by `App`: cover 340ms (same
cubic-bezier as the panel) → 1400ms hold on the greeting (long enough to read without
hurrying) → 340ms lift revealing the feed, which rises/fades in underneath via `HomePage`'s
`entering` prop. Greeting is heading-only ("Welcome back" / "Account created") plus the
user's email; it rides the curtain away rather than dismissing separately.

## Logout mirrors the success transition in reverse

Sign-out plays the same curtain top-to-bottom (`direction="down"`, accent edge on the bottom):
cover → 1400ms "Signed out" + email hold → curtain continues down revealing `/login`, whose
container settles from above via `AuthPage`'s `entering` prop. `App` owns the whole sequence
(the `logout()` call fires in parallel with the cover so feedback is instant, and a failed
request still signs out locally, as before); `HomePage` just reports the click through
`onSignOutRequest`. Reduced-motion skips the curtain in both directions.

## Error text uses a red, against the spec's "Red is not in this system"

`auth-design.md` §9 explicitly bans red, including for field-invalid state. Explicitly asked to
change the error message color anyway (white didn't read as "wrong"), so `.auth-error` now uses a
new `--danger: #f85149` token (chosen for ~5.6:1 contrast on `--bg`, GitHub Primer's dark-mode
danger red) instead of `--text`. Left everything else alone — no red on input borders or
`aria-invalid` styling, so the deviation stays scoped to the one place it was asked for.

## Dashboard feed built against `GET /api/v1/feed`, not the raw topic-service routes

`dashboard-feed.md` needs per-state (All/Subscribed/Not subscribed) paging and counts that
`GET /api/v1/interest-topics` can't provide (it only filters by `categoryId`) and
`GET /api/v1/subscriptions` can't either (full list, no topic detail). Flagged this gap in
`../MISSING_ENDPOINTS.md` and paused; the gateway grew `GET /api/v1/feed` (cursor `after`, `items`
already flagged `subscribed`, `nextCursor`, and `counts` for all three states in one response) to
close it. `src/api/feed.ts` targets that endpoint exclusively — the dashboard never calls
`/api/v1/interest-topics` or `/api/v1/subscriptions` (`GET`) directly. Subscribe/unsubscribe still go
through the existing `POST`/`DELETE /api/v1/subscriptions` endpoints.

Card ticker was dropped from `dashboard-feed.md` itself (no `ticker` field exists on any topic DTO) —
not implemented, not a gap to close later unless that field gets added.

## Switching the segmented filter discards the other filters' loaded pages

Each of All/Subscribed/Not subscribed re-fetches from the start (`after=null`) on switch rather than
caching per-filter pagination state. Simpler, and the pager's "N of total" readout stays honest without
tracking three independent cursors; cost is losing scroll position/progress in a filter you switch away
from and back to.

## New CSS tokens added rather than reusing near-identical existing ones

`src/index.css` already had `--surface-focus` and `--accent-wash`, used by `AuthForm` for its own input
focus and submit-hover states. `dashboard-feed.md`'s token table specifies close-but-different tokens for
the feed (`--surface-hover` at the same hex as `--surface-focus`, an `--accent-wash` at a different alpha
than the existing one). Added new tokens (`--surface-hover`, `--accent-wash-soft`, `--surface-sub`,
`--surface-raise`, `--surface-filter-hover`, `--line-sub`, `--line-btn-hover`, `--line-chrome`,
`--line-chrome-hover`, `--surface-chrome`, `--surface-chrome-hover`, `--accent-line`, `--track`) instead
of repurposing the existing pair, so `AuthPage` styling can't shift as a side effect of this page.

## Back to top is always visible, not scroll-triggered

The spec allows either. Picked the simpler one — no scroll listener, nothing to guard for
`prefers-reduced-motion` beyond the existing hover/click transitions.

## Toggling subscribe/unsubscribe never removes or reorders a card

Un/subscribing while viewing "Subscribed"/"Not subscribed" leaves the card in place with its state
flipped, rather than dropping it out of the now-mismatched filter view. `dashboard-feed.md` describes the
toggle purely as a card-level visual state change and says nothing about live list membership; removing
cards out from under a scrolling user seemed like the worse surprise.

## Panel transition timing implemented with two setTimeout calls

`AuthPage` drives the cover → swap → uncover sequence with `setTimeout` at 300ms/360ms (matching
the spec's timing table) rather than `transitionend`, since the content swap has to happen while
still covered regardless of whether the CSS transition fires (e.g. if a browser drops the
transition). Guarded by `isAnimatingRef` per the spec's re-entrancy note.

## Mobile scale-down via root font-size at ≤480px, no touch-target floors

At `max-width: 480px` `index.css` drops the root to 87.5% (percent, not px, so browser
font-size prefs still apply) plus `body` to `0.875rem`, shrinking all rem-based sizing
~12% app-wide in one rule. Tried px floors holding controls at 44px first — looked
unchanged, explicitly asked to drop them, so buttons/inputs now sit at ~38.5px on
phones. Known exception to the 44px touch-target rule.

## Dashboard large-screen scaling mirrors auth's stepped scale var

Feed had no rules past 1280px, so at 2560+ it sat ~660px wide with small type.
Added `--home-scale` on `.home-page` (1 / 1.2 / 1.5 / 1.65 / 2 at 1920/2560/3440/3840,
same bands as `--auth-scale`) with rem sizes wrapped in `calc()` across the header and
all five feed stylesheets. px hairlines, radii, and keyframes stay fixed, same as auth.

## Tablet auth capped at 30rem instead of full-width stacked

At 481–900px the stacked phone layout stretched inputs to ~710px (desktop content is
~390px), so the container is capped at 30rem and stays centered. Side-by-side can't fit
(it needs ~884px); phones ≤480px stay full-bleed.

## Tablet type bumped 12.5% at 768–900px and portrait 901–1100px

Root goes to 112.5% plus `body` to `1rem` in the tablet bands, mirroring the mobile
87.5% step in magnitude. Portrait clause covers large-tablet stacked auth (and the
dashboard there); landscape 901–1100px stays 100% with side-by-side auth. The 768–900
band is width-only so large-phone landscape also catches it, accepted.

## Extra-small phones get a 75% root at ≤375px

320px-wide phones still felt oversized at 87.5%, so a second band drops the root to
75% there. Scoped to ≤375px (covers 320–375, incl. iPhone SE) so the approved 390px
look doesn't shift. Same band also tightens header/card/column padding, since chrome
ate ~22% of a 320px row. Band `body` sizes are `1rem` (track root) not compounded
percents of an already-scaled root.

## Portrait tablets stack auth instead of squeezing side-by-side

At 1024px portrait the 820px fixed container gave 282px fields. Stacked condition is
now `(max-width: 900px), (max-width: 1100px) and (orientation: portrait)` in CSS and
the `isNarrow` hook together (they must match or the switch animation desyncs), with
the 30rem cap extended over the same portrait range. Landscape 901–1100px keeps the
tight side-by-side; rare and accepted.
