# Twitter Frontend — plan

**Status: built 2026-10-01 (all 12 steps done); open items are in the TODO section.** Designed via `/grill-me` on 2026-10-01 (Q-numbers in parentheses).
Visual source of truth: `AuthenticationViewsDesigns.md` (the "Hadal Descent" brief, §-numbers below). This plan
records where the build departs from or extends the brief; everything not mentioned here is built as the brief says.

**Hard rule:** no step starts on a red or missing test, each step ends green. Invoke `frontend-code-style` before
touching any `.ts`/`.tsx`/`.css`.

## Design in short

- **Stack (copied from `../../SignalFlow/frontend`):** Vite 8, React 19, TypeScript 6 (`strict`), react-router 7,
  oxlint, 4-space indent, plain co-located CSS. Dev server `:5173`, `/api` proxied to the gateway on `:8080` (Q10).
  The proxy target comes from an env var in `vite.config.ts` (default `http://localhost:8080`) so e2e can repoint it.
  No frontend container or Caddy yet (Q10).
- **New dependencies (need approval in step 1):** `react-router-dom`; dev: `vitest`, `jsdom`,
  `@testing-library/react`, `@testing-library/user-event`. In `../e2e`: `@playwright/test`, `pg`, `@types/pg` (Q11–12).
- **Fonts:** Google Fonts `<link>` in `index.html`: IBM Plex Mono 400/500, Inter Tight 400, `display=swap` (Q13).
- **Routes (`src/routes.ts`):** `/login`, `/register`, `/confirm`, `/resend`, `/feed`; `/` and unknown paths →
  `/feed` or `/login` by session (Q8–9). On app load `me` decides the session; logged in → auth routes redirect
  to `/feed`; logged out → `/feed` redirects to `/login`.
- **API (`src/api/endpoints.ts` + `src/api/auth.ts`):** `register`, `login`, `logout`, `me`, `confirm`,
  `resendConfirmation`. `AuthApiError` carries `status` and the gateway's `messages[]` (`ErrorResponseDTO`
  `{status, messages, timestamp}`); a failed `fetch` (network) throws it with status `0`.
- **Units:** the brief's px become rem (÷16) per the style skill; px only for borders/hairlines and the `720px`
  breakpoint. Root font `100%` on `html`, one band (the brief is already fluid via `clamp()`).

### Flows (one machine, step arrays per flow — §4)

| Flow | Steps | Seafloor |
|---|---|---|
| login | identifier (text, `autocomplete="username"`) → password (`current-password`) | 4900 |
| register | email (`email`, `autocomplete="email"`) → username (`username`) → password (`new-password`) | 10910 |
| resend (Q3) | `IDENTITY` / "Where should we send it?" / placeholder "email address", type email, depth 140 | 4900 |

- **Steps don't change the URL** (Q9): each advance pushes a history entry on the same path with the step index in
  `history.state`; `popstate` steps back. Footer link switches `/login` ↔ `/register` (index 0, horizon 140,
  forward transition, §5). Refresh restarts at step 1 — nothing typed is persisted.
- **Horizon positioning (§4 fix):** the horizon element is the stage's full height and moves by
  `translateY(y%)`, so `%` resolves against the stage height, the same basis as the ticks.

### Validation and errors

- **Client checks mirror `RegisterRequestDTO` exactly (Q5)**, run on Enter before advancing; the gauge holds.
  Email `^[\w.+-]+@[\w-]+\.[a-zA-Z]{2,}$` and ≤254; username `^[A-Za-z0-9_]{3,15}$`; password 8–72 chars,
  ≤72 UTF-8 bytes, a lowercase, an uppercase and a digit. Messages are the server's wording, authored uppercase:
  `EMAIL MUST BE A VALID EMAIL ADDRESS`, `USERNAME MUST BE 3 TO 15 LETTERS, DIGITS OR UNDERSCORES`,
  `PASSWORD MUST BE 8 TO 72 CHARACTERS`, `PASSWORD MUST CONTAIN A LOWERCASE LETTER, AN UPPERCASE LETTER AND A DIGIT`.
  Login and resend only check non-empty (login never reveals the rules); resend also checks email format.
- **Empty field:** `NO VALUE ENTERED — HOLDING AT DEPTH`, gauge holds (§5).
- **Server responses → screen:**

| Response | Where | Gauge | Message |
|---|---|---|---|
| register `409` "Email already registered." | back to step 1 (Q4) | travels to step 1 depth | `EMAIL ALREADY REGISTERED` |
| register `409` "Username already taken." | back to step 2 | travels to step 2 depth | `USERNAME ALREADY TAKEN` |
| register `400` | earliest failing field's step | travels there | that field's server message |
| login `401` | password step | rises 420 m, alarm colours (§5) | `INVALID CREDENTIALS — RISING 420 M` |
| login `403` (Q6) | password step | holds | `PLEASE CONFIRM YOUR EMAIL FIRST` + secondary `RESEND LINK` → `/resend` (email prefilled if the identifier contains `@`) |
| network / `5xx` / anything else (Q7) | current step | holds | `SIGNAL LOST — TRY AGAIN`; Enter retries |

  Server messages are shown uppercased with the trailing period dropped. Typed values survive every error; any
  keystroke clears it (§5). 409s are told apart by exact message text — see accepted gaps.

### Screens beyond the brief

- **Register arrival (Q1):** eyebrow `ACCOUNT ESTABLISHED`, headline "Check your inbox.", mono line
  `CONFIRMATION SENT TO <EMAIL>`, secondary `RESEND` button, footer → login. No redirect — no session exists.
- **Login arrival:** brief copy; holds for the rule draw (150 ms + 1.3 s), then `navigate(ROUTES.feed)`;
  reduced motion → immediate.
- **`/confirm` (Q2):** same stage, no input. Calls `confirm` once on mount (guarded by a ref — StrictMode runs
  effects twice in dev and the token is single-use). While pending: the arrival rule draws as the loader (§5).
  - `204` → eyebrow `ACCOUNT CONFIRMED`, headline "You're cleared to descend.", horizon to 4900, primary `LOG IN` → `/login`.
  - `400` or no `token` param → alarm treatment `LINK EXPIRED OR ALREADY USED`, secondary `RESEND LINK` → `/resend`,
    footer → login.
  - network/other → `SIGNAL LOST — TRY AGAIN` with a retry button.
- **`/resend` (Q3):** the one-step flow above. On submit (always `202`) → arrival: eyebrow `LINK DISPATCHED`,
  line `IF AN ACCOUNT IS WAITING, A NEW LINK IS ON ITS WAY`, footer → login.
- **Resend cooldown (Q14):** after any send, the button reads `LINK SENT` and is disabled for 60 s (matches the
  gateway's `CONFIRMATION_RESEND_COOLDOWN`), on the register arrival and on `/resend`.
- **`/feed` placeholder (Q8):** `@username` from `me` and a `LOG OUT` button (`logout` → `/login`). Nothing else;
  the real feed replaces it.

### Accessibility deltas

- **Contrast (Q15):** the `.34`, `.40` and `.50` pale alphas all fail 4.5:1 on `--deep` (2.6 / 3.2 / 4.4). All three
  become `.52` (4.7:1). `.26`/`.30` stay — decorative, `aria-hidden`.
- **Focus (Q17):** the input's rule fill is its focus indicator. Buttons and links get a `1px` `--pale` outline
  with an offset on `:focus-visible` only — no `--glow`, no radius.
- Touch targets ≥ 44×44 px: buttons and the footer link get `min-height: 2.75rem` / padding.
- Everything else per §6 (labels, one `<form>`, persistent hidden identifier, live region, `role="alert"`,
  reduced motion).

## Test strategy (Q11–12)

- **Unit (Vitest):** depth maths, validators (cases mirror the register `400` rows of
  `../twitter_api_gateway/TESTING.md`), error → screen mapping, API module with a stubbed `fetch`.
- **Component (Vitest + RTL, jsdom):** flows with a stubbed API module and fake timers (`vi.useFakeTimers`,
  `requestAnimationFrame` faked) — no real waits. Assert DOM state, ARIA and `data-*` attributes, not pixels.
- **E2E (Playwright, `../e2e/`, layout as `../../SignalFlow/e2e`):** own compose project — Postgres, Kafka,
  MinIO, gateway built from `../twitter_api_gateway/Dockerfile` (`BCRYPT_STRENGTH=4`, `CONFIRMATION_LINK_BASE_URL`
  → the e2e frontend's `/confirm`, `OUTBOX_POLL_FIXED_DELAY_MS` set to a day so outbox rows are never published
  and deleted, cleanup cron set to never). Playwright's `webServer` starts Vite with the proxy pointed at the e2e
  gateway. `reducedMotion: 'reduce'`, fresh `e2e-<uuid>` user per test, `retries: 0`, Playwright's auto-waiting
  only (no `waitForTimeout`). The confirmation link comes from the newest `outbox` row whose `payload->>'email'`
  matches (`pg`).
- **Manual gates:** visual check vs the brief at 1440, 720 and 320 px, plus reduced motion; iOS keyboard (step 10).
- **Exit:** `npm run build`, `npm run lint`, `npm test` clean, and `npx playwright test` green **3× in a row**.

## Steps — all done 2026-10-01

1. **Scaffold:** Vite, React, TS, oxlint, tokens in `index.css`, routes, endpoints, proxy to the gateway.
2. **Pure logic:** `depth`, `flows`, `validation`, `authErrors` (unit-tested).
3. **API module:** `src/api/auth.ts`, `AuthApiError` (status `0` on a network failure).
4. **Stage and depth readout:** `DescentStage`, `Ruler`, `Horizon`, `useDepthCount`.
5. **Step machine:** `StepFlow`, `useStepFlow`, `useStepTransition`; history-driven steps.
6. **Login and register wiring:** error table, arrival states, resend cooldown, `AuthContext`, route guards.
7. **`/feed` placeholder:** `@username` and `LOG OUT`.
8. **`/confirm`:** one confirm call under StrictMode, expired and signal-lost states.
9. **`/resend`:** one-step flow, prefill from router state, always-202 arrival.
10. **iOS keyboard check:** iPhone 17 Pro, Safari, every flow: the question and input stayed visible, so no
    `visualViewport` fix. How to run it: `LAN-DEV-SERVER.md`.
11. **E2E:** `../e2e`, 10 Playwright tests, green 3× in a row from a fresh stack. The e2e gateway sets
    `CONFIRMATION_RESEND_COOLDOWN=0s`, and the `typeStep` helper waits for `data-slide="idle"` because the form
    ignores Enter while a step slides in.
12. **Finish:** style review (no violations), `exploit-report-2026-10-01.md` (no Critical, High or Medium), `CLAUDE.md`.

Exit state: 156 unit and component tests, `npm run build` and `npm run lint` clean.

## TODO

- [ ] **Visual check at 720 px and 320 px** (carried over from step 4), with reduced motion on and off. Check
      `/login`, `/register`, the arrival states, `/confirm` and `/resend`. Only 1440 px and the iPhone were looked at.
- [ ] **Footer switch animation:** `/login` ↔ `/register` remounts the page instead of animating the step block
      forward and returning the horizon to 140 m, as brief §5 describes. Needs one shared page component (or a key
      kept across both routes).
- [ ] **Referrer policy** (exploit report #1, Low): add `<meta name="referrer" content="same-origin">` to
      `index.html`, so the confirmation token in `/confirm?token=…` never leaves the origin. Before any deployment.
- [ ] **Content-Security-Policy and self-hosted fonts** (exploit report #2, Low): with the first frontend
      deployment, send a CSP and serve the fonts locally so `style-src 'self'` works.
- [ ] **E2E with motion on** (optional): the suite runs headless Chromium with `reducedMotion: 'reduce'`, so step
      transitions are never exercised. Add one project or test for it if a motion bug shows up.
- [ ] **Commit:** nothing from `frontend/`, `e2e/`, `.env.example` or the plans is committed yet.

## Accepted gaps — revisit when the named trigger lands

- **409s are told apart by exact message text** ("Email already registered." / "Username already taken.").
  **Trigger:** either message changes → give `ErrorResponseDTO` a `field` or `code`.
- **Validation rules live twice** (frontend + `RegisterRequestDTO`, Q5). **Trigger:** any rule change → both
  sides in the same change.
- **No frontend container / Caddy on `:80`** (Q10); the confirmation link base differs per environment.
  **Trigger:** first deployment, or the mail service's e2e.
- **Fonts load from Google** (Q13). **Trigger:** offline use or a privacy requirement → self-host.
- **Alpha hierarchy flattened** to `.52` for contrast (Q15). **Trigger:** a design pass that wants it back with
  passing values.
- **Resend cooldown is a client timer;** a reload re-enables the button (the server still throttles silently).
- **Login by username can't prefill `/resend`** — the email is unknown.
- **Session expiry (JWT 1h) is only checked on app load;** `/feed` doesn't react to a mid-session 401.
  **Trigger:** the real feed → a shared 401 handler.
- **No forgot-password link** — no backend flow (§7). **Trigger:** password reset exists.

## Out of scope
Everything in brief §7; the real feed, profiles, tweets; frontend containerisation; email templates.
