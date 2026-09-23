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
success." There's no authenticated screen yet, so a successful login/register does
`window.location.assign('/')` — the eventual feed's home. Revisit once that route exists.

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
