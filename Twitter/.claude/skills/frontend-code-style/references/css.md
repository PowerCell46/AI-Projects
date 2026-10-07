# CSS

The goal is a design that scales: text, spacing and containers grow and shrink together when a media query changes
one value.

## Files

- Each component styles itself in its co-located `<Name>.css`, imported by `<Name>.tsx`.
- `src/index.css` is the only global stylesheet: tokens, reset, the root font size and its bands, the breakpoint
  list, and utilities such as `.sr-only`.
- Order within a component's file: base rules → state rules (`[data-*]`, `:hover`, `:disabled`) → media queries →
  the `prefers-reduced-motion` block last.

## Formatting

- Exactly one blank line between rules. No blank lines inside a rule.
- A rule with several selectors puts each selector on its own line.
- Properties go in this order: positioning (`position`, `inset`, `z-index`) → layout (`display`, `flex`/`grid`
  properties, `gap`) → box (`width`, `height`, `margin`, `padding`, `overflow`) → typography (`font-*`,
  `line-height`, `letter-spacing`, `text-*`, `white-space`, `overflow-wrap`) → visuals (`color`, `background`,
  `border`, `border-radius`, `box-shadow`, `opacity`) → motion (`transform`, `transition`, `animation`).

```css
/* flag */
.reply-cell-name, .reply-cell-time {
    color: var(--display-name);
    font-size: 0.8125rem;

    min-width: 0;
    display: flex;
}

/* prefer */
.reply-cell-name,
.reply-cell-time {
    display: flex;
    min-width: 0;
    font-size: 0.8125rem;
    color: var(--display-name);
}
```

## Colors

No raw colors outside `src/index.css`: no hex, no `rgb()`/`rgba()`, no named colors like `white`. Components use
`var(--token)` only. A new color becomes a semantic token in `index.css` first.

## Units

px is allowed **only** for things that must never scale: borders, `outline` and `outline-offset`, thin lines (1–2px
drawn with `width`/`height`, including the 1px `.sr-only` box), and media-query breakpoints. Everything else uses
relative units.

| Case | Unit |
|---|---|
| Font sizes, spacing (margin, padding, gap), border radius, small motion offsets (`translate`) | `rem` (`em` when it should follow the element's own font size) |
| `line-height` | unitless (`1.5`), so it follows the element's font size |
| `letter-spacing` | `em` |
| Borders, `outline`, `outline-offset`, thin lines | `px` |
| Media-query breakpoints | `px` (they describe the viewport; `rem`/`em` in a media query ignore the root font size anyway) |
| Widths relative to the parent | `%` |
| A readable maximum width | `rem` (`max-width: 30rem`) |
| Full-viewport layers (overlays, curtains) | `vw`, `dvh` (not `vh`: on phones it includes the area behind the address bar) |
| Root font size | `%` on `html` (see **Scaling**); `body` is `1rem` |
| Durations | `ms` |

```css
/* flag */
.pager-head {
    width: 10px;
    border-radius: 4px;
    transform: rotate(45deg) translate(4px, 4px);
}

/* prefer */
.pager-head {
    width: 0.625rem;
    border-right: 1px solid var(--line-btn);
    border-radius: 0.25rem;
    transform: rotate(45deg) translate(0.25rem, 0.25rem);
}
```

## Scaling

Sizes scale in **one place**: the root font size in `src/index.css`.

- `html` sets `font-size: 100%` and raises or lowers it in a few viewport bands. Band values are always `%`, so the
  browser's font-size setting still applies.
- Components write plain `rem`, so every size follows the root. No `vw` or `clamp()` sizes in components (only
  full-viewport layers use `vw`/`dvh`), no scale variable of their own, no `calc(var(--<page>-scale) * ...)`.
- Component media queries change **layout**: columns, direction, order, visibility, wrapping. Spacing may change only
  in the same query that changes the layout (a row that stacks into a column). Font sizes never change in a component
  media query; if everything is too big at some width, change the root band.
- Breakpoints are a short fixed list in a comment at the top of `src/index.css` (CSS variables can't be used in media
  queries). Media queries use only those values, all in one direction: `max-width` or `min-width`, never mixed.
- Component media queries go at the bottom of the component's own `.css`.
- A box that holds text uses `min-height`, never `height`, so a bigger root can't clip it.
- No font size below `0.75rem` (12px at the default root).

```css
/* src/index.css — prefer */
/* Breakpoints (max-width): 600px, 1024px. */
html {
    font-size: 100%;
}

@media (max-width: 600px) {
    html {
        font-size: 93.75%;
    }
}

/* flag — a component scaling itself */
.feed-title {
    font-size: clamp(1.5625rem, 4.8vw, 2.625rem);
}

@media (max-width: 600px) {
    .post-cell-body {
        padding: 1.125rem 1.125rem 0;
        font-size: 1.0625rem;
    }
}

/* prefer — plain rem; the media query only changes layout, plus the spacing that layout needs */
.feed-title {
    font-size: 2.25rem;
}

@media (max-width: 600px) {
    .person-card {
        grid-template-columns: 2.5rem 1fr;
        column-gap: 0.875rem;
    }

    .person-card-action {
        grid-column: 2;
    }
}
```

## Class naming

- Classes are `<component-in-kebab>-<part>`, prefixed with the component's own name: `.topic-card`,
  `.topic-card-title`, `.pager-stem`.
- Visual state goes on `data-*` or `aria-*` attributes, selected in CSS. No BEM `--modifiers`, no state classes, no
  class names built with template strings.

```css
/* flag */
.topic-card--subscribed { ... }
.topic-card.is-subscribed { ... }

/* prefer */
.topic-card[data-subscribed='true'] { ... }
.feed-filter-cell[aria-selected='true'] { ... }
```

## Scope

- A component's `.css` styles only its own classes. It never reaches into a child's (`.feed .topic-card-title`).
- No `!important`, except a `prefers-reduced-motion` reset in the global stylesheet.
- No inline `style={{}}`, except for values computed at runtime (a progress width, a measured height).
- No ID selectors.

## Overflow (guideline)

A container shouldn't let its content spill out. Techniques that usually get there:

- Flex and grid children that hold text get `min-width: 0`, so a long word can't push the column wider.
- User or API text (emails, names, descriptions, URLs) gets `overflow-wrap: anywhere`.
- A label that must stay on one line uses `overflow: hidden`, `text-overflow: ellipsis` and `white-space: nowrap`,
  with the full text in a `title` attribute.
- `img`, `svg` and `video` get `max-width: 100%`.
- Content that's legitimately wider than its container (tables, code) scrolls inside its own `overflow-x: auto`
  wrapper. The page itself doesn't scroll sideways.
- Don't put `overflow: hidden` on a layout container just to hide a scrollbar. Find and fix what's too wide.
- Check new or changed layouts at 320px, at each breakpoint and 1px on either side of it, and at 1920px.
