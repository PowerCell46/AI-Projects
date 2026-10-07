# Decisions

Non-obvious calls made *during* implementation - the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Config and infra

- **`open-in-view` is off** (step 4). Boot's default keeps a connection checked out for the whole request, and the
  feed and saved reads call the gateway and tweet service over HTTP, so a slow downstream would pin pool
  connections. Services that touch lazy data do it inside their own transaction.
- **Retention settings fail at startup when invalid** (step 10). `FEED_RETENTION`, `FEED_CLEANUP_CRON`,
  `FEED_CLEANUP_ZONE` and `FEED_CLEANUP_BATCH_SIZE` map to `app.feed.*`. A zero or negative retention, or a batch
  size below 1, makes `FeedRetentionServiceImpl` throw at construction: `0` would end the loop without deleting and a
  negative one would turn the `LIMIT` into an error at the first night run.
- **The timeline's DLT variables carry a `TIMELINE_` prefix** (frontend step 16). The mail service reads
  `USER_FOLLOWED_DLT_NAME` / `_PARTITIONS` from the same root `.env`, so setting one would have put both services'
  dead letters on one topic. The timeline reads `TIMELINE_USER_FOLLOWED_DLT_NAME` and
  `TIMELINE_USER_FOLLOWED_DLT_PARTITIONS`; the topic name stays the shared `USER_FOLLOWED_TOPIC_NAME`. Chosen by the
  user on 2026-10-04.

## Downstream calls

- **Every failed downstream call is "unavailable" except a read timeout** (step 7). Connect failures, resets, any
  error status (a `404` from a wrong secret is our bug, not the client's) and an unreadable answer become
  `UpstreamUnavailableException` (502); only a read timeout is `UpstreamTimeoutException` (504), and a connect
  timeout is "unavailable", as in the gateway. Messages are fixed and the log line carries only the status or failure
  types, never a body or exception message.
- **The secret goes to the gateway client only** (step 7). `RestClientConfiguration` builds one `RestClient` per
  downstream; only the gateway one carries `X-Internal-Secret`, and a test checks the tweet service never receives
  it. The gateway client refuses a secret under 32 bytes at startup, the gateway's own rule.
- **The follower loop hands pages to a consumer** (step 7). `forEachFollowerPage(userId, Consumer<List<UUID>>)` lets
  the fan-out insert page by page (one transaction each) without holding every follower, over returning one list
  (unbounded for a big account). An empty page is skipped; a consumer failure propagates and stops the paging.
- **The tweet and author lookups run on virtual threads** (step 9). `FeedServiceImpl` uses `supplyAsync` on a
  `newVirtualThreadPerTaskExecutor()` bean (`downstreamCallExecutor`), waits for tweets first and authors second,
  each bounded by the read timeout. If both fail, the tweet failure is the one the caller sees, and the future's
  wrapper is removed so the handler sees the lookup's own exception. Chosen over `StructuredTaskScope` (still a
  preview API on this JDK).
- **Authors are looked up by the entry's `author_id`, not the tweet's** (step 9). The user call doesn't wait for the
  tweet call, so they run side by side.
- **The follow check accepts only a `200` with `{"following": true|false}`** (frontend step 24, audit finding 1).
  The gateway answers a wrong secret, or a build without the route, with a bare `404`, so reading `404` as "not
  following" made a misconfiguration delete every back-fill silently. `FollowLookupServiceImpl` reads the body with
  `retrieve()` and `FollowCheckClientDTO`; anything else (error status, `204`, a `200` without the field or body) is
  "unavailable", retried and then dead-lettered, so the symptom is visible.

## Data model and SQL

- **The user attribute is `ownerId`, so the primary key is ordered right** (step 6). Hibernate orders a composite
  key's columns by attribute name; with `userId` the key came out `(tweet_created_at, tweet_id, user_id)` and could not
  serve `WHERE user_id = ? ORDER BY tweet_created_at DESC, tweet_id DESC`. `ownerId` (column still `user_id`) gives
  `(user_id, tweet_created_at, tweet_id)`, so the key is the page index with no second index. A repository test reads
  the key's column order from `pg_index`.
- **One insert statement for the author and the followers** (step 6). `insertIfAbsent(UUID[] userIds, ...)` is a
  single `INSERT ... SELECT unnest(...) ON CONFLICT DO NOTHING`; the author is a one-element array. It is
  `@Transactional` itself, so each call commits on its own ("one transaction per follower page").
- **Event times are cut to microseconds** (step 8). The tweet service sends milliseconds, so the cut changes nothing
  today, but it keeps the key identical on a redelivery and the cursor exact. The unfollow's `occurredAt` gets the
  same cut (the gateway sends more digits than Postgres keeps).
- **Ids are sorted by Postgres, not by Java** (step 21). Both view statements put `ORDER BY tweet_id` on the `unnest`
  select so overlapping reports lock in one order; Java's `UUID.compareTo` (signed longs) orders differently from
  Postgres's `uuid`. The counter's `views` is `updatable = false`. `incrementAll` needs distinct ids (Postgres refuses
  to update a row twice in one statement), so the service deduplicates first.
- **The cleanup loop ends on a short batch** (step 10). It repeats `deleteOlderThan` while a batch removes exactly the
  batch size, so an exact multiple costs one extra empty statement; cheaper than a count first. One cutoff per run:
  rows that age past retention mid-run wait for the next night.

## Services

- **The tweet-and-author join lives in `TweetItemAssemblyService`** (step 16). The saved list needs the same
  parallel lookup, skip-the-missing and mapping as the feed. `FeedServiceImplTest` keeps every assertion and builds
  the real assembler over mocked lookups; `SavedTweetServiceImplTest` mocks it. No separate assembler test: both
  services' tests and both controller suites cover it.
- **The saved page is `SavedTweetsResponseDTO`, not `FeedResponseDTO`** (step 16). Same JSON, own class, so the feed's
  name doesn't leak into the saved endpoint and the two can diverge. `TweetItemResponseDTO` is shared. Cursors come
  from the last row read, never the last item returned.
- **The `tweet.deleted` delete stays in `FeedEntryCleanupService`** (step 16). `onTweetDeleted` is `@Transactional`
  and deletes from `feed_entries` and `saved_tweets` together. A rename would touch the listener, the spy bean and
  three tests for no behaviour. `save` isn't transactional: it makes the HTTP call first, and with `open-in-view` off
  no connection is held meanwhile.
- **One validator and one exception for both tweet-id lists** (step 22). `TweetIdsValidator` (1 to a limit, none
  `null`, counted as sent) serves the report body (50) and the read parameter (100); a bad list is
  `InvalidTweetIdsException`, 400. The request DTO carries no bean-validation annotations, so a missing `tweetIds` and
  a bad size get the same answer.
- **The view transaction lives in `ViewRecordingService`** (step 22). `ViewServiceImpl.report` calls the tweet service
  first, then hands the existing ids to `record`, whose `@Transactional` wraps the insert-returning and the counter
  upsert. A separate bean because a self-call would skip the proxy, and no connection is held during the HTTP call.
  Reports log nothing: they arrive every few seconds per open browser.
- **Inline counts come through `ViewService.countViews`** (step 23). Asked once per page after the two downstream
  calls, with the page's distinct ids; a tweet without a counter gets `0`. It is `getViews` without the 1-100 size
  check. `views` sits second in `TweetItemResponseDTO`.
- **`savedByMe` is read in the assembly service** (frontend step 2). `assemble` takes the viewer id first and asks
  `SavedTweetRepository.findSavedTweetIds` once per page, so the saved list's items are `true` because their rows
  are in the result, not by a special case. It reads the repository itself: going through `SavedTweetService` would
  be a cycle.

## Likes (phase 4)

- **Repositories grouped into `feed`, `savedtweets`, `views` and `likes`** (step 29). The two like repositories made
  six files in `repositories`, past the five-file rule, so the package and its tests mirror the entity packages.
  Only the packages and imports moved.
- **`LikeRecordingService.like` and `unlike` return a `boolean`** (step 29): `true` when a row was added or removed.
  The counter moves on exactly that, and the INFO line of step 30 (`new like: {}`) reads it, so no second query.
- **`TweetLikeCountRepository.decrement` is an update by key, not an upsert** (step 29). A missing counter touches 0
  rows, and a counter already at 0 is refused by `ck_tweet_like_counts_likes_non_negative`, so a broken invariant
  fails loudly instead of showing a negative count.
- **Response DTOs grouped into `feed`, `savedtweets` and `likes`** (step 30). `DTOs/response` already held six files, so
  the page DTOs moved into subpackages; `ErrorResponseDTO` and the item, author and image DTOs, shared by every
  list, stay at the root.
- **`LikeService` is not transactional; `LikeRecordingService` is** (step 30). The tweet lookup comes first and holds no
  connection, the recording opens the transaction, as for views.
- **The like controller suite seeds likes through `LikeRecordingService`** (step 30), so a seeded row and its counter
  agree, as view seeding does.
- **The "likes delete fails once" test retries through `JdbcTemplate`** (step 31). Mockito cannot call the real method
  of a spied Spring Data proxy (the retry then fails every time and dead-letters), so the spy throws on the first
  call and on later calls runs `DELETE FROM tweet_likes` by hand, in the listener's transaction. The test is about
  the earlier deletes rolling back, not about the like delete itself.
- **The phase 4 audit's findings are accepted, not fixed** (step 34). The Low (forged `X-User-Id` reads a private
  liked list and mints likes) and the Info (a forged `tweet.deleted` wipes likes) extend the existing "trusted header"
  and "trusted topic" gaps and carry their triggers in `PLAN.md` and `SECURITY-AUDITS.md`. The raw report was condensed
  into `SECURITY-AUDITS.md` and its file removed.

## Back-fill (frontend step 16)

- **It is its own service.** `FeedBackfillService` (insert, check, undo) is called by `UserFollowedListener`; the
  by-author read is a `TweetLookupService` method, the follow check a new `FollowLookupService`. It is not
  `@Transactional`: each insert and the undo run in their own repository transaction, and no connection is held
  across the two HTTP calls.
- **One insert per tweet, through the existing statement.** Up to 50 `insertIfAbsent` calls, each its own
  transaction, not a new multi-tweet statement: cheap, already idempotent, harmless to re-run on a retry.
  **Trigger:** the back-fill size grows past a few hundred, or follow volume shows in the database → one statement
  over two arrays.
- **The undo bound is the clock's now.** When the follow is gone, the delete removes the follower's entries by that
  author up to now (cut to microseconds), including ones the author's own fan-out added meanwhile. It is the same
  delete `user.unfollowed` runs.

## Tests

- **One shared application context** (step 8). Every full-context test extends `AbstractDownstreamIntegrationTest`
  (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles`, two `@MockitoSpyBean`s). A second context would
  join the same Kafka consumer group and take partitions, so a record could be handled by a context whose spies the
  test never looks at. `@DataJpaTest` slices start no listeners and stay as they are.
- **"No retry" is shown with a spy and a sentinel, not a clock** (step 8). An invalid event is followed on the same
  key by a valid sentinel; once its effect is visible the spy has been called exactly twice and the dead-letter
  record is there.
- **Delete-before-create is split across two tests** (step 8). The listener test asserts the orphan entries exist
  (`should_leave_orphan_entries_when_the_delete_arrives_before_the_create`); the feed's `MissingData` scenarios
  assert they are skipped.
- **The e2e "fan-out done" signal is Ana's feed** (step 12). The fan-out writes the author's entry first, so Bob's
  entry proves nothing about the followers; Ana's entry, in the same follower page that would hold any other, is the
  signal. `feed.spec.ts` polls (`expect.poll`, 30 s) instead of sleeping.
- **The feed concurrency suite calls the services, not the listeners** (step 13). Kafka's per-key ordering would
  serialise the overlap under test. Races the plan accepts are not asserted away; what must always hold: nothing
  throws, no entry goes to a stranger, other followers keep the tweet, a tweet posted after the unfollow stays, the
  unfollower's older entry goes. The back-fill suite (frontend step 16) works the same way and does not assert
  follow, unfollow, follow within seconds (accepted gap).
- **The saved and view suites go through MockMvc** (steps 19, 27) where the contract is a client-visible status
  ("all 204"). The latch helper is shared as `support/LatchedTasks`. The opposite-order view scenario calls
  `ViewRecordingService` directly, because the tweet-service call in front spreads two requests too far apart to
  meet in the database.
- **The view concurrency suite does not prove the lock order** (step 27). Removing the `ORDER BY` from the counter
  upsert did **not** make it fail (five variants: fresh rows, 1,000 and 20,000 ids per report, existing rows with 30,
  100 and 300 reports per worker, workers shown to overlap). It guards against a deadlock only if one shows up; the
  ascending order stays as a defence against the hazard Postgres documents for `INSERT ... ON CONFLICT DO UPDATE`,
  covered by the repository test of the returned order.
- **View seeding goes through `ViewRecordingService`** (step 23). `seedView` / `seedViews` in
  `AbstractListenerIntegrationTest` use the real service, so a seeded counter and its rows agree.

## `replyCount` passed through (tweet service phase 2, step 18)

- **`TweetClientDTO.replyCount` is a `Long`, and the mapper turns a missing one into `0`.** Jackson 3 refuses a
  missing primitive (it builds the DTO through its all-args constructor and maps the gap to `null`), so a `long` made
  every tweet read fail with `502` whenever the tweet service did not send the field, for example while it is
  deployed after this service. `TweetItemResponseDTO.replyCount` stays a `long`, after `likedByMe`.

## The details read (tweet service phase 2, step 19)

- **`assembleFetched(viewerId, tweets, authorsById)`** is the second half of `assemble`: views, saves, likes, then the
  mapper. `assemble` fetches the tweets and the authors side by side, keeps the rows whose author and tweet exist, and
  calls it. Two small effects, both invisible from outside: the author of an item is now the tweet's own `authorId`
  (the rows named the same one), and the views, saves and likes queries cover only the tweets that were found.
- **The details read is sequential:** tweet by id, then its author, one call each. A gone tweet answers `404` without
  a call to the gateway; a gone author answers `404` "Author not found." (`AuthorNotFoundException`).
- **Controllers grouped into `feed`, `likes`, `savedtweets`, `views` and `tweetdetails`** (the package would have held
  6 files); `GlobalExceptionHandler` stays at the root, as in the test packages.

## Audit fixes (tweet details read, 2026-10-07)

- **Downstream timeouts are now 1 s connect and 3 s read (were 2 s and 5 s).** The details read calls two services
  in sequence, so the worst case is 2 × (1 + 3) = 8 s, under the gateway's 10 s read timeout; before it was ~14 s and the
  gateway answered `504` while this service kept working. The feed and the other reads get the tighter bound too.
  Both stay overridable through `TIMELINE_HTTP_CONNECT_TIMEOUT` / `TIMELINE_HTTP_READ_TIMEOUT`.
- **A tweet without `id`, `authorId` or `images`, a null item or a null body is an upstream failure (`502`).**
  `TweetLookupServiceImpl.findByIds` checks the answer instead of letting an NPE become a `500` with a stack trace.
  `UpstreamUnavailableException` gained a constructor without a cause for this.
