# SignalFlow Frontend — what's left

## Style migration — bring `src/` in line with `frontend-code-style`

Planned 2026-10-01.

### Design

One pass that brings existing code in line with the `frontend-code-style` skill, so that "match the surrounding code"
is safe again and later feature diffs stay clean. **No visual or behavioral change** apart from the two small ones
listed under Accepted gaps. Out of scope: the root-scaling refactor (removing `--home-scale`/`--auth-scale`), which is
its own section once this one ships.

Steps are ordered so `tsc -b` stays green after each one: config first, then structure (new files, moved types),
then names, then formatting, then CSS.

### Verification strategy

There are no frontend tests, so every gate combines:

- `npm run build` (`tsc -b && vite build`) and `npm run lint` clean
- the step's own `grep` checks (listed per step) returning nothing
- a manual check against the skill's review checklist for the files the step touched
- where a step can change runtime behavior, a smoke run with `npm run dev` against the gateway: sign in, load the
  feed, switch filters, load more, subscribe/unsubscribe, sign out, register a fresh account

### Steps

0. **Skill clarifications.** Three px/ID cases the inventory found aren't covered by `references/css.md`. Add them
   as named exceptions there: the `.sr-only` utility's `1px` clip, element-drawn hairlines up to `3px` (the
   `TopicCard` accent bar, the `Pager` stem), and `#root` in `index.css`.
   **Gate:** `css.md` lists all three; nothing else in the skill changes.

1. **Strict mode.** Add `"strict": true` to `tsconfig.app.json` and `tsconfig.node.json`. Fix whatever `tsc -b`
   reports. Known item: `main.tsx`'s `document.getElementById('root')!` becomes a narrowed lookup that throws a
   clear error when the element is missing. Re-indent `main.tsx` from 2 spaces to 4.
   **Gate:** build clean with strict on; `grep -rn '!)' src/main.tsx` empty.

2. **URL constants.** Create `src/api/endpoints.ts` (`ENDPOINTS`, the only reader of `VITE_BASE_API_URL`,
   `subscription(interestTopicId)` as a function) and `src/routes.ts` (`ROUTES`). `auth.ts`, `feed.ts` and
   `subscriptions.ts` drop their own `BASE_URL` and use `ENDPOINTS`. `App.tsx` routes and `navigate()` calls, and
   `AuthPage`'s `PATH_BY_MODE`, use `ROUTES`.
   **Gate:** build clean; `grep -rn "'/api\|\`/api\|VITE_BASE_API_URL" src` hits only `endpoints.ts`;
   `grep -rnE "'/(login|register)?'" src` hits only `routes.ts`; smoke run passes.

3. **Types and structure.**
   - Named interfaces instead of inline object types: `CurtainGreeting` (`App.tsx` state), the `COPY` value shapes
     in `Panel.tsx` and `AuthForm.tsx`. The burst kind in `TopicCard.tsx` becomes a named `BurstKind` union.
   - `Mode` is declared twice (`Panel.tsx`, `AuthForm.tsx`). Keep one, `AuthMode`, in `AuthPage/authMode.ts`, imported
     by `App`, `AuthPage`, `AuthForm` and `Panel`.
   - `countFor` exists twice (`Feed.tsx`, `FeedFilter.tsx`). Move one `countForFilter(counts, filter)` to
     `src/utils/feed.ts`; `FeedFilter` keeps its own `null` check.
   - `useMediaQuery` moves out of `AuthPage.tsx` into `AuthPage/useMediaQuery.ts`.
   - Component anatomy order (constants → pure helpers → props interface → component) in `Panel.tsx`, `AuthForm.tsx`
     and `FeedFilter.tsx`.

   **Gate:** build clean; `grep -rnE '<\{|: \{ [a-z]+:' src --include='*.tsx'` finds no inline object types;
   `grep -rn "type Mode" src` empty; smoke run passes (auth switch animation included).

4. **Naming.**
   - Booleans get `is`/`has`/`should`: `signingOut`, `covered`, `mobileFading`, `visible`, `scrollable`,
     `pastThreshold`, `reduceMotion`/`prefersReducedMotion`, `pending`, local `subscribed` state in `TopicCard`,
     `selected`, `loadingMore`, `cancelled`, `showPassword`, `filterChanged`. Props: `entering` → `isEntering`
     (`HomePage`, `AuthPage`), `active` → `isActive` (`AuthForm`), Pager `loading` → `isLoading`. `FeedFilter`'s
     `active` holds a filter value, not a boolean, so it becomes `activeFilter`. API fields (`FeedTopic.subscribed`)
     and `data-*` attribute names stay as they are, so the CSS doesn't change.
   - Units in constant names: `LOADING_INDICATOR_DELAY` → `LOADING_INDICATOR_DELAY_MS`, `SCROLL_THRESHOLD` →
     `SCROLL_THRESHOLD_PX`. `TopicCard`'s bare `1000` becomes `BURST_DURATION_MS`, with a coupling comment saying it
     must outlast the longest burst animation in `TopicCard.css` (950ms).
   - Vague names: `item`, `current`, `next`, `prev`, `ref`, `wrapped`, `fields`, `result` → names for what they
     hold (`currentTopics`, `topic`, `nextIsSubscribed`, `wasPasswordVisible`, ...).

   **Gate:** build clean; manual pass over every `useState` and prop of type `boolean` against the rule; smoke run
   passes.

5. **Formatting** (every `.ts`/`.tsx` plus `vite.config.ts`).
   - Semicolons everywhere; exactly two blank lines after the last import.
   - `if`/`else` and `try`/`catch`/`finally` in the skill's layout (`} else` on the brace line, blank line closing
     every branch but the last). Today's mix: `}\nelse` in `Feed.tsx`/`AuthForm.tsx`, `} else {` without the blank
     line in `AuthForm.tsx`/`FeedFilter.tsx`/`TopicCard.tsx`, `} catch` without it in `Feed.tsx`/`TopicCard.tsx`.
   - Chains: 2+ calls broken one per line (`createRoot(...).render(...)` in `main.tsx`); single calls joined back
     onto one line where today they're split (`BackToTop`'s plain `removeEventListener('resize', ...)`).
   - Crammed calls broken one argument per line; blank lines between logical steps (the guard-`if` runs in
     `AuthForm`'s validators, the `.then` bodies in `App.tsx`).
   - Comments: drop `TopicCard`'s "Trigger the one-shot animation" (says what, not why).

   **Gate:** build and lint clean; `grep -rnE '^\s*(else|catch|finally)\b' src` empty;
   `grep -rnE '^import .*[^;]$' src vite.config.ts` empty; checklist items 1–3 pass on every file.

6. **CSS.**
   - `index.css`: `body { font-size: 16px }` → `1rem`.
   - `Pager.css`: `translate(4px, 4px)` → `translate(calc(var(--home-scale) * 0.25rem), calc(var(--home-scale) * 0.25rem))`.
   - `TopicCard.css`: the six raw `rgba(...)` in the glow keyframes become tokens in `index.css` (`--accent-glow`
     0.22, `--accent-glow-soft` 0.10, `--danger-glow` 0.20, `--danger-glow-soft` 0.10), and the zero-alpha stops
     become `transparent`. Remove the 9 section banners, keeping any line that explains a why as a short comment.
   - `Panel`: drop the inline `style` prop. `AuthPage` passes a `position: 'full' | 'left' | 'right'` prop instead,
     rendered as `data-position` and styled in `Panel.css` with the same `left`/`width` values, so the existing
     340ms transition is unchanged.

   **Gate:** build clean; `grep -rn 'style=' src` empty; `grep -rnE 'rgba?\(|#[0-9a-fA-F]{3,8}' src --include='*.css'`
   hits only `index.css`; px left only under the allowed cases. Visual check at 320, 768, 1440 and 2560px: auth
   sign-in ↔ register switch (desktop side-by-side and stacked), subscribe and unsubscribe glow, pager hover arrow,
   success and logout curtain.

7. **Record decisions.** Add to `DECISIONS.md`: "`as` only in `src/api/`" and "one `AuthMode` in
   `AuthPage/authMode.ts`".
   **Gate:** both entries present.

### Accepted gaps

- **Extraction thresholds exceeded:** `AuthForm.tsx` (236 lines), `Feed.tsx` (201, with a ~50-line effect),
  `App.tsx` (181). These are a guideline, not part of a formatting pass. **Trigger:** the next feature that touches
  the file (`Feed` → `useFeedPages`, `App` → a curtain hook).
- **Long JSX lines** (`App.tsx` `<Route element={...}>` at ~200 characters). The skill only covers wrapping for calls
  and object literals, not JSX props. **Trigger:** you decide JSX props need a rule.
- **`TopicCard`'s burst timer isn't cleared on unmount.** Harmless in React 19. **Trigger:** the `Feed` extraction
  above, or a reported bug.
- **`auth-*` class prefix in `AuthForm`/`Panel`** is kept (decided in the grill-me).
- **Two small visual changes:** the pager hover offset now scales with the root font size (4px → 3.5px on phones),
  and the TopicCard glow keyframes start from `transparent` (identical in modern browsers, which interpolate with
  premultiplied alpha).

### Grill-me record (2026-10-01)

| Decision | Outcome |
|---|---|
| One skill or several | One `frontend-code-style` with `references/` for TS, React, CSS; HTML folded into React |
| Location | `SignalFlow/.claude/skills/`, next to `java-code-style` |
| `CLAUDE.md` split | Style → skill; architecture and a11y stay as hard rules |
| Enforcement | Skill text only; no ESLint/Prettier |
| Existing code | One migration pass (this section) |
| `interface` vs `type` | Interfaces for object shapes; `type` for unions/utility aliases; no inline object types |
| Strict | `"strict": true` (step 1) |
| `as` / `!` | `as` only in `src/api/`; `!` and `@ts-ignore` banned |
| URLs | `ENDPOINTS` + `ROUTES`; no URL/path literals elsewhere |
| Chains | 2+ calls broken; single inline; promise methods always broken |
| `if`/`else`, `try`/`catch` | Java layout: `} else`, blank line closing every branch but the last |
| Import order | No rule |
| Booleans | `is`/`has`/`should` prefix |
| State | Local state first; context only to avoid drilling; `use<Name>()` hook that throws |
| Theme | Dropped from the skill |
| Tables | TanStack via shared `DataTable`; pageable; skeleton on first load only; tables only |
| Units | px only for borders, outline, breakpoints |
| Scaling | Root font bands; per-page scale vars removed in the follow-up refactor |
| CSS naming | `<component>-<part>`, state on `data-*`/`aria-*` |
| Rules vs guidelines | Overflow, extraction thresholds, reuse, built-ins and comments are guidelines |
| Argument breaking | Only crammed calls (>~120 chars, or a function/object/nested multi-arg argument) |
