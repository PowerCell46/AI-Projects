# Replies — design

**Status:** approach approved 2026-10-04. Not broken into steps yet. Next: settle the open questions at the bottom,
then a gated plan with **[timeline]**, **[gateway]**, **[frontend]** and **[e2e]** steps. Build after likes
(`likes-design.md`): the details page shows likes too.

## Decision

Replies are **comments, not tweets**: a separate entity that belongs to one tweet and can't exist without it, like
comments on a Facebook post. They live in **`twitter_tweet_service`**, in their own `replies` collection, deleted with
their tweet in the same Mongo transaction.

- **Flat:** a reply answers the tweet, never another reply.
- **Text only**, same checks and limit as tweet text (trimmed, empty refused, 280 code points).
- **Editable** by its author only; marked `edited`, shown in the frontend.
- **Deletable** by its author or by the tweet's author.
- **No likes, saves or views** on replies.
- **Listed oldest first**, in pages.
- **The tweet carries `replyCount`**, shown on posts in the feed, saved, liked and details views.

## Rejected alternatives

- **Replies as tweets** (`inReplyToId` on `Tweet`): replies would get images, likes, saves, views and replies of their
  own. Not wanted: replies stay simple and fully dependent on the tweet.
- **Replies embedded in the tweet document:** an unbounded array. Every tweet read (the feed's batch read included)
  would carry all of them; Mongo caps a document at 16 MB.
- **Reply authors fetched by the browser:** needs a new public batch user endpoint and a second round trip per page.
- **Author name copied into the reply:** goes stale on rename or a new picture.

## Data model (tweet service Mongo)

**`replies`** collection, `Reply extends CommonDocument` (`id`, `createdAt`, `updatedAt` from the `Clock`, millisecond
precision):

| Field | Type | Notes |
|---|---|---|
| `tweetId` | UUID | The tweet it belongs to. |
| `authorId` | UUID | The caller's `X-User-Id` at creation. |
| `content` | string | Trimmed text. |
| `edited` | boolean | `false` on create, `true` after any edit. Named `edited`, not `isEdited`: Lombok's getter for `isEdited` is `isEdited()`, so Jackson would write `edited` and the Java and JSON names would drift. |

Index `{tweetId: 1, createdAt: 1, _id: 1}`: serves the oldest-first pages and the delete-all when the tweet goes.

**`Tweet` gains `replyCount`** (`long`, `0` on create). Changed only by `$inc`, never by a full save, and the `$inc`
leaves the tweet's `updatedAt` alone (otherwise a reply would make the tweet look edited).

**Invariant:** `replyCount` = number of replies of the tweet. It moves only in the transaction that adds or removes a
reply.

## Endpoints (tweet service)

All under `/api/v1/tweets/{tweetId}/replies`. The gateway already forwards `/api/v1/tweets/**` to the tweet service with
the caller's identity, so **no gateway route change**.

| Call | Who | Answer |
|---|---|---|
| `POST` `{content}` | anyone | `201` + the reply. `404` tweet missing, `400` empty or too long |
| `GET ?cursor=&size=` | anyone | `200` `{items, nextCursor}`, oldest first. `404` tweet missing, `400` bad cursor or size |
| `PUT /{replyId}` `{content}` | the reply's author | `200` + the reply, `edited = true`. `403` anyone else, `404` missing |
| `DELETE /{replyId}` | the reply's author or the tweet's author | `204`. `403` anyone else, `404` missing or already deleted |

A `replyId` that doesn't belong to `{tweetId}` answers `404`. `PUT` matches the tweet's own edit verb.

**`ReplyResponseDTO`:** `id`, `tweetId`, `content`, `edited`, `createdAt`, `updatedAt`, `author` `{id, username,
profilePictureUrl}`. The author has the same shape as the timeline service's `AuthorResponseDTO`.

## Write paths

**Create.**
1. Look up the caller's user **before** the transaction (see "Reply authors"). A gateway failure then leaves nothing
   written, so a retry can't create a duplicate reply.
2. One transaction: `$inc replyCount +1` on the tweet first. 0 matched → `404`, and the transaction aborts, so a reply
   is never saved for a deleted tweet. Then insert the reply.

**Edit.** Look up the caller first (same reason), then a conditional update matching `{_id, tweetId, authorId}`, as
the tweet's `updateContentIfAuthor`: 0 matched → `404` if the reply is gone, `403` if it belongs to someone else. Sets
`content`, `edited = true`, `updatedAt`.

**Delete.** Load the reply (`404` if missing) and the tweet's `authorId`. The caller must be the reply's author or the
tweet's author, else `403`. One transaction: delete the reply; only if that removed it → `$inc replyCount -1`. Two
deletes racing: one removes the reply and decrements, the other removes nothing and answers `404`.

**Tweet delete.** The existing transaction also runs `deleteMany({tweetId})` on `replies`, next to the tweet delete and
the `tweet.deleted` outbox message.

**Write conflicts.** Every reply create or delete updates its tweet document, so two at once on one tweet conflict
and one transaction aborts. Retry it as the tweet delete already does (`isWriteConflict`, `MAX_DELETE_ATTEMPTS` in
`TweetServiceImpl`).

## Read path

`GET` replies: check the tweet exists (`404` lets the page tell "deleted" from "no replies"), then a keyset page on
`(createdAt ASC, _id ASC)`. Page size and cursor as the timeline service: `PageSizeValidator` (1–100, default 20) and a
cursor codec ported from `TimelineCursorCodec`.

## Reply authors

The tweet service only stores `authorId`; usernames and pictures live in the gateway. It looks them up with **one
batch call per page** to the gateway's `/internal/v1/users?ids=`, using a copy of the timeline service's
`UserLookupService` (same `X-Internal-Secret`, same `app.gateway-internal.url` / `INTERNAL_API_SECRET` env, same
timeouts). Create and edit look up just the caller.

- Gateway down → `502`, slow → `504`, as the timeline service's reads.
- A reply whose author the lookup doesn't return is left out of the page, as the timeline service's lists do.
- The tweet service stops "knowing nothing about users": update its `CLAUDE.md` "What this is".

## `replyCount` on posts

`TweetResponseDTO` gains `replyCount`. It's shared by the public `GET /api/v1/tweets/{id}` and the internal batch read,
so the timeline service gets it with no new call: `TweetClientDTO` → `TweetItemResponseDTO.replyCount` → feed, saved,
liked and details.

## Tweet details read (timeline service)

The details page needs the post exactly as the feed shows it (author, text, images, likes, `likedByMe`, `savedByMe`,
views, `replyCount`). Only the timeline service assembles that, so it gets one more endpoint:

- **`GET /api/v1/tweet-details/{tweetId}`** → `200` with one `TweetItemResponseDTO`, the same shape as a feed item;
  `404` when the tweet or its author is gone. Not under `/api/v1/tweets/**`, which the gateway sends to the tweet
  service.
- **Reuses `TweetItemAssemblyService`.** The author id is known only after the tweet is read, so the tweet and author
  calls run one after the other, not in parallel. The plan decides how to share the code without reading the tweet
  twice.
- **[gateway]** `TimelineRoutesConfiguration` gains `TWEET_DETAILS_PATH = "/api/v1/tweet-details/*"` on the
  `timeline-service` route.
- **Replies come separately.** The browser asks for the details and the first page of replies at the same time;
  "load more" uses the replies endpoint.

Rejected: the browser assembling the post from about four calls and new public endpoints; the tweet service's own
`GET` asking the timeline service for likes, saves and views, which would make the two services call each other.

## Kafka

None. Nothing consumes replies yet; `tweet.deleted` already covers the tweet going away.

## Frontend (details settled in its own plan)

- `TweetItem` gains `replyCount`; post cells show it.
- Clicking a post opens `/tweets/:tweetId`: the post from the details read and the replies list, loaded in parallel.
- A reply composer (280 counter); "edited" on edited replies; edit on your own replies; delete on your own replies
  and on any reply under your own tweet.

## Tests

Per each project's standing rules (`java-junit`, `TESTING.md` in the same change, `mvn verify` 3×, deterministic).

- **Repository:** keyset pages with ties on `createdAt`; `$inc` leaves `updatedAt` alone; delete-all by tweet.
- **Service:** create on a missing tweet saves nothing; edit by a non-author → `403`; delete by reply author and by
  tweet author; delete by anyone else → `403`; the count moves only on a real add or remove; the caller lookup runs
  before the write.
- **Concurrency:** N replies at once on one tweet → `replyCount = N`; two deletes of one reply → decrement once; reply
  racing a tweet delete → no orphan.
- **Controller (`TESTING.md`):** every row of the endpoint table.
- **Contract:** the user lookup against the real gateway endpoint, as the timeline service's.
- **Timeline:** the details read (`200`, `404`, the same fields as a feed item); `replyCount` passed through.
- **Gateway:** the details route forwards with the caller's identity.
- **E2E API spec (`e2e/`):** reply → listed with the author's name, `replyCount = 1` in the feed; edit → `edited`;
  the tweet's author deletes it → gone, count `0`; tweet deleted → replies `404`.

## Accepted gaps — revisit when the named trigger lands

- **No `reply.created` event.** **Trigger:** notifications ("X replied to your post") → event through the tweet
  service's outbox.
- **Large tweet delete:** a tweet with very many replies makes one large transaction. **Trigger:** slow or failing
  deletes → delete the tweet, sweep its replies afterwards.
- **Hot tweet document:** replies on a viral tweet conflict on `replyCount` and retry. **Trigger:** retries running
  out → a counter outside the tweet document.
- **One gateway call per replies page,** no cache. **Trigger:** details p95, or gateway load → cache authors.
- **No rate limiting on replies.** **Trigger:** abuse, or a second instance.
- **`X-User-Id` trusted blindly** by the tweet service: anyone reaching its port can reply as anyone (existing gap).
  The tweet service now also holds `INTERNAL_API_SECRET`. Same trigger as the existing gap.
- **`replyCount` can exceed the visible replies** if an author disappears (account deletion is out of scope).
  **Trigger:** account deletion → remove or anonymise their replies.

## Calls made without a question

Caller lookup before every write; a second delete answers `404` (as tweets); a replies list for a missing tweet answers
`404`; `PUT` for edit; the path `/api/v1/tweet-details/{tweetId}`; replies of missing authors hidden.

## Open questions

1. Where the steps go: a new phase in `twitter_tweet_service/PLAN.md` (reply work lives there, the timeline, gateway
   and frontend steps marked), or one plan with likes?
2. Saving the same text with no change: still marks `edited`, or a no-op?
