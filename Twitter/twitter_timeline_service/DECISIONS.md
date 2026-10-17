# Decisions

Non-obvious calls made *during* implementation, the ones hard to re-derive from the code. Conventions live in
`CLAUDE.md`; open and accepted gaps are in `../ACCEPTED_GAPS_ROOT.md`.

One bullet per decision: what was chosen and why. Group by topic, keep the phase in parentheses for provenance. Skip
anything the code or its tests already say.

## Config and infra

- **`open-in-view` is off** (phase 1). The feed and saved reads call the gateway and tweet service over HTTP, and
  Boot's default would pin a pool connection for the whole request. Services that touch lazy data do it inside their
  own transaction.
- **Retention settings fail at startup when invalid** (phase 1). `FEED_RETENTION`, `FEED_CLEANUP_CRON`,
  `FEED_CLEANUP_ZONE` and `FEED_CLEANUP_BATCH_SIZE` map to `app.feed.*`. A zero or negative retention, or a batch size
  below 1, makes `FeedRetentionServiceImpl` throw at construction: `0` would end the loop without deleting, a negative
  one would turn the `LIMIT` into an error at the first night run.
- **The timeline's DLT variables carry a `TIMELINE_` prefix** (frontend phase 3). The mail service reads
  `USER_FOLLOWED_DLT_NAME` / `_PARTITIONS` from the same root `.env`, so one shared name would have put both services'
  dead letters on one topic. The timeline reads `TIMELINE_USER_FOLLOWED_DLT_NAME` and
  `TIMELINE_USER_FOLLOWED_DLT_PARTITIONS`; the topic name stays the shared `USER_FOLLOWED_TOPIC_NAME`. Chosen by the
  user on 2026-10-04.
- **Downstream timeouts are 1 s connect, 3 s read** (audit fix, 2026-10-07; were 2 s and 5 s). The details read calls
  two services in sequence, so the worst case is 2 × (1 + 3) = 8 s, under the gateway's 10 s read timeout (before:
  ~14 s, the gateway answered `504` while this service kept working). The feed and other reads get the tighter bound
  too. Overridable through `TIMELINE_HTTP_CONNECT_TIMEOUT` / `TIMELINE_HTTP_READ_TIMEOUT`.

## Downstream calls

- **Every failed downstream call is "unavailable" except a read timeout** (phase 1). Connect failures, resets, any
  error status (a `404` from a wrong secret is our bug, not the client's) and an unreadable answer become
  `UpstreamUnavailableException` (502); only a read timeout is `UpstreamTimeoutException` (504), a connect timeout is
  "unavailable", as in the gateway. Messages are fixed; the log line carries only the status or failure types, never a
  body or exception message.
- **A tweet without `id`, `authorId` or `images`, a null item or a null body is also a `502`** (audit fix, 2026-10-07).
  `TweetLookupServiceImpl.findByIds` checks the answer instead of letting an NPE become a `500` with a stack trace.
  `UpstreamUnavailableException` gained a constructor without a cause for this.
- **The secret goes to the gateway client only** (phase 1). `RestClientConfiguration` builds one `RestClient` per
  downstream; only the gateway one carries `X-Internal-Secret` (a test checks the tweet service never receives it). It
  refuses a secret under 32 bytes at startup, the gateway's own rule.
- **The follower loop hands pages to a consumer** (phase 1). `forEachFollowerPage(userId, Consumer<List<UUID>>)` lets
  the fan-out insert page by page (one transaction each) without holding every follower. An empty page is skipped; a
  consumer failure propagates and stops the paging.
- **The tweet and author lookups run on virtual threads** (phase 1). `FeedServiceImpl` uses `supplyAsync` on a
  `newVirtualThreadPerTaskExecutor()` bean (`downstreamCallExecutor`), waits for tweets first and authors second, each
  bounded by the read timeout. If both fail, the tweet failure is the one the caller sees (the future's wrapper is
  removed so the handler sees the lookup's own exception). Chosen over `StructuredTaskScope`, a preview API on this JDK.
  Authors are looked up by the entry's `author_id`, not the tweet's, so the two calls run side by side.
- **The follow check accepts only a `200` with `{"following": true|false}`** (frontend phase 3, audit finding 1). The
  gateway answers a wrong secret, or a build without the route, with a bare `404`; reading that as "not following" made
  a misconfiguration delete every back-fill silently. `FollowLookupServiceImpl` reads `FollowCheckClientDTO`; anything
  else (error status, `204`, a `200` without the field or body) is "unavailable", retried, then dead-lettered.

## Data model and SQL

- **The user attribute is `ownerId`, so the primary key is ordered right** (phase 1). Hibernate orders a composite
  key's columns by attribute name; with `userId` the key came out `(tweet_created_at, tweet_id, user_id)` and could not
  serve `WHERE user_id = ? ORDER BY tweet_created_at DESC, tweet_id DESC`. `ownerId` (column still `user_id`) gives
  `(user_id, tweet_created_at, tweet_id)`, so the key is the page index. A repository test reads the column order from
  `pg_index`.
- **One insert statement for the author and the followers** (phase 1). `insertIfAbsent(UUID[] userIds, ...)` is a
  single `INSERT ... SELECT unnest(...) ON CONFLICT DO NOTHING`; the author is a one-element array. It is
  `@Transactional` itself, so each call commits on its own.
- **Event times are cut to microseconds** (phase 1). The tweet service sends milliseconds, so nothing changes today,
  but the key stays identical on a redelivery and the cursor exact. The unfollow's `occurredAt` gets the same cut (the
  gateway sends more digits than Postgres keeps).
- **Ids are sorted by Postgres, not by Java** (phase 3). Both view statements put `ORDER BY tweet_id` on the `unnest`
  select so overlapping reports lock in one order; Java's `UUID.compareTo` (signed longs) differs from Postgres's
  `uuid`. The counter's `views` is `updatable = false`. `incrementAll` needs distinct ids (Postgres refuses to update a
  row twice in one statement), so the service deduplicates first.
- **The cleanup loop ends on a short batch** (phase 1). It repeats `deleteOlderThan` while a batch removes exactly the
  batch size; an exact multiple costs one extra empty statement, cheaper than a count first. One cutoff per run: rows
  that age past retention mid-run wait for the next night.

## Services

- **The tweet-and-author join lives in `TweetItemAssemblyService`** (phase 2). The saved list needs the same parallel
  lookup, skip-the-missing and mapping as the feed. `FeedServiceImplTest` builds the real assembler over mocked
  lookups; `SavedTweetServiceImplTest` mocks it. No separate assembler test: both services' tests and both controller
  suites cover it.
- **The saved page is `SavedTweetsResponseDTO`, not `FeedResponseDTO`** (phase 2). Same JSON, own class, so the two can
  diverge. `TweetItemResponseDTO` is shared. Cursors come from the last row read, never the last item returned.
- **The `tweet.deleted` delete stays in `FeedEntryCleanupService`** (phase 2). `onTweetDeleted` is `@Transactional` and
  deletes from `feed_entries` and `saved_tweets` together; a rename would touch the listener, the spy bean and three
  tests for no behaviour. `save` isn't transactional: it makes the HTTP call first, and with `open-in-view` off no
  connection is held meanwhile.
- **One validator and one exception for both tweet-id lists** (phase 3). `TweetIdsValidator` (1 to a limit, none
  `null`, counted as sent) serves the report body (50) and the read parameter (100); a bad list is
  `InvalidTweetIdsException`, 400. The request DTO carries no bean-validation annotations, so a missing `tweetIds` and
  a bad size get the same answer.
- **The view transaction lives in `ViewRecordingService`** (phase 3). `ViewServiceImpl.report` calls the tweet service
  first, then hands the existing ids to `record`, whose `@Transactional` wraps the insert-returning and the counter
  upsert. A separate bean because a self-call would skip the proxy; no connection is held during the HTTP call. Reports
  log nothing: they arrive every few seconds per open browser.
- **Inline counts come through `ViewService.countViews`** (phase 3): asked once per page after the two downstream
  calls, with the page's distinct ids; a tweet without a counter gets `0`. It is `getViews` without the 1-100 size
  check. `views` sits second in `TweetItemResponseDTO`.
- **`savedByMe` is read in the assembly service** (frontend phase 2). `assemble` takes the viewer id first and asks
  `SavedTweetRepository.findSavedTweetIds` once per page, so saved-list items are `true` without a special case. It
  reads the repository itself: going through `SavedTweetService` would be a cycle.
- **`assembleFetched(viewerId, tweets, authorsById)`** is the second half of `assemble` (tweet service phase 2): views,
  saves, likes, then the mapper. `assemble` fetches tweets and authors side by side, keeps rows whose author and tweet
  exist, and calls it. Effects, invisible from outside: an item's author is the tweet's own `authorId`, and the views,
  saves and likes queries cover only the tweets found.
- **The details read is sequential** (tweet service phase 2): tweet by id, then its author. A gone tweet answers `404`
  without a gateway call; a gone author answers `404` "Author not found." (`AuthorNotFoundException`).
- **`TweetClientDTO.replyCount` is a `Long`, the mapper turns a missing one into `0`** (tweet service phase 2). Jackson
  3 maps a missing primitive to `null` through the all-args constructor, so a `long` made every tweet read fail with
  `502` whenever the tweet service did not send the field (for example while deployed after this service).
  `TweetItemResponseDTO.replyCount` stays a `long`, after `likedByMe`.
- **`GET /api/v1/author-tweets/{authorId}`** (frontend phase 4) pages the tweet service's `by-author/{id}/page` read and
  builds items with `assembleFetched`, so an item equals a feed item. The author is looked up first (`404 Author not
  found.` before any tweet is read, an unconfirmed account included); size defaults to 20, 1-100.
- **The author-tweets cursor is the tweet service's, passed through** (frontend phase 4). A `400` from the tweet service
  becomes `InvalidCursorException` (`400 Invalid cursor.`) in `TweetLookupServiceImpl.findPageByAuthor`: size and id
  are already valid, so only the cursor can cause one. Other failures stay `502` / `504`.

## Likes (phase 4)

- **`LikeRecordingService.like` and `unlike` return a `boolean`**: `true` when a row was added or removed. The counter
  moves on exactly that, and the INFO line (`new like: {}`) reads it, so no second query.
- **`TweetLikeCountRepository.decrement` is an update by key, not an upsert.** A missing counter touches 0 rows, and a
  counter already at 0 is refused by `ck_tweet_like_counts_likes_non_negative`, so a broken invariant fails loudly
  instead of showing a negative count.
- **`LikeService` is not transactional; `LikeRecordingService` is.** The tweet lookup comes first and holds no
  connection, the recording opens the transaction, as for views.
- **Package grouping.** Repositories (`feed`, `savedtweets`, `views`, `likes`), response DTOs (`feed`, `savedtweets`,
  `likes`), controllers (`feed`, `likes`, `savedtweets`, `views`, `tweetdetails`, `authortweets`) and `DTOs/client`
  (`users`, `follows`, `tweets`) are split to stay under the five-file rule. `GlobalExceptionHandler`,
  `ErrorResponseDTO` and the item, author and image DTOs stay at the root.
- **The like controller suite seeds likes through `LikeRecordingService`**, so a seeded row and its counter agree, as
  view seeding does.
- **The "likes delete fails once" test retries through `JdbcTemplate`.** Mockito cannot call the real method of a
  spied Spring Data proxy (the retry then fails every time and dead-letters), so the spy throws on the first call and
  later runs `DELETE FROM tweet_likes` by hand, in the listener's transaction. The test is about the earlier deletes
  rolling back, not the like delete.
- **The phase 4 audit's findings are accepted, not fixed.** The Low (forged `X-User-Id` reads a private liked list and
  mints likes) and the Info (a forged `tweet.deleted` wipes likes) extend the "trusted header" and "trusted topic"
  gaps in `../ACCEPTED_GAPS_ROOT.md`; finding texts are in `../security-private/timeline-SECURITY-AUDITS-open.md`.

## Back-fill (frontend phase 3)

- **It is its own service.** `FeedBackfillService` (insert, check, undo) is called by `UserFollowedListener`; the
  by-author read is a `TweetLookupService` method, the follow check a new `FollowLookupService`. Not `@Transactional`:
  each insert and the undo run in their own repository transaction, and no connection is held across the two HTTP
  calls.
- **One insert per tweet, through the existing statement.** Up to 50 `insertIfAbsent` calls, each its own transaction,
  not a new multi-tweet statement: cheap, already idempotent, harmless to re-run on a retry. **Trigger:** the tweet
  service's 100 limit is raised and the size grows past a few hundred, or follow volume shows in the database → one
  statement over two arrays.
- **The undo bound is the clock's now.** When the follow is gone, the delete removes the follower's entries by that
  author up to now (cut to microseconds), including ones the author's own fan-out added meanwhile. Same delete as
  `user.unfollowed`.
- **The window starts at the clock's now minus the retention; the event's `occurredAt` is not read** (audit 2026-10-04,
  fixed 2026-10-09). The event time is not trusted: `Instant.MIN` threw and was retried ~4 minutes, a far-past value
  skipped the 7-day window. An entry older than the retention only waits for the next cleanup. `occurredAt` stays
  `@NotNull` as part of the wire contract.
- **A self-follow is an invalid event, and the size is capped by a constant, not a property** (same audit). The gateway
  never emits a self-follow; a forged one made the undo delete the user's own entries, so it goes to the DLT without a
  retry. `FEED_BACKFILL_SIZE` above 100 refuses to start, the limit a constant as in the tweet service
  (`MAX_TWEETS_PER_AUTHOR_READ`); a longer answer is cut to the size.

## Kafka listeners

- **A `tweet.created` older than the retention is skipped** (2026-10-10). `FeedFanOutServiceImpl` drops it after
  validating, with an info log and no follower lookup: replays (new consumer group, expired offsets, a dead-letter
  retry) bring old tweets back, each costing a gateway call and entries the nightly cleanup deletes anyway. The cutoff
  is the cleanup's own (`clock.instant().minus(retention)`, strictly older). The same check on `user.followed` is left
  out on purpose: the back-fill is anchored to the clock, not the untrusted event time, and a replayed follow only
  re-adds tweets the follower would have received anyway. The check lives in `utilities/Durations`, shared by the
  fan-out, the back-fill and the cleanup.
- **One consumer group per listener** (2026-10-10). The four listeners shared `twitter-timeline-service` (four
  containers of three threads), so a rebalance caused by any one paused all twelve consumers. Each listener now sets
  `groupId = "${spring.kafka.consumer.group-id}-<topic>"` (`-tweet-created`, `-tweet-deleted`, `-user-followed`,
  `-user-unfollowed`), so the existing property stays the one knob. Topics, DLTs and concurrency are unchanged. A new
  group starts at `earliest`, so an environment that ran the old group replays each topic once: `tweet.created` is cut
  by the retention check, `tweet.deleted` and `user.unfollowed` only delete, `user.followed` back-fills again
  (idempotent). The old group's offsets are left to expire. `KafkaListenerGroupsIntegrationTest` reads each group's
  real assignment from the broker and fails when a listener falls back to a shared group.
- **A stopped Kafka listener reports DOWN** (2026-10-11). `configurations/health/KafkaListenerHealthIndicator` reports
  `DOWN` with the stopped listener ids while any `KafkaListenerEndpointRegistry` container is not running; otherwise
  the service answered healthy while consuming nothing. `DOWN` turns `/actuator/health` to 503 and fails the compose
  health check, unlike the tweet and gateway outboxes' `DEGRADED`, because a stopped listener never recovers on its
  own.

## Tests

- **One shared application context** (phase 1). Every full-context test extends `AbstractDownstreamIntegrationTest`
  (`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles`, two `@MockitoSpyBean`s). A second context would join
  the same Kafka consumer group and take partitions, so a context whose spies the test never looks at could handle the
  record. `@DataJpaTest` slices start no listeners and stay as they are.
- **"No retry" is shown with a spy and a sentinel, not a clock** (phase 1). An invalid event is followed on the same key
  by a valid sentinel; once its effect is visible the spy has been called exactly twice and the dead-letter record is
  there.
- **Delete-before-create is split across two tests** (phase 1). The listener test asserts the orphan entries exist
  (`should_leave_orphan_entries_when_the_delete_arrives_before_the_create`); the feed's `MissingData` scenarios assert
  they are skipped.
- **The e2e "fan-out done" signal is Ana's feed** (phase 1). The fan-out writes the author's entry first, so Bob's
  entry proves nothing about the followers; Ana's, in the same follower page that would hold any other, is the signal.
  `feed.spec.ts` polls (`expect.poll`, 30 s) instead of sleeping.
- **The feed concurrency suite calls the services, not the listeners** (phase 1). Kafka's per-key ordering would
  serialise the overlap under test. Accepted races are not asserted away; what must always hold: nothing throws, no
  entry goes to a stranger, other followers keep the tweet, a tweet posted after the unfollow stays, the unfollower's
  older entry goes. The back-fill suite (frontend phase 3) works the same way and does not assert follow, unfollow,
  follow within seconds (accepted gap).
- **The saved and view suites go through MockMvc** (phases 2 and 3) where the contract is a client-visible status ("all
  204"). The latch helper is shared as `support/LatchedTasks`. The opposite-order view scenario calls
  `ViewRecordingService` directly, because the tweet-service call in front spreads two requests too far apart to meet
  in the database.
- **The view concurrency suite does not prove the lock order** (phase 3). Removing the `ORDER BY` from the counter
  upsert did **not** make it fail (five variants: fresh rows, 1,000 and 20,000 ids per report, existing rows with 30,
  100 and 300 reports per worker, workers shown to overlap). It guards against a deadlock only if one shows up; the
  ascending order stays as a defence against the hazard Postgres documents for `INSERT ... ON CONFLICT DO UPDATE`,
  covered by the repository test of the returned order.
- **View seeding goes through `ViewRecordingService`** (phase 3): `seedView` / `seedViews` in
  `AbstractListenerIntegrationTest` use the real service, so a seeded counter and its rows agree.
