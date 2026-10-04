# Implementation Brief — Tab Row + People Tab

Scope: add a two-tab navigation row beneath the existing header, and build the People tab behind it. **The feed itself is already implemented — do not modify the post cell, the compose modal, the header, or the avatar dropdown.** The only change to existing code is inserting the tab row and wrapping the current feed in a panel.

---

## 1. Rationale

The app had no way to find or follow anyone. Adding discovery raises one structural question: where does the switch live?

A segmented control inside the header was rejected — the header already carries a brand, a post button and an avatar, and a fourth element makes it cramped below 600px. A left rail was rejected because the whole layout is built around a centred 660px column and a rail would restructure it for two destinations.

A **second row beneath the header** costs ~46px of permanent vertical space and buys unambiguous labelling, room to breathe at small widths, and a place to add a third destination later without redesigning anything.

**People is denser than the feed on purpose.** The feed is tuned for reading one post at a time — wide measure, large type, 22px gaps. Discovery is a scanning task: you're comparing candidates, not reading them. Tighter cells and 12px gaps make the two surfaces feel like different kinds of place even though they share a construction. Resist the urge to unify the spacing.

**Why a 50-character bio.** Enough signal to decide whether to follow, short enough that every card stays the same height and the list stays scannable. A full bio would make cards variable-height and turn the screen into a second feed.

---

## 2. Tokens Used

All of these already exist in the app. Nothing new is introduced.

```
--bg      #050E22   page
--cell    #07142C   card surface
--glow    #4E8BFF   accent — active tab, tab indicator, follow button
--pale    #E2E9F7   text base
--e       cubic-bezier(.16,.84,.26,1)

rgba(226,233,247,.16)  card border
rgba(226,233,247,.10)  tab row bottom border
rgba(226,233,247,.62)  bio text
rgba(226,233,247,.42)  inactive tab label
rgba(226,233,247,.40)  handle, follower count
rgba(226,233,247,.24)  avatar border
rgba(226,233,247,.80)  inactive tab label on hover
rgba(78,139,255,.55)   follow button border (resting)
```

Typefaces are the two already in use — IBM Plex Mono and Inter Tight. **Do not add a third.**

---

## 3. Tab Row

Sits directly beneath the header and **inside the same sticky container**, so header and tabs scroll away and stick as one block. Bottom border `1px` in `rgba(226,233,247,.1)`.

Inner width matches the feed: `max-width: 660px`, `padding: 0 24px`, centred. The first tab's left padding is zeroed so its label aligns with the left edge of the cards below.

| Property | Value |
|---|---|
| Row height | `46px` |
| Tab label | IBM Plex Mono, `10px`, `.17em` tracking, uppercase |
| Tab padding | `0 20px` (`0 14px`, `9.5px`, `.12em` at ≤600px) |
| Inactive | `rgba(226,233,247,.42)` |
| Hover | `rgba(226,233,247,.80)`, `300ms` |
| Active | `--glow` |

Tabs: **TWEETS**, **PEOPLE**. TWEETS is the default.

### Indicator
A `2px` `--glow` bar absolutely positioned at `bottom: -1px` of the row, so it overlaps the bottom border rather than sitting below it.

It animates **both `transform: translateX()` and `width`** over `520ms` on `--e`, measured from the active tab's `offsetLeft` and `offsetWidth`. Because the two labels differ in width, the bar visibly shrinks and grows as it travels — that is the intended behaviour, not a side effect. Do not substitute a fixed-width indicator or a background pill.

Recompute the indicator on window resize and after webfonts load, since label widths shift when IBM Plex Mono replaces the fallback.

### Panels
Each tab's content is a panel; only the active one is in the document flow. Switching plays a short entrance on the incoming panel: `opacity 0 → 1` with `translateY(8px) → 0` over `400ms` on `--e`. No exit animation, no cross-fade, no horizontal slide.

**Scroll position is remembered per tab.** Store the scroller's `scrollTop` when leaving a tab and restore it when returning. Jumping back to the top of a feed you were reading is the most annoying thing a tab switch can do.

Accessibility: `role="tablist"` on the row, `role="tab"` + `aria-selected` on each button, `role="tabpanel"` + `aria-labelledby` on each panel. Left/Right arrow keys move between tabs. Reflect the active tab in the URL so it survives a reload and works with browser back.

---

## 4. Person Card

Same construction language as a post cell: `--cell` surface, `1px` border in `rgba(226,233,247,.16)`, zero radius, no shadow, **no hover state on the card itself.**

```
Layout            grid, 44px | 1fr | auto
Column gap        16px
Align             start
Padding           18px 22px
Margin-bottom     12px
```

| Element | Spec |
|---|---|
| Avatar | `44px` square, `1px` border `rgba(226,233,247,.24)`, tinted fill, mono initials `12px` |
| Name | Inter Tight 500, `14.5px`, `-.01em`, `#fff` |
| Handle | Mono, `9.5px`, `.1em`, `rgba(226,233,247,.4)` — baseline-aligned with the name, `9px` gap, wraps to its own line if needed |
| Bio | Inter Tight 400, `14px`, line-height `1.45`, `rgba(226,233,247,.62)`, `7px` above |
| Follower count | Mono, `9.5px`, `.14em`, `rgba(226,233,247,.4)`, `tabular-nums`, `10px` above |

**Bio truncation** — clip to 50 characters, then trim back to the last whole word and append an ellipsis. Never cut mid-word. Truncate server-side if the API can, so the client isn't shipping full bios it discards.

**Follower count format** — `12.4K` below 10,000 (one decimal, trailing `.0` stripped), `34K` at and above. Raw number under 1,000. Label reads `FOLLOWERS`, uppercase, after the number.

### Follow button
Reuses the post button's mechanism exactly — the two should feel like the same control.

- Resting: ghost. `1px` border `rgba(78,139,255,.55)`, `--glow` text, `32px` tall, `0 16px` padding, `min-width: 104px` so the label swap doesn't resize the card.
- Label: Mono, `9.5px`, `.16em`.
- Hover: a solid `--glow` panel scales up from the bottom (`transform-origin: bottom`, `scaleY(0) → scaleY(1)`, `500ms` on `--e`) and the text inverts to `--bg`.
- Followed: the panel stays at `scaleY(1)`, text stays `--bg`, label becomes `FOLLOWING`.

The upward fill direction is shared with the post button and the like/save icons. Keep it consistent.

`aria-pressed` reflects state. Optimistic toggle — flip immediately, reconcile with the server after, revert and surface an error if the call fails.

**Unfollow confirmation:** a followed button that reverts on a single click is easy to hit by accident. Either swap the label to `UNFOLLOW` on hover when followed, or require a confirm. Pick one; don't ship a silent one-click unfollow.

---

## 5. Pagination

Identical mechanism to the feed, operating on whichever panel is active: fire when within `500px` of the bottom, guarded by a busy flag, append rather than replace.

People loads **8 initially, 6 per page** — denser than the feed's 6/5 because the cards are shorter.

The loading indicator is the existing one — a `7px` `--glow` square blinking on a 1s `steps(2,end)` cycle beside mono `FETCHING MORE`. It is shared between panels; keep one instance rather than duplicating it per tab.

Production requirements the prototype does not cover: cursor-based pagination, an end-of-suggestions state, and an error state with retry. Throttle the scroll listener or replace it with an `IntersectionObserver` on a sentinel.

---

## 6. Responsive (≤600px)

- Tab labels tighten to `9.5px` / `.12em`, padding `0 14px`
- Person card grid collapses to `40px | 1fr`; avatar `40px`; padding `16px 18px`; gap `14px`
- The follow button moves to its own line: `grid-column: 2`, `margin-top: 12px`, `justify-self: start` — it does not squeeze the bio
- Everything else inherits from the existing responsive rules

---

## 7. Open Decisions

These affect behaviour, not visuals. Resolve before shipping:

1. **What TWEETS contains.** Follow-only means a new account lands on an empty screen. All-posts means following changes nothing visible and the action feels inert. If follow-only, build an empty state that points at PEOPLE — a mono line and a button in the existing style, nothing more.
2. **What following does to the feed.** Refetch immediately, or leave it until the next natural load? Refetching while the user is mid-scroll is disruptive.
3. **Search on People.** Currently suggestions only. A search field is a separate component and is not specified here.

---

## 8. Do NOT Build

- A third tab, or a sidebar/rail
- A background pill, underline-on-hover, or fixed-width tab indicator
- Hover states on the person card
- A horizontal slide or cross-fade between panels
- Rounded corners on the avatar, card, or follow button
- Mutual-follow indicators, verified badges, "suggested because…" labels, dismiss buttons
- A person's recent post inside the card
- Full untruncated bios, or variable-height cards
- Box-shadows, gradients, glows
- Any new colour, typeface, or motion curve