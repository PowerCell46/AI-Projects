# SignalFlow — Topic feed view ("Etched Panel")

Implement the signed-in topic feed: a centered single-column list of interest topics with a segmented filter, a subscribe/unsubscribe toggle per card, paged loading, and a back-to-top control.

---

## 1. Design rationale

The feed's only interaction is a binary toggle repeated 20+ times per page. Everything in this design exists to make **subscription state readable at a glance while scrolling**, without turning the page into a wall of coloured chips.

- **Card-level state, not button-level state.** A subscribed topic is identified by an amber rule down the card's left edge. The eye can scan the left margin of the column and resolve state without reading a single button. Relying on the button label alone forces a read per card.
- **One accent, one job.** Amber `#DCA82F` appears only for subscribed state and the active filter. No other colour is used anywhere in the UI, so colour always means the same thing.
- **The state change is animated, not snapped.** The left rule wipes in from 0 to 3px over 260ms with a decelerating curve. The toggle is optimistic and instantaneous in data terms; the motion exists to show *what changed* on a page where 20 near-identical cards make an instant swap easy to miss.
- **Narrow column, tall cards.** Content is capped at 660px — a comfortable measure for 1–2 sentence descriptions — and cards are given generous internal padding so roughly 4–5 land per viewport. Density is deliberately traded away: the user is evaluating topics, not triaging a queue.
- **The pager reports position.** Instead of a bare "Load more", the control shows how many of the total are loaded, a hairline progress track, and the action. A user scrolling a 248-item list needs to know where they are.

---

## 2. Design tokens

Define these as CSS custom properties on `:root` in a stylesheet file. **All sizing must be expressed in `rem`** (divide the px values below by 16). The only exceptions are 1px hairline borders and the 3px subscribed rule, which stay in px so they don't scale into blurriness.

### Colour

| Token | Value | Use |
|---|---|---|
| `--bg` | `#0D1117` | Page background |
| `--surface` | `#121A23` | Card background, resting |
| `--surface-hover` | `#141D26` | Card background, hover |
| `--surface-sub` | `#151D26` | Card background, subscribed |
| `--surface-raise` | `#161E28` | Active filter cell background |
| `--line` | `#1E2731` | Card border resting, filter border |
| `--line-hover` | `#2C3945` | Card border hover |
| `--line-sub` | `#2A3340` | Card border subscribed |
| `--line-btn` | `#2A3440` | Button border resting |
| `--line-btn-hover` | `#3E4C5A` | Button border hover |
| `--line-chrome` | `#202932` | Back-to-top border |
| `--accent` | `#DCA82F` | Subscribed state, active filter, progress fill |
| `--accent-wash` | `rgba(220,168,47,.07)` | Subscribed button fill |
| `--accent-line` | `rgba(220,168,47,.45)` | Subscribed button border |
| `--text` | `#E4EBF2` | Headings, card titles |
| `--text-2` | `#A7B3C0` | Descriptions, button labels |
| `--text-3` | `#6E7C8C` | Categories, counts, sub-headline |
| `--focus` | `#4C8DFF` | Focus ring |
| `--track` | `#1B2430` | Pager progress track |

### Type

Two families, loaded from Google Fonts:

- **Inter** (400 / 500 / 600) — all UI and prose.
- **JetBrains Mono** (400 / 500) — counts, pager readout only. Monospace is reserved for values, never for labels or prose.

| Role | Size | Weight | Notes |
|---|---|---|---|
| Page title | 25px | 600 | letter-spacing `-0.015em` |
| Page sub-headline | 14px | 400 | line-height 1.6, `--text-3`, max-width 56ch |
| Filter label | 13px | 400 | |
| Filter count | 11px | 400 | mono, opacity 0.6, 8px left margin |
| Card title | 17px | 600 | letter-spacing `-0.012em` |
| Card category | 12.5px | 400 | `--text-3` |
| Card description | 14px | 400 | line-height 1.7, max-width 62ch |
| Button label | 13px | 400 | |
| Pager readout | 11.5px | 400 | mono, letter-spacing `0.02em` |

Sentence case throughout. No all-caps labels anywhere.

### Geometry & spacing

| Token | Value |
|---|---|
| Card radius | 4px |
| Filter group radius | 4px |
| Button radius | 3px |
| Card padding | 30px top/right/bottom, 32px left |
| Card gap | 26px |
| Feed top margin | 40px (below filter) |
| Filter top margin | 28px (below sub-headline) |
| Page top padding | 78px |
| Page bottom padding | 160px |
| Pager top margin | 44px |
| Subscribed rule width | 3px |

No shadows. No gradients except the fade behind the fixed back-to-top area if one is needed for legibility. No elevation system — depth is expressed through background steps only.

### Motion

| Property | Duration | Easing |
|---|---|---|
| Subscribed rule wipe (width 0 → 3px) | 260ms | `cubic-bezier(.22,1,.36,1)` |
| Card background / border | 200ms | ease |
| Button border / background / colour | 220ms | ease |
| Filter cell | 160ms | ease |
| Back-to-top | 180ms | ease |

Wrap all transitions so they are disabled under `prefers-reduced-motion: reduce`.

---

## 3. Layout

Single centered column. Header, feed, and pager all share the same column width and alignment — nothing is full-bleed.

```
              ┌─────────── 42% / max 660px ───────────┐
              │  Feed                                  │   page title
              │  Subscribe to a topic to route it …   │   sub-headline
              │                                        │
              │  ┌────┬──────────┬────────────────┐   │   segmented filter
              │  │ All 248 │ Subscribed 12 │ Not … │   │
              │  └────┴──────────┴────────────────┘   │
              │                                        │
              │ ▌┌────────────────────────────────┐   │   ← 3px amber rule
              │  │ Bitcoin              Crypto    │   │     (subscribed only)
              │  │ Spot action, ETF flows and …   │   │
              │  │ ┌──────────────┐               │   │
              │  │ │  Subscribed  │               │   │
              │  │ └──────────────┘               │   │
              │  └────────────────────────────────┘   │
              │                26px                    │
              │  ┌────────────────────────────────┐   │
              │  │ Meta Platforms       Equities  │   │
              │  …                                     │
              │                                        │
              │  82 of 248 ────────────── [ Next 20 ] │   pager
              └────────────────────────────────────────┘
                                                    (↑)  fixed back-to-top
```

Column width: `42%`, `min-width: 440px`, `max-width: 660px`, `margin: 0 auto`.

Responsive steps:
- ≤1280px → width `52%`
- ≤1000px → width `70%`
- ≤760px → width `100%`, `min-width: 0`, page side padding 24px (16px below 480px)

On the narrowest breakpoint the card's category may drop to a second line beneath the title rather than sitting right-aligned; do not shrink the description below 14px.

---

## 4. Components

### Header

Static — it scrolls away with the page. Contains the page title, a one-line sub-headline explaining what subscribing does, and the filter group. No sticky behaviour, no search field, no avatar or account chrome in this view.

### Segmented filter

Three joined cells inside a single 1px-bordered, 4px-radius group with `overflow: hidden`. Cells are divided by 1px borders; the last cell has none. Padding 8px × 17px.

- Resting: `--text-3` on transparent.
- Hover (inactive only): `--text-2` on `#141C25`.
- Active: `--accent` on `--surface-raise`.

Each cell shows its label followed by a monospace count. Counts are: **All** = total topics, **Subscribed** = the user's subscription count, **Not subscribed** = total minus subscribed. These require per-state totals from the API alongside the paged results; if the endpoint cannot supply them, omit the counts entirely rather than computing them from the loaded page — a count that only reflects loaded rows is worse than no count.

Implement as a tablist: `role="tablist"` on the group, `role="tab"` and `aria-selected` on each cell.

### Topic card

Structure: title row (name, category pushed right), description, action button.

- Resting: `--surface` background, 1px `--line` border, 4px radius, `overflow: hidden`.
- Hover: background `--surface-hover`, border `--line-hover`. The whole card responds; it is not clickable, so use no cursor change and no transform.
- Subscribed: background `--surface-sub`, border `--line-sub`, and the left rule at 3px.

The left rule is a pseudo-element pinned to the card's left edge, full height, `width: 0` at rest and `width: 3px` when subscribed, with the card clipping overflow so it sits flush inside the border radius. Animating `width` (not `transform` or `opacity`) is what produces the wipe.

### Subscribe button

- Label: `Subscribe` when not subscribed, `Subscribed` when subscribed.
- **`min-width: 126px`** — this is required. Without it the button resizes between the two labels and the card's layout jitters on every toggle.
- Resting: 1px `--line-btn` border, `--text-2` label, transparent fill, 3px radius, padding 8px vertical.
- Hover: border `--line-btn-hover`, label `--text`.
- Subscribed: border `--accent-line`, label `--accent`, fill `--accent-wash`.
- Subscribed + hover: border to full `--accent`.

One click each way — no confirmation on unsubscribe. Set `aria-pressed` to reflect state. While a toggle request is in flight, keep the optimistic state and disable pointer events on that single button only; on failure, revert the card and surface a message that says what failed and that it can be retried.

### Pager

A row of three parts, aligned to the column: the monospace readout (`82 of 248`), a flexible 1px `--track` hairline with an `--accent` fill spanning the loaded proportion, and the action button (`Next 20`) styled like the subscribe button at rest, with hover taking border and label to `--accent`.

The readout total changes with the active filter — under **Subscribed** it reports against the subscription count, not the global total.

While loading the next page, replace the button label with `Loading` and disable it; leave already-rendered cards untouched. When everything is loaded, replace the whole action with the readout and a full track — do not leave a dead button.

### Back to top

Fixed, bottom-right, 42px square, 28px from both edges. 1px `--line-chrome` border, `#121921` background, `--text-3` arrow. Hover: `--text` arrow, `#3A4855` border, `#18212B` background. Smooth scroll on click, respecting reduced-motion. It may be always visible, or fade in after roughly one viewport of scroll — either is acceptable; do not animate it beyond opacity.

### Empty state

When a filter yields nothing, render centered text in `--text-3` at 14px in place of the feed, with the pager hidden: *"No topics in this view. Switch to All to find something to follow."* Directive, not apologetic.

---

## 5. Accessibility

- Focus ring: 2px solid `--focus`, 3px offset, on every interactive element. Never remove outlines without replacement.
- Card titles are heading elements at the level below the page title; cards are `article` elements.
- The subscribe button carries `aria-pressed`. Subscription state must not be conveyed by the amber rule alone — the button label is the accessible signal.
- Body text at `--text-2` on `--surface` and all accent-on-surface pairings meet WCAG AA for their size; verify after any colour adjustment.
- The filter is keyboard-navigable as a tablist; loading a new page must not steal focus from wherever the user is.

---

## 6. Out of scope

Do not build or alter:

- Authentication, sign-up, or sign-in screens.
- Global navigation, header chrome, account menus, or logout.
- Search, sorting, or category filtering beyond the three subscription states.
- Auto-loading infinite scroll — loading is explicitly user-triggered via the pager.
- Topic detail views, or making cards clickable or linked in any way.
- Any state persistence beyond the existing subscribe/unsubscribe endpoints.
- Light theme. This view is dark only.
- Toasts, modals, or confirmation dialogs for the toggle.

## 7. Styling constraints

- Styles live in a separate stylesheet, not inline. Tokens as custom properties; component rules keyed off a data attribute for subscription state rather than conditional class strings.
- No CSS framework utilities, no shadow, no gradient, no transform on hover, no `text-transform: uppercase`.
- Colour appears in exactly two places: subscribed state and the active filter. If a new element seems to need a colour, it needs a background step instead.