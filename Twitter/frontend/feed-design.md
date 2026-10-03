# Implementation Brief — Feed View

Build the authenticated home feed. This is the screen the user lands on after the auth descent completes. Self-contained; implement what is described and nothing beyond it.

---

## 1. Design Rationale

### Relationship to the auth flow
The auth screen was a full-bleed, single-field descent. The feed deliberately **does not** continue that metaphor — no depth gauge, no ruler, no travelling readout. The descent was an arrival ritual; it ends at the door.

What carries over is the **material**, not the device:
- the same two typefaces, used for the same jobs (mono for instrument text, Inter Tight for content)
- the same accent, used with the same discipline
- zero border-radius, zero shadows, hairline rules everywhere
- the same motion curves, so transitions feel like they belong to one product

Continuity through shared material rather than a repeated gimmick is what keeps the feed habitable for long sessions. A gauge that animated on every scroll would have become noise within a minute.

### Why bounded cells
Earlier iterations used full-bleed hairline dividers with no container. It read as one continuous column of text — it was genuinely hard to see where one post ended and the next began, which matters more as posts vary in length and image count. Each post is now a bounded cell on a slightly lifted surface, with internal rules separating author / content / actions.

### Why the author row is on top
A prior iteration put the byline *underneath* the content, so posts read as signed writing. It looked better in isolation, but with the action row below it, the author row ended up sandwiched between two blocks and it stopped being obvious which post the like belonged to. Reading order is now unambiguous: **author → content → images → actions.** Do not reorder this.

### Why the reading surface is wide and slow
620–660px measure, 18.5px body, 1.5 line-height, generous padding. Most feeds optimise for posts-per-screen. This one optimises for the post you are actually reading. Roughly two to three posts fit a desktop viewport. That is intended.

---

## 2. Design Tokens

### Colour
| Token | Value | Role |
|---|---|---|
| `--bg` | `#050E22` | Page background, header background, compose modal background |
| `--cell` | `#07142C` | Post cell surface — the only lifted surface in the UI |
| `--glow` | `#4E8BFF` | Accent. Roles: active/focus indication, post button, save-when-saved, loading indicator |
| `--pale` | `#E2E9F7` | Text base; all text colours are alphas of this |
| `--like` | `#FF3B5C` | **Like only** — heart fill, heart stroke when liked, like count when liked |
| `--alarm` | `#FF7A5C` | **Destructive and error only** — log out item, over-limit character count |
| `#FFFFFF` | — | Display names, typed input text |
| `#9CC0FF` | — | Primary button hover fill only |

`--like` and `--alarm` are both warm and sit near each other. They are separated by role, not by appearance: `--alarm` never appears on a post, `--like` never appears outside a heart. Do not introduce a third warm colour, and do not use `--alarm` for the heart.

Alphas on `--pale` (use exactly):
```
rgba(226,233,247,.94)  post body text
rgba(226,233,247,.85)  user avatar initials
rgba(226,233,247,.75)  dropdown menu items
rgba(226,233,247,.50)  brand wordmark
rgba(226,233,247,.42)  action icons (resting)
rgba(226,233,247,.40)  handles, timestamps, modal meta row
rgba(226,233,247,.35)  loading text
rgba(226,233,247,.30)  user avatar border (resting)
rgba(226,233,247,.24)  author avatar border
rgba(226,233,247,.20)  modal textarea placeholder
rgba(226,233,247,.18)  modal textarea rule (resting)
rgba(226,233,247,.16)  post cell border, dropdown border
rgba(226,233,247,.12)  internal rules inside a post cell
rgba(226,233,247,.10)  header bottom border, menu item dividers
rgba(78,139,255,.55)   post button border (resting)
rgba(78,139,255,.10)   user avatar fill, menu item hover
rgba(255,122,92,.10)   log out hover
```

Author avatar tints (background behind initials, `1px` border over it):
```
rgba(78,139,255,.18) rgba(127,212,193,.16) rgba(232,196,138,.16) rgba(255,155,176,.16)
rgba(156,192,255,.18) rgba(198,166,245,.16) rgba(111,227,176,.16) rgba(245,185,138,.16)
```
These are placeholders for real profile images. When real images exist, keep the `1px` border and the square crop.

### Typography
Two families only — the same two as auth. **Do not add a third.**

- **IBM Plex Mono** 400/500 — brand, handles, timestamps, counts, buttons, menu items, labels, loading text
- **Inter Tight** 400/500 — post body, display names, modal headline

| Element | Family | Size | Tracking | Other |
|---|---|---|---|---|
| Brand wordmark | Mono | `10.5px` | `.42em` | |
| Post body | Inter Tight 400 | `18.5px` (`17px` ≤600px) | `-.005em` | line-height `1.5` |
| Display name | Inter Tight 500 | `14.5px` | `-.01em` | `#fff` |
| Handle, timestamp | Mono | `9.5px` | `.1em` | |
| Like count | Mono | `10px` | `.1em` | `tabular-nums` |
| Post button | Mono | `10px` | `.17em` | |
| User avatar initials | Mono | `10px` | `.06em` | |
| Author avatar initials | Mono | `9.5px` | — | |
| Menu item | Mono | `10px` | `.14em` | |
| Loading text | Mono | `9.5px` | `.18em` | |
| Modal eyebrow | Mono | `10px` | `.2em` | `--glow` |
| Modal headline | Inter Tight 400 | `clamp(24px,4.6vw,40px)` | `-.032em` | line-height `1.04`, max `16ch` |
| Modal textarea | Inter Tight 400 | `clamp(17px,2.6vw,22px)` | — | line-height `1.5` |
| Modal meta / counter | Mono | `9.5px` | `.14em` | counter `tabular-nums` |
| Publish button | Mono | `12.5px` | `.14em` | |

### Spacing
```
Feed column max-width        660px
Column padding               20px 24px 140px
Gap between post cells       22px
Post cell border             1px
Author row padding           16px 22px   (14px 18px ≤600px)
Author row gap               11px
Body padding                 22px 22px 0 (18px 18px 0 ≤600px)
Image grid margin            20px 22px 0 (16px 18px 0 ≤600px)
Image grid gap               3px
Action row top margin        22px (18px ≤600px)
Action row padding           0 22px (0 18px ≤600px)
Action button padding        13px 0
Action icon ↔ count gap      10px
Header padding               18px 24px
Header right-group gap       16px
```

### Borders & elevation
- **`border-radius: 0` everywhere.** No exceptions, including avatars, buttons and images.
- **No box-shadows, no gradients, no blurs.**
- All rules `1px`. The post cell is the only element with a surface colour different from the page.

### Motion
| Curve | Value |
|---|---|
| `--e` | `cubic-bezier(.16,.84,.26,1)` |
| `--s` | `cubic-bezier(.34,.02,.18,1)` |

| Transition | Duration |
|---|---|
| Icon fill rise | `620ms` `--e` |
| Icon press scale | `460ms` `--e`, held `150ms` at `scale(.82)` |
| Post button fill | `420ms` `--e` |
| Post button colour | `360ms` `--e` |
| Dropdown open | opacity `220ms`, transform `380ms` `--e` |
| Menu item hover indent | `300ms` `--e` |
| Modal open | opacity `340ms`, transform `580ms` `--s` |
| Modal rule fill on focus | `720ms` `--e` |
| Generic colour changes | `250–340ms` |

---

## 3. Post Cell

Order is fixed: **author row → body text → image grid → action row.**

**Author row** — square avatar (`30px`, `1px` border, tinted fill, mono initials), display name, `@handle`, timestamp pushed to the far right with `margin-left:auto`. Closed by a `1px` bottom rule.

**Body** — plain paragraph. No truncation, no "show more".

**Image grid** — 0 to 4 images, `3px` gaps, square corners, no captions or overlays:
- 1 → full width, `16/9`
- 2 → two equal columns, `1/1` each
- 3 → first image spans both rows on the left at full height; two `16/9` images stacked on the right
- 4 → 2×2, `4/3` each

**Action row** — opened by a `1px` top rule. `justify-content: space-between`.
- **Left: like.** Heart icon + count. Count is `tabular-nums` so the row does not shift when it increments.
- **Right: save.** Bookmark icon, no count. Save is private; a public count there would change what the action means.
- **No reply, no repost.** Not in this build.

### No hover state on the post cell
The cell does not change on hover — no border colour shift, no corner marks, no background change. Only the two action buttons respond to the pointer. A whole row lighting up as the cursor crosses it is distracting in a scrolling feed.

---

## 4. Icons

Both are hand-drawn SVG paths on a `24×24` viewBox, `19px` rendered. **No icon library.**

```
heart:    M12 20.4C4.4 15.2 2.6 11.8 2.6 8.9 2.6 6.2 4.7 4.1 7.3 4.1c1.8 0 3.4.9 4.3 2.3h.8c.9-1.4 2.5-2.3 4.3-2.3 2.6 0 4.7 2.1 4.7 4.8 0 2.9-1.8 6.3-9.4 11.5z
bookmark: M5.8 3.2h12.4v17.6L12 16.1l-6.2 4.7z
```

### The fill animation
Each icon is **two copies of the same path**:
1. an outline copy — `fill:none`, `stroke:currentColor`, `stroke-width:1.5`, round joins and caps
2. a solid copy — filled with `--like` (heart) or `--glow` (bookmark), clipped by a `<clipPath>`

The clipPath contains a single `<rect>` that covers the whole viewBox and is translated **down and out of view** at rest (`transform: translateY(24px)`). Toggling the active state moves it to `translateY(0)` over `620ms` on `--e`. The solid shape therefore **fills from the bottom upward**, like water rising — not a colour swap, not a scale-in.

Each icon instance needs a **unique clipPath id**; duplicate ids across a list silently break every instance after the first.

On press, the whole SVG scales to `.82` and releases over `460ms` — a single damped compression, no overshoot or bounce.

When active, the outline stroke and the count also take the active colour, so the whole control reads as on.

Requirements: `aria-pressed` reflects state; `aria-label` on each button; `aria-hidden="true"` on the SVG. Optimistic UI — toggle immediately, reconcile with the server after, revert on failure.

---

## 5. Header

Sticky, page background, `1px` bottom rule. Constrained to the same `660px` measure as the feed so the brand aligns with the left edge of the cells.

- **Left:** brand wordmark, mono, heavily tracked.
- **Right:** post button, then user avatar.

**Post button** — ghost, not solid. `1px` border in `rgba(78,139,255,.55)`, `--glow` text, `34px` tall. A `9px` plus drawn from two `1px` bars, not a glyph or an icon. On hover a solid `--glow` panel scales up from the bottom (`transform-origin: bottom`, `scaleY(0)` → `scaleY(1)`) and the text inverts to `--bg`. Same upward-fill direction as the action icons — this is deliberate; keep them consistent. Below `600px` the label is hidden and the button becomes a square plus.

**User avatar** — `32px` square, `1px` border, `rgba(78,139,255,.1)` fill, mono initials. Border brightens to `--glow` on hover. When the menu is open, four `4px` `--glow` squares appear at the corners, offset `-2px`. In production this is the user's profile image with the same square crop and border.

**Dropdown** — opens below the avatar, `200px` min-width, `#071634` surface, `1px` border. Items: EDIT ACCOUNT / SAVED TWEETS / LIKED TWEETS / LOG OUT. Hover tints the row and indents the text by `6px` over `300ms`. LOG OUT is `--alarm` and gets a warm hover tint. Closes on outside click; clicks inside do not close it. Needs Escape-to-close, arrow-key navigation, focus return to the avatar on close, and `aria-expanded` on the trigger.

---

## 6. Compose Modal

Opened by the post button. **Not an inline composer** — an inline box at the top of the feed competes with the first post and makes posting feel incidental.

Constructed from the auth step so posting feels like the same kind of event as signing in:
- mono eyebrow "NEW POST" in `--glow`
- Inter Tight headline, max `16ch`
- textarea above a `1px` rule; focusing it fills the rule `scaleX(0)` → `scaleX(1)` from the left over `720ms` — the identical focus treatment used in auth
- meta row: attach-image control on the left, `n / 280` counter on the right; counter turns `--alarm` past the limit
- publish button (solid `--glow`, mono, `⌘↵` hint) and a text cancel

Full-bleed over the page on `--bg`, entering with opacity `340ms` and `translateY(30px)` → `0` over `580ms` on `--s`. Autofocus the textarea ~80ms after open. `Escape` closes; `⌘/Ctrl + Enter` publishes. Needs a focus trap while open, `role="dialog"` + `aria-modal="true"`, focus returned to the post button on close, and background scroll locked.

---

## 7. Pagination

Infinite scroll. Fire the next fetch when the scroll position is within `500px` of the bottom, guarded by a busy flag so one scroll cannot trigger several requests. Append to the existing list; never replace it.

Loading state is a `7px` `--glow` square blinking on a 1s two-step `steps(2,end)` cycle, beside mono "FETCHING MORE" text. **No spinner, no skeleton rows.**

Production requirements the prototype does not cover: cursor-based pagination (offsets duplicate posts when new ones arrive at the head), an end-of-feed state when the cursor is exhausted, an error state with a retry control, and a `scroll` listener that is throttled or replaced with an `IntersectionObserver` on a sentinel element.

---

## 8. Accessibility & Responsive

- Each post is an `<article>`. Timestamps use `<time datetime="...">` with the full ISO value; the `2h` display form is the text content.
- Like and save are `<button>` with `aria-pressed`, `aria-label`, and visible keyboard focus. Do not rely on colour alone — the fill state is a shape change too.
- Images need real `alt` text from the uploader. The index label in the placeholder is prototype scaffolding; remove it.
- Announce appended posts via a polite live region ("5 more posts loaded"). Do not move focus on append.
- `prefers-reduced-motion: reduce` collapses all transitions and animations to `0.01ms`. The icon fill then snaps rather than rising; everything stays legible.
- Verify the `.33`–`.42` alpha action icons meet 3:1 against `--cell`; raise the alpha if not. Do not lower any alpha below the listed values.

**≤600px:** body `17px`; author row `14px 18px`; body padding `18px 18px 0`; image margin `16px 18px 0`; action row padding `0 18px`, top margin `18px`; post button loses its label. Everything else is already fluid. Respect `env(safe-area-inset-*)`.

The post button sits in the header, which is a long thumb reach on tall phones. If it tests badly, move it to a bottom-anchored trigger below `600px` and keep the header version above — but keep the ghost treatment and the upward fill either way.

---

## 9. Do NOT Build

- Reply or repost actions, reply threads, quote posts
- A save count, or any public counter besides likes
- Hover states on the post cell
- An inline composer at the top of the feed
- Rounded corners anywhere — including avatars and images
- Box-shadows, gradients, blurs, glows
- An icon library — both icons are the two paths in §4
- A third typeface
- Spinners, skeleton loaders, progress bars
- Any depth gauge, ruler, water, wave, bubble or particle effect carried over from auth
- Ambient or looping background animation
- A light theme
- `--alarm` anywhere on a post; `--like` anywhere outside a heart; `--glow` outside its four assigned roles
- Verified badges, follow buttons, trending sidebars, search, notifications