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

## Panel transition timing implemented with two setTimeout calls

`AuthPage` drives the cover → swap → uncover sequence with `setTimeout` at 300ms/360ms (matching
the spec's timing table) rather than `transitionend`, since the content swap has to happen while
still covered regardless of whether the CSS transition fires (e.g. if a browser drops the
transition). Guarded by `isAnimatingRef` per the spec's re-entrancy note.
