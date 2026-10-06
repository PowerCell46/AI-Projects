# Likes — design

**Status:** approach approved 2026-10-04; broken into steps in `twitter_timeline_service/PLAN.md` (phase 4 backend,
phase 5 UI). Open questions 1–4 are answered there (Q20, Q22, Q21, Q19).

## Decision

Likes live in **`twitter_timeline_service`**, built as a copy of saved tweets plus a public counter (a copy of the
views counter). Writes are synchronous HTTP; no Kafka. This reverses the timeline plan's Q1 ("Liked tweets later,
elsewhere").

The timeline service already owns "a user's relationship to tweets": a like is a saved tweet with a public count, and
every piece exists there (`saved_tweets`, `tweet_view_counts`, cleanup on `tweet.deleted`, item assembly).

## Rejected alternatives

**Liker ids embedded in the tweet document, plus `tweet.liked` / `tweet.unliked` projected into the timeline service.**

- **Unbounded array.** Every tweet read carries it (the feed's batch read included) unless projected out each time;
  every like rewrites the same document, so a popular tweet becomes a write hotspot; Mongo caps a document at 16 MB.
- **It can't answer the main question.** The array has ids but no times, so "liked tweets, newest like first" needs the
  projection anyway. The array would then only give the count and `likedByMe`, which the projection gives too.
- **Toggles break async projection.** The feed works because `created` and `deleted` happen once each. Likes flip.
  The outbox publisher skips a failed send and retries it later
  (`twitter_tweet_service/.../OutboxPublisherServiceImpl.java`, `continue` in `publishPending`), so
  like (send fails) → unlike (sent) → like (retried) arrives as unlike, like: a phantom like. A fix needs a version per
  (user, tweet), which breaks the timeline's "insert or delete, never edit" rule. A `FAILED` outbox row leaves the two
  copies out of sync for good.
- **The liked list lags clicks** by the outbox poll interval. The optimistic heart hides it; the list doesn't.
- **Two sources of truth** for one fact.

**A separate likes service.** Feed and saved pages would need a third downstream call per page for counts and
`likedByMe`, and one more container, DB and set of DLTs for one table and one counter.

## Data model (timeline Postgres)

**`tweet_likes`**, mirrors `saved_tweets`. Entity `TweetLike`, `@IdClass(TweetLikeId.class)`; the user attribute is
`ownerId` on column `user_id`, as in `SavedTweet`. No `CommonEntity` (timeline Q18).

| Column | Type | Notes |
|---|---|---|
| `user_id` | `uuid` | PK part. Who liked. |
| `tweet_id` | `uuid` | PK part. |
| `author_id` | `uuid` | From the tweet lookup at like time, so a list page fetches tweets and authors in parallel (as saved). |
| `liked_at` | `timestamptz` | From the `Clock`, truncated to microseconds; the page cursor is built from it. |

Indexes: `ix_tweet_likes_tweet (tweet_id)` for the delete cleanup; `ix_tweet_likes_user_liked (user_id, liked_at,
tweet_id)` for paging.

**`tweet_like_counts`**, mirrors `tweet_view_counts`: `tweet_id uuid` PK, `likes bigint`, check `likes >= 0`,
`updatable = false` so only the native statements change it.

**Invariant:** `likes` = number of `tweet_likes` rows for the tweet. The counter moves only in the same transaction as
a row that was actually added or removed.

## Write path

**`PUT /api/v1/likes/{tweetId}`** → `204`, idempotent (timeline Q10 rules, as save).

1. Tweet lookup (the tweet service's internal batch read) **before** the transaction, so no connection is held while
   waiting. Missing → `404`. Takes `authorId` from it.
2. One transaction: `INSERT ... ON CONFLICT DO NOTHING`. If it added a row → upsert the counter
   `+1` (`INSERT ... ON CONFLICT (tweet_id) DO UPDATE SET likes = tweet_like_counts.likes + 1`).
3. Liking again changes nothing and keeps the original `liked_at`.

**`DELETE /api/v1/likes/{tweetId}`** → `204`, idempotent, no tweet lookup (as unsave).

1. One transaction: delete the row. If it removed one → `UPDATE ... SET likes = likes - 1`.

**Concurrency.** Two parallel likes by one user: the PK lets one insert win, the other adds 0 rows, so the count moves
once. A like racing an unlike: the delete doesn't see the uncommitted insert, removes 0 rows and doesn't decrement;
the invariant holds either way. The frontend already sends one request at a time per button (last click wins).

## Read path

- **Inline on every item.** `TweetItemResponseDTO` gains `likes` (`long`) and `likedByMe` (`boolean`).
  `TweetItemAssemblyServiceImpl` adds two local queries per page, next to views and `savedByMe`: counts by id (as
  `ViewServiceImpl.countViews`, `0` when no row) and `findLikedTweetIds(viewerId, tweetIds)` (as `findSavedTweetIds`).
  No new HTTP call. Feed and saved lists get both fields for free.
- **`GET /api/v1/likes?cursor=&size=`.** The caller's liked tweets, newest like first, keyset paged on
  `(liked_at DESC, tweet_id DESC)` with `TimelineCursorCodec` and `PageSizeValidator`. Response
  `LikedTweetsResponseDTO { items, nextCursor }`, like `SavedTweetsResponseDTO`. Items whose tweet or author is gone
  are filtered out by assembly, as on the other lists.

## Tweet deleted

`FeedEntryCleanupServiceImpl.onTweetDeleted` also deletes the tweet's `tweet_likes` rows and its `tweet_like_counts`
row, in the transaction it already opens. The log line gains the number of likes removed.

## Gateway

- Delete the stub: `controllers/likes/LikeController` and its tests (gateway `DECISIONS.md`, "Frontend plan step 1").
- `TimelineRoutesConfiguration`: add `LIKES_PATH = "/api/v1/likes/**"` to the `timeline-service` route predicate, so
  the same identity filters and timeouts apply. Update the comment on `app.timeline-service.url`.
- Route tests as for saved tweets.

## Frontend

- `TweetItem` (`src/api/tweetPage.ts`) gains `likes: number` and `likedByMe: boolean`.
- `PostActions` takes `isLikedInitially` and the server count. Shown count = server count − (liked initially ? 1 : 0)
  \+ (on now ? 1 : 0). Drop the stub comment and `likeCount = like.isOn ? 1 : 0`.
- `src/api/likes.ts` gains the list read; `ENDPOINTS` gains the list URL.
- `LIKED TWEETS` menu item and a `/liked` page: `PostList` with the likes `fetchPage` (frontend plan "Left open" trigger).

## Tests

Per each project's standing rules (`java-junit`, `TESTING.md` in the same change, `mvn verify` 3×, deterministic).

- **Repository:** insert is idempotent and keeps `liked_at`; keyset paging tie-break on equal timestamps; delete by
  tweet; increment / decrement.
- **Service:** the counter moves only on a real row change; `404` for a missing tweet; unlike makes no lookup.
- **Concurrency:** N users like at once → `likes = N`; one user likes N times at once → `1`; a like/unlike storm →
  `likes` = row count.
- **Controller (`TESTING.md`):** `PUT` / `DELETE` / `GET` scenarios, bad cursor and size, bad UUID.
- **Listener (`TESTING.md`):** `tweet.deleted` removes the likes and the counter.
- **Gateway:** the likes route forwards with the caller's identity; the stub is gone.
- **E2E API spec (`e2e/`):** like → `likes = 1`, `likedByMe` in the feed; listed under `/likes`; unlike → `0`; tweet
  deleted → gone from the list.
- **Frontend:** count math in `PostActions`; Playwright: a like survives a reload, `/liked` lists it.

## Accepted gaps — revisit when the named trigger lands

- **No `tweet.liked` event.** **Trigger:** a consumer, e.g. a "X liked your post" notification → an outbox in this
  service (copy of the tweet service's) and the event.
- **No "who liked this" list.** **Trigger:** a likers page → index `(tweet_id, liked_at, user_id)` and an endpoint.
- **Hot counter row** on a viral tweet (as views). **Trigger:** like latency → buffered or sharded counters.
- **Orphans:** a like racing a tweet delete leaves a row and a counter for a dead tweet; hidden in lists, never
  removed. Same trigger as saved orphans → a sweep job checking ids against the tweet service.
- **No rate limiting on likes** (each one a tweet-service call and a log line). **Trigger:** abuse, or a second
  instance.
- **`X-User-Id` is trusted blindly** on port 8083, so anyone who reaches it can mint likes. Same trigger as the existing
  timeline gap.

## Open questions (`/grill-me`) — answered

Answered in `twitter_timeline_service/PLAN.md`: 1 → Q20, 2 → Q22, 3 → Q21, 4 → Q19.

1. Liked list: only your own (`GET /api/v1/likes`), or anyone's from their profile (`/api/v1/users/{id}/likes`)?
   X made likes private in 2024.
2. An unliked post on `/liked`: stays until reload (as `/saved`, frontend Q5) or disappears?
3. Liking your own tweet: allowed (as on X), counted?
4. Where the steps go: phase 4 of the timeline plan (as the gateway and tweet steps were), or a plan of its own?
