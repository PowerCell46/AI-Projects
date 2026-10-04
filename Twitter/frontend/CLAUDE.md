Read the repo-level `../CLAUDE.md` first (frontend code rules and the `frontend-code-style` skill). This file covers
running and testing this app.

## What this is

The Twitter SPA: the "Hadal Descent" auth flow (`/login`, `/register`, `/confirm`, `/resend`) and the feed
(`/feed`, `/saved`). Vite 8, React 19, TypeScript (strict), react-router-dom 7, plain co-located CSS. Plans and design
decisions: `PLAN.md`, `DECISIONS.md`; the visual briefs: `AuthenticationViewsDesigns.md` (auth), `feed-design.md` (feed).

`/feed` and `/saved` sit behind `ProtectedRoute` in one layout route, `Shell` (header, compose modal, `<Outlet>`).
Both render `PostList` with a different `fetchPage`. The shell hands the posts you published this session to the feed
through outlet context (`useShellContext().ownPosts`).

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
  the stack, runs the suite and tears it down; `npm run test:fast` skips the image build.

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
- Likes are a **stub**: the gateway's `PUT`/`DELETE /api/v1/likes/{tweetId}` answer `204` and store nothing, so every
  post loads at `0` and a reload forgets your like. Replace the UI's starting state when a likes service exists.
- Views: `ViewReporter` (`src/utils/viewReporter.ts`) is a module-level singleton that queues post ids (once per page
  load, after 1 s at half visible) and posts them every 5 s and on hide. Tests reset it.
- `--header-height` in `src/index.css` is shared by the header and the sticky `NEW POSTS` button; the compose modal
  locks scrolling with `data-scroll-locked` on `<html>`.
