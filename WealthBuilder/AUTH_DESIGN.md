# WealthBuilder — Auth Screen Implementation Brief

Build a single auth screen that contains both login and register forms, with a sliding "cover panel" that swaps sides when the user toggles modes. Visual register: vintage CRT terminal (phosphor green on dark, scanlines, vignette, soft glow). Transition: VHS tracking-band sweep.

---

## ⚠️ Sizing rule (non-negotiable)

**All sizes — typography, spacing, padding, margins, radii, line heights, gaps, icon dimensions, and component widths/heights — MUST be expressed in `rem` or `em`. No `px` values anywhere except for:**

- `1px` hairline borders and dividers
- Glow `box-shadow` blur radii (where px reads more predictably than rem)
- The scanline pattern's `1px`/`3px` repeating gradient stops

Set the root font size on `:root` (default `16px`); every other size derives from it. `em` is appropriate inside components where a child should scale relative to its parent's font-size (e.g. button padding relative to button text); use `rem` everywhere else for predictable global scaling. The whole layout should remain proportional at root sizes from `14px` to `20px` without rework.

---

## Design rationale

Investment apps default to either sterile fintech (cards, gradients, pastel charts) or heavy-handed institutional (navy, serif, gold). Neither matches a developer-built portfolio tracker. The CRT terminal direction reframes the product as a tool, not a brochure — it implies precision, command-line literacy, and a user who reads their P/L the way they'd read a log file. Phosphor green is doing double duty as both the "old terminal" cue and the "positive return" cue, with terminal red reserved for losses and destructive paths (the "switch mode" link). The VHS sweep transition extends the analog-screen metaphor into motion: a channel change rather than a slide. It's a deliberate aesthetic risk that earns its keep by being completely consistent with the cover-art treatment.

---

## Design tokens

### Color

| Token | Value | Use |
|---|---|---|
| `--bg-stage` | `#0A1208` | Outer stage background, cover side |
| `--bg-form` | `#0C1610` | Form side background |
| `--bg-app` | `#040506` | Page background behind the stage |
| `--phosphor` | `#7AFFA0` | Primary text, borders, focus, positive |
| `--phosphor-dim` | `rgba(122, 255, 160, 0.5)` | Dashed borders, secondary text |
| `--phosphor-faint` | `rgba(122, 255, 160, 0.18)` | Idle input borders |
| `--terminal-red` | `#FF6B6B` | The "switch mode" link, errors, losses |
| `--scanline` | `rgba(0, 0, 0, 0.18)` | Scanline overlay stop color |
| `--vignette` | `rgba(0, 0, 0, 0.55)` | Vignette outer stop |

### Type

- **Family:** `"IBM Plex Mono", ui-monospace, "Cascadia Code", "JetBrains Mono", monospace` — load from Google Fonts, weights 300/400/500/600.
- **Glow:** `text-shadow: 0 0 0.375rem rgba(122, 255, 160, 0.6)` applied globally to phosphor text. For the red link: `text-shadow: 0 0 0.375rem rgba(255, 107, 107, 0.7)`.
- **Scale:**
  - Eyebrow / micro-label: `0.6875rem`, letter-spacing `0.22em`, weight 400
  - Body / link copy: `0.75rem`, letter-spacing `0`
  - Field value: `0.875rem`, weight 400
  - Button label: `0.8125rem`, letter-spacing `0.18em`, weight 500
  - Form heading: `1.5rem`, letter-spacing `-0.01em`, weight 500
  - Logo mark: `0.6875rem`, letter-spacing `0.22em`, weight 500
- **Line height:** `1.35` for the ASCII chart block, `1.5` body default.

### Spacing

Base unit `0.25rem` (4px at default root). Use multiples: `0.25 / 0.5 / 0.75 / 1 / 1.25 / 1.5 / 2 / 2.25 / 2.5 / 2.75 / 3` rem.

- Stage internal padding (cover side): `2.25rem`
- Stage internal padding (form side): `2.75rem`
- Form field vertical rhythm: `1.125rem` between fields
- Heading bottom margin: `2rem`
- Eyebrow bottom margin: `1.625rem`

### Borders & radii

- **No border-radius anywhere.** All corners square. The terminal aesthetic depends on this.
- Hairline divider: `1px solid rgba(255, 255, 255, 0.08)` on the outer stage frame.
- Input idle bottom border: `1px dashed var(--phosphor-dim)`.
- Input focus bottom border: `1px solid var(--phosphor)`.
- Button border: `1px dashed var(--phosphor)`.

### Stage dimensions

- Max width: `60rem`
- Aspect ratio: `16 / 10`
- Split: two equal halves, `50%` each, absolute-positioned, transitioning `left`.
- Mobile (< `40rem` viewport): collapse to single column. Cover panel becomes a compressed header above the form (`20vh` tall, ASCII chart truncated to the heading + the figure). The slide animation becomes a vertical curtain instead — same VHS bands, sweeping the same direction.

---

## Layout structure

A single container with two halves:

- **Cover half** (signature visual): logo mark top-left, ASCII candlestick chart vertically centered, `[ END_OF_TRANSMISSION ]` footer caption bottom-left.
- **Form half**: eyebrow label, heading, two fields (username, password), primary button, mode-switch line.

In **login mode**, cover is on the left, form on the right. In **register mode**, cover is on the right, form on the left. The cover panel's `left` property is what animates.

### Copy

- Cover logo: `▮ WEALTHBUILDER v0.1`
- Cover footer: `[ END_OF_TRANSMISSION ]`
- Login eyebrow: `>> LOG ON`
- Register eyebrow: `>> NEW USER`
- Login heading: `identify_self` (with blinking `_` cursor appended)
- Register heading: `enroll_new` (with blinking `_` cursor appended)
- Field labels: `USR`, `PWD` (rendered as small phosphor eyebrows above each input; each input is prefixed inline by a `:` glyph)
- Login button: `[ LOG ON ]`
- Register button: `[ ENROLL ]`
- Login switch line: `NO RECORD ON FILE? enroll`
- Register switch line: `RECORD EXISTS? log on`

The switch verb (`enroll` / `log on`) is the only red element on the screen. It's the link that triggers mode change.

### ASCII chart (cover art)

Render as a `<pre>` block, monospace, phosphor color, with the glow shadow applied. Exact content:

```
   P/L 30D                       +12.4%
   ┌─────────────────────────────────┐
   │                          ▄█     │
   │                       ▄▄██      │
   │                    ▄▄█▀▀        │
   │              ▄█▄▄▄▀             │
   │           ▄▄█▀                  │
   │      ▄▄▄▄█                      │
   │  ▄█▀▀                           │
   │▀▀                               │
   └─────────────────────────────────┘
    JAN  FEB  MAR  APR  MAY  JUN
```

Font-size for the pre block: `0.6875rem`.

---

## CRT overlays

Two full-stage overlay layers, both `pointer-events: none`, both sitting above the content (`z-index: 50` and `51`):

1. **Scanlines.** `background: repeating-linear-gradient(0deg, var(--scanline) 0px, var(--scanline) 1px, transparent 1px, transparent 3px)`. Keep the `px` here — `rem` would break the visual.
2. **Vignette.** `background: radial-gradient(ellipse at center, transparent 55%, var(--vignette) 100%)`.

The scanline layer is the more distinctive of the two — do not skip it.

---

## VHS Tracking Bands transition

Trigger: user clicks the red switch-mode link. Duration: `800ms` total.

### Mechanics

The transition reveals the new mode from the **top down** behind a sweep front. Three horizontal phosphor bands ride along that front with staggered offsets, jittering horizontally as they travel — they're the analog-noise cue.

Implementation outline:

1. On click, snapshot the current mode and the target mode. Lock further clicks until the animation finishes.
2. Drive a single `progress` value from `0` → `1` over `800ms` using `requestAnimationFrame`. Do not use a CSS keyframe — the bands and the reveal share this clock.
3. Render two layered stage compositions:
   - **Base layer:** the current mode, full-stage.
   - **Sweep layer:** the target mode, full-stage, but clipped vertically to `height: ${progress * 100}%` from the top. This is what gets revealed.
4. Render three thin horizontal band elements at vertical positions `((progress + offset) % 1) * 100%` for `offset ∈ [0, 0.33, 0.66]`:
   - The lead band (offset 0) is taller (`1.125rem`), with a soft phosphor gradient and a strong outer glow (`box-shadow: 0 0 1.5rem var(--phosphor)`).
   - The two trailing bands are thinner (`0.375rem`), flat phosphor at 50% alpha, dimmer glow.
   - Each band gets a small horizontal `translateX` jitter driven from `sin(progress * 40 + bandIndex) * 0.375rem`. The math creates organic-looking analog wobble without random numbers.
   - `mix-blend-mode: screen` on the bands so they read as light, not paint.
5. When `progress` reaches `1`, commit the target mode as the new current mode and unmount the sweep layer.

### Reduced motion

Respect `prefers-reduced-motion: reduce`. Replace the animated sweep with an instantaneous swap (no bands, no progress, no transition delay). The form heading should still update; everything else cuts.

### Mobile

The same horizontal band mechanic, but sweeping from top of the (now vertical) layout to bottom — same code, just a taller stage on small viewports.

---

## States

### Input

- **Idle:** bottom border `1px dashed var(--phosphor-dim)`, text color `var(--phosphor)` with glow, placeholder color `rgba(122, 255, 160, 0.3)`.
- **Focus:** bottom border becomes `1px solid var(--phosphor)`. No transition longer than `150ms` — focus should feel snappy, not eased.
- **Filled:** identical to idle. The phosphor glow already signals presence.
- **Error:** bottom border `1px solid var(--terminal-red)`, helper text below in `var(--terminal-red)` with its own red glow, prefixed `! `.

### Button

- **Idle:** transparent background, `1px dashed var(--phosphor)` border, phosphor text with glow.
- **Hover:** background fills with `rgba(122, 255, 160, 0.08)` over `150ms`. Border becomes solid `1px solid var(--phosphor)`. No translate, no scale, no shadow change.
- **Active (mousedown):** background `rgba(122, 255, 160, 0.15)`, instant (no transition).
- **Disabled:** opacity `0.4`, cursor `not-allowed`, no hover effect.

### Switch-mode link

- Color `var(--terminal-red)`, glow as defined. On hover, the red glow intensifies (`text-shadow: 0 0 0.5rem rgba(255, 107, 107, 0.9)`). No underline.

### Blinking cursor

A `_` character (or `█` for the logo mark, designer's call — `_` is the chosen default here) animated with `@keyframes blink { 0%, 49% { opacity: 1 } 50%, 100% { opacity: 0 } }` at `1s steps(2) infinite`. The `steps(2)` is important — it must hard-snap, not fade.

---

## Accessibility & quality floor

- All form controls have associated `<label>` elements (visible eyebrows count if `htmlFor` is wired up).
- Visible keyboard focus: in addition to the border change, add a `2px` solid `var(--phosphor)` outline with `2px` offset on `:focus-visible` for inputs and buttons. This is the one place `px` is acceptable for outline values — but feel free to express as `0.125rem` if preferred.
- Color contrast: phosphor (`#7AFFA0`) on `#0C1610` background passes WCAG AA for body text. The terminal-red link on the same background also passes. Verify with your contrast checker before shipping.
- The CRT scanline and vignette overlays must not block pointer events.
- `prefers-reduced-motion`: bands disabled, blink cursor reduced to a static visible state.
- Form is submittable by `Enter` from either field.

---

## What this brief does not cover

- Backend auth wiring.
- Validation rules / regex (use whatever the project standard is).
- Routing after successful login.
- "Forgot password" flow — not in the scope of this screen.
- The mobile layout collapse is described directionally; final breakpoint values are left to the implementer.