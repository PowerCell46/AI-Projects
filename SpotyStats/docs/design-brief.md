# Spotistats — Design System & App Shell Implementation Brief

## Design rationale

A personal Spotify listening diary deserves a visual identity that feels premium and atmospheric rather than dashboard-y. The "Vinyl Glass" direction uses a deep navy base with frosted-glass surfaces and a single warm amber accent — evoking the feeling of late-night listening sessions without leaning on Spotify's own green. Generous spacing and large typographic moments make the data feel reflective, not transactional.

The design avoids: neon effects, gradients on text, harsh contrast, dense data-table aesthetics, and the default Spotify green (which would make the app feel derivative).

---

## Design tokens

### Colors

```
--bg-base:          #0b0d1a   /* deep navy, page background */
--bg-glow:          #1a1530   /* radial glow center, top-left */
--panel:            rgba(255, 255, 255, 0.04)   /* default glass panel */
--panel-strong:     rgba(255, 255, 255, 0.06)   /* active nav, hover */
--panel-inset:      rgba(255, 255, 255, 0.025)  /* list rows inside panels */

--border:           rgba(255, 255, 255, 0.09)   /* panel borders */
--border-soft:      rgba(255, 255, 255, 0.04)   /* inset row borders */
--border-strong:    rgba(255, 255, 255, 0.12)   /* button borders */

--text:             #f4f1ea   /* primary text, slight warm tint */
--text-mute:        #8a8fa3   /* labels, metadata */
--text-mute-soft:   #b0b4c4   /* secondary content (artist/album) */

--accent:           #f4b860   /* warm amber, single accent */
--accent-text:      #1a1530   /* text on accent backgrounds */

--chart-1:          #f4b860   /* amber */
--chart-2:          #c9a3ff   /* lavender */
--chart-3:          #7ad7c5   /* mint */
--chart-4:          #f48fb1   /* pink */
--chart-5:          #8aa9ff   /* periwinkle */
--chart-6:          #3a3f55   /* slate (rest) */

--track-art-bg:     #22263a   /* placeholder album art */
```

### Page background

```css
background: radial-gradient(1200px 800px at 0% 0%, var(--bg-glow) 0%, var(--bg-base) 55%);
```

### Typography

- Font family: `'Inter', system-ui, sans-serif`
- Page title (h1): **44px / weight 600 / letter-spacing -1px / line-height 1.1**
- Section title (h2): **18px / weight 600**
- Stat value: **38px / weight 600 / letter-spacing -1px / line-height 1**
- Track title: **15px / weight 600**
- Nav item: **15px / weight 500**
- Body / list value: **13px / weight 400**
- Metadata, labels: **12px / weight 400**
- Eyebrow label (uppercase): **12px / weight 600 / letter-spacing 2px / uppercase**
- Stat eyebrow (uppercase): **11px / weight 600 / letter-spacing 1.5px / uppercase**
- Tabular numerics: `font-variant-numeric: tabular-nums` on all numbers in lists/stats

### Spacing & layout

- Layout: 2-column grid, `grid-template-columns: 300px 1fr`
- Sidebar padding: **36px 28px**
- Main content padding: **48px 56px 64px**
- Vertical gap between main sections: **40px**
- Stat row grid: **4 columns, gap 20px**
- Content grid below stats: **1.45fr 1fr, gap 24px**
- Track list internal gap: **14px**

### Border radii

- Panels / cards: **20px** (large surfaces), **18px** (stat cards), **14px** (small surfaces, list rows)
- Nav items: **12px**
- Pill buttons / toggles: **999px**
- Track artwork: **10px**

### Glass effect (apply to all panels)

```css
background: var(--panel);
border: 1px solid var(--border);
backdrop-filter: blur(12px);
```

### Transitions

- Nav item hover: `background 150ms ease`
- Button hover: `background 150ms ease, border-color 150ms ease`
- Toggle switch: `background 150ms ease`

---

## Application name & branding

- **Name:** Spotistats
- **Logo treatment:** Wordmark only. **Do NOT add an icon or symbol next to it.** Just the word "Spotistats" in **19px / weight 600 / letter-spacing 0.3px**, color `var(--text)`.

---

## What to build

### 1. App shell — two-column layout

A `300px` left sidebar and a fluid-width main content area. The sidebar and main share the same gradient background (no divider between them).

### 2. Sidebar

Contents from top to bottom:

1. **Brand wordmark** — "Spotistats", standalone (no symbol/icon). Padding `0 4px`, margin-bottom **48px**.
2. **Navigation items** — vertical list, gap **6px**. Items: `Today`, `History`, `Artists`, `Liked`, `Insights`, `Profile`.
   - Each item: padding `14px 18px`, border-radius `12px`, font-size `15px`, font-weight `500`.
   - **Inactive:** color `var(--text-mute)`, transparent background, transparent 1px border.
   - **Active:** color `var(--text)`, background `var(--panel-strong)`, 1px border `var(--border)`, `backdrop-filter: blur(10px)`.
   - **Hover (inactive):** background `var(--panel)`, color `var(--text)`.
3. **Bottom area** — **REMOVE the account/profile card from the bottom of the sidebar entirely.** The sidebar should end at the navigation list. Profile access lives in the "Profile" nav item.

### 3. Main content — page header

- Eyebrow label: `LISTENING DIARY` — 12px, uppercase, letter-spacing 2px, weight 600, color `var(--accent)`, margin-bottom 10px.
- H1: "Your week in sound" — 44px, weight 600, letter-spacing -1, line-height 1.1.
- Right side: two buttons (see Button styles below).

### 4. Stats row — 4 metric cards

Grid of 4 equal columns, gap 20px. Each card:

- Padding: `28px 26px`
- Background: `var(--panel)`, border: 1px solid `var(--border)`, border-radius: 18px, `backdrop-filter: blur(12px)`
- **Label** (top): 11px, uppercase, letter-spacing 1.5px, weight 600, color `var(--text-mute)`, margin-bottom 16px
- **Value**: 38px, weight 600, letter-spacing -1, line-height 1, color `var(--text)`
- **Sublabel**: 12px, color `var(--text-mute-soft)`, margin-top 10px

### 5. Content grid below stats — history (left) + chart (right)

`grid-template-columns: 1.45fr 1fr; gap: 24px`. Both panels use the standard glass treatment with **border-radius 20px, padding 32px 32px 28px**.

#### Left panel — daily history

- Header row: section title (e.g. "Thursday, June 5") on the left, track count on the right (12px, mute, letter-spacing 1).
- Track list, gap 14px. Each row:
  - Grid: `56px 1fr auto`, gap 18px, align-items center
  - Padding: `16px 18px`, border-radius 14px
  - Background: `var(--panel-inset)`, border: 1px solid `var(--border-soft)`
  - Left: 56×56 album art, border-radius 10px, fallback bg `var(--track-art-bg)`
  - Middle: title (15px / 600) + "artist · album" (12px / `var(--text-mute-soft)`), both truncated with ellipsis
  - Right: heart icon (16px, `var(--accent)` when liked, `#3a3f55` when not) → played-at time (tabular) → duration (tabular), gap 18px

#### Right panel — artist breakdown

- Header: "By artist" + segmented toggle (see Toggle below) with options `Tracks` / `Time`.
- Center: donut chart, SVG viewBox `0 0 100 100`, outer radius 40, inner hole radius 24 with `var(--bg-base)` fill. Center text: large number (e.g. "847") at 9px weight 600, "tracks" label at 5px `var(--text-mute)`. Use the 6 chart colors in order of descending value; lump the tail into `--chart-6`.
- Below: legend list, gap 12px. Each row: 10px color dot + artist name on the left, tabular value on the right (`var(--text-mute-soft)`).

---

## Button styles — REWORK FROM THE PREVIEW

The pill buttons in the preview were not the right direction. Use this revised system:

### Primary button (e.g. "Refresh")

- Padding: `11px 22px`
- Background: `var(--accent)` (#f4b860)
- Color: `var(--accent-text)` (#1a1530)
- Border: none
- Border-radius: **10px** (NOT pill / 999px)
- Font: 13px / weight 600
- Hover: `background: #f6c47e` (slight lighten), no transform
- Active: `background: #e0a64a`

### Secondary button (e.g. "This week")

- Padding: `10px 22px` (1px less vertical to account for border)
- Background: transparent
- Color: `var(--text)`
- Border: 1px solid `var(--border-strong)` (rgba(255,255,255,0.12))
- Border-radius: **10px**
- Font: 13px / weight 500
- Hover: `background: var(--panel)`, `border-color: rgba(255,255,255,0.18)`
- No `backdrop-filter` on buttons.

### Segmented toggle (Tracks / Time)

- Container: background `var(--panel)`, border-radius 999px, padding 4px, display flex
- Each option: padding `6px 14px`, border-radius 999px, font 11px / weight 600, border none, cursor pointer
- **Active option:** background `var(--accent)`, color `var(--accent-text)`
- **Inactive option:** background transparent, color `var(--text-mute)`
- Hover on inactive: color `var(--text)`

The toggle stays pill-shaped (it's a different component class than action buttons) — this is intentional contrast.

---

## Visual behavior & states

- **Nav items:** active state is mutually exclusive — only one item carries the `panel-strong` background at a time.
- **Track rows:** subtle hover — `background: rgba(255,255,255,0.04)` on hover.
- **Heart icon:** clickable; clicking toggles liked state and animates a quick scale pulse (1 → 1.2 → 1 over 200ms).
- **Donut segments:** on hover, slightly brighten the segment (`filter: brightness(1.15)`) and emphasize the matching legend row.
- **Loading states:** every panel that fetches data should show a skeleton — same panel dimensions, with `background: var(--panel-inset)` shimmer blocks at 60% opacity. No spinners.

## Responsive notes

- At viewport `< 1100px`: collapse the content grid below stats to a single column.
- At `< 900px`: collapse stat row to 2×2.
- At `< 720px`: collapse sidebar to a top bar with horizontal-scrolling nav chips; main content padding becomes `24px 20px`.

## Do not touch / do not add

- Do NOT add a sidebar account/profile card.
- Do NOT add an icon, dot, or symbol next to the "Spotistats" wordmark.
- Do NOT use pill-shaped buttons for primary actions — only the segmented toggle uses pill shape.
- Do NOT introduce Spotify green (`#1db954`) anywhere. The amber accent is the only accent.
- Do NOT add gradients to text, buttons, or panels (the page-background radial gradient is the only gradient).