# Frontend decisions

Standing decisions that are not already a rule in `CLAUDE.md` ("Things that are easy to break" holds the behavioural
ones: error boundary, like and reply counts, updates providers, tabs, profile edit). The chronological build journal
(33 entries, feed step 3 to profile step 38) is in git history: `git log --follow -- frontend/DECISIONS.md`.

- **One `ApiError`, two senders.** `src/api/core/http.ts` holds `ApiError`, `send` and `sendAuthenticated`. `send`
  serves the auth module, where a `401` means wrong credentials or no session. `sendAuthenticated` wraps it, calls the
  handler `AuthProvider` registers on a `401`, then rethrows. This replaces the older rule of one `<Resource>ApiError`
  per module.
- **Shared list types live in `src/api/tweets/tweetPage.ts`.** `feed.ts` and `savedTweets.ts` return the same
  `TweetPage`, so item types, the page request, the query builder and the picture-URL mapping sit there. Picture paths
  (`/api/v1/files/{id}`) get the API origin at the API boundary (`toPictureUrl`), so components only see a usable URL
  or `null`.
- **Alpha tokens are named for their job** in `src/index.css`, not for their value.
- **Shared code is grouped, single-use code sits next to its consumer.** A helper or hook with two or more consumers
  lives in `src/utils/<group>` (`navigation`, `text`, `validation`, `posts`, `browser`) or `src/hooks/<group>`
  (`routing`, `lists`, `dom`, `social`); one with a single consumer sits beside it. API modules are in `src/api/<group>`
  (`core`, `tweets`, `accounts`).
- **Tests live outside `src`** in `tests/`, mirroring the `src` tree (`tests/api/tweets/feed.test.ts` tests
  `src/api/tweets/feed.ts`). Shared test helpers are in `tests/support`. Decided 2026-10-17.
- **Edits and deletes of replies are overrides on the loaded list,** so `usePagedList` stays unchanged.
- **The views reporter is a module-level singleton** (`src/components/shared/PostList/viewReporter.ts`): best-effort, so a failed
  batch is lost rather than retried.
