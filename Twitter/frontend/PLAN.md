# Twitter Frontend — open items

Everything built so far (auth flow, feed, tab row, People, tweet details and replies, likes, profile) is described in
`CLAUDE.md`. The per-phase build journal, interview records and design briefs are in git history
(`git log --follow -- frontend/PLAN.md`; older briefs: `git show 95d594a^:./feed-design.md`,
`git show 8238824^:./feed-design-addition.md`).

Steps in other projects follow that project's `CLAUDE.md`. Invoke `frontend-code-style` before touching any
`.ts`/`.tsx`/`.css`.

## Accepted gaps — revisit when the named trigger lands

| Gap | Trigger |
|---|---|
| **Shortcut:** posts already on screen keep your old picture after a photo change (only masthead and header avatar update) | Mismatched-avatar report → author-picture override or refetch of mounted lists |
| Reply authors aren't links | Wanting to visit a replier's profile |
| Profile → tweet → Back reloads the profile from the top (as `/saved` and `/liked` do) | Lost place on long profiles → keep the profile panel mounted |
| Follower and following counts aren't clickable (API lists exist) | Wanting to see who follows whom → design a list view |
| No "remove photo" (`DELETE /me/profile-picture` exists) | A user asks to go back to initials |
| Tweet count is read on open plus your own posts; one indexed count query per open | Load concern → stored counter fed by `tweet.created` / `tweet.deleted` |
| No `NEW POSTS` check on a profile | People keep a profile open to watch it |
| No display names; no `@handle` on cards | Backend gets a display name |
| **Shortcut:** no rate limit on proxied read routes (one `author-tweets?size=100` fans out to three services; the feed is the same). Decided 2026-10-10: not planned | Abuse or load → per-user limit at the gateway |
| Back-fill is the newest 50 posts of the last 7 days | Readers ask for more → paged read |
| Follow → unfollow → follow within seconds can lose the back-fill (posts go missing, never show wrongly) | Missing back-fill report → per-pair sequence number on follow events |
| A quick return to an empty feed within ~3–5 s of a follow stays empty until the next `NEW POSTS` check | "I followed and nothing happened" → feed-changed signal from the timeline service |
| A back-fill that exhausts retries sits in `user.followed-timeline-dlt`; nothing replays it | A non-empty DLT |
| Tab state and scroll positions live in memory (reload, `/saved` or sign-out resets them) | — |
| Person cards aren't one height on phones (long bio wraps to 2–3 lines) | Visual check finds it uneven → clamp bio to one line |
| Card counts are a snapshot plus your own changes; no search on People | — |
| Alt text is generic | Tweet service stores descriptions → field in compose |
| View counts aren't shown; views are best-effort (failed batches and reports pending at tab death are lost) | — |
| `NEW POSTS` reads a full feed page every 60 s per open tab | Load concern → "count newer than" endpoint |
| An open draft is lost on session expiry and reload | — |
| `POST` stays in the header on phones | iPhone thumb-reach check fails → bottom trigger below `600px` |
| Client file checks trust the browser's MIME type (a renamed file gets the server's `415`) | — |
| Edited posts aren't marked (`updatedAt` ignored) | An edit UI |

## Left open

- **Visual checks waived** (feed step 12, tab row step 24, profile step 40): 1440 / 600 / 320 px, reduced motion on and
  off, iPhone Safari. Look at: every label raised to `0.75rem` on 2026-10-17 (header, tab row, `UNFOLLOW?` widening the follow
  button at 320 px, person cards, compose modal), the headings that lost their `clamp()` (they are plain `rem` now and
  may wrap at 320 px), the edit sheet's padding on phones, the bio's `26rem` max width, indicator travel, panel entrance, `UNFOLLOW?` / `TRY AGAIN` not resizing the card.
- **Auth screens:** visual check at 720 px and 320 px; the footer `/login` ↔ `/register` switch remounts instead of
  animating; e2e with motion on (optional).
- **Security:** the open findings are listed in `SECURITY-FINDINGS.md` (details kept privately).
- **Style leftovers:** the SVG path in `FillIcon.tsx` and a few test titles in `e2e/tests` exceed 120 columns; the
  sign-out leave animation is copied in `PeopleList.css` and `PostList.css`; `tab-row-fade` / `tab-row-leave` repeat
  `header-content-fade` / `-leave`; `TabPanels` imports the two pages; `utils/posts/bottomState.ts` imports a type from a
  component; the `merged.push(... ++ ...)` ternary in `utils/posts/tweetList.ts`.

## Out of scope

Display names; follower / following lists; reply-author links; removing the photo; the cover picture (API exists, no
UI); deleting your own tweets from the profile (API exists); a birthdate UI; search; profile search; an image viewer;
clickable links or mentions; popularity order; hiding people you already follow; refetching the people list; DLT replay
tooling.
