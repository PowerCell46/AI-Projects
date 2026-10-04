# Decisions

Non-obvious calls made *during* implementation - the ones that would be hard to re-derive from the
code alone. Design settled up front lives in `PLAN.md`; conventions live in `CLAUDE.md`.

One entry per decision: what was chosen, what it was chosen over, and why.

---

## Step 4 - `open-in-view` is off

Boot defaults `spring.jpa.open-in-view` to true, which keeps a database connection checked out for the whole
request. The feed and saved reads call the gateway and the tweet service over HTTP, so with it on, a slow
downstream would pin pool connections. Chosen: off. Services that touch lazy data do it inside their own
transaction.

## Step 4 - datasource credentials follow the gateway's convention

`TIMELINE_DATASOURCE_USERNAME` / `TIMELINE_DATASOURCE_PASSWORD` default to `__PLACEHOLDER__` like the
gateway's, so a missing value fails to connect instead of silently using a guessable login. `.env.example`
sets both to the compose pair; only the URL default is real (`localhost:5433`).

## Step 4 - the downstream stand-ins are named now, the properties arrive with the clients

`AbstractDownstreamIntegrationTest` points `app.gateway-internal.url` and `app.tweet-service.url` at two
WireMock containers. The matching entries in `application.properties` (with `GATEWAY_INTERNAL_URL`,
`TWEET_SERVICE_URL`, `INTERNAL_API_SECRET` and the timeouts) are added in step 7, where the clients first read
them; nothing reads them before that.

## Step 4 - nothing Kafka-shaped yet

The consumer properties, error handlers, DLT topics and listeners are step 8. The scaffold ships only the
Kafka test container, so the smoke test needs Postgres alone and moves to the Kafka base class in step 8.

## Step 4 - one body cap

The tweet service's filter has a second, larger cap for image uploads. Nothing here takes a large body, so the
ported filter has the one `app.request.max-body-bytes` cap and no per-route special case.

## Step 6 - the user attribute is `ownerId`, so the primary key is ordered right

Hibernate orders a composite key's columns by attribute name, not declaration order. With `userId`, `tweetId`
and `tweetCreatedAt` the key came out `(tweet_created_at, tweet_id, user_id)`, which cannot serve `WHERE user_id
= ? ORDER BY tweet_created_at DESC, tweet_id DESC`. Renaming the attribute to `ownerId` (column still
`user_id`) sorts it first and gives `(user_id, tweet_created_at, tweet_id)`, so the key stays the page index as
the plan says, with no second index. A repository test reads the key's column order from `pg_index`, so a
rename that breaks it fails the build. Chosen over a separate `(user_id, tweet_created_at, tweet_id)` index,
which would store the same data twice.

## Step 6 - one insert statement for the author and the followers

The author's own entry (Q8) and a page of followers use the same `insertIfAbsent(UUID[] userIds, ...)`, a
single `INSERT ... SELECT unnest(...) ON CONFLICT DO NOTHING`; the author is a one-element array. The statement
is `@Transactional` itself, so each call commits on its own, which is what "one transaction per follower page"
needs.

## Step 6 - the cursor exception sits at the package root

`InvalidCursorException` is in `exceptions/`, not a feature subpackage, because the feed, the saved list and
(later) any other list use it. Its 400 handler arrives with the first endpoint, in step 9.

## Step 7 - the follower paging loop hands pages to a consumer

`FollowerLookupService.forEachFollowerPage(userId, Consumer<List<UUID>>)` fetches a page, hands it over, then
fetches the next, so the fan-out can insert page by page (one transaction each) and never holds every follower
at once. Chosen over returning one list (unbounded memory for a big account) and over exposing a single-page
call with the loop in the fan-out (the plan puts the loop in the client). An empty page is skipped, not handed
over. A consumer failure propagates unchanged and stops the paging.

## Step 7 - every failed downstream call is "unavailable" except a read timeout

Connect failures, resets, any error status (4xx included: a `404` from a wrong secret is our bug, not the
client's), and an unreadable answer all become `UpstreamUnavailableException` (502). Only a read timeout is
`UpstreamTimeoutException` (504); a connect timeout is "unavailable", as in the gateway. The messages are
fixed, and the log line carries only the status or the failure types, never the answer's body or exception
message, which can hold what the downstream sent.

## Step 7 - the secret goes to the gateway client only

`RestClientConfiguration` builds one `RestClient` per downstream. Only the gateway one carries
`X-Internal-Secret`, as a default header; a test checks the tweet service never receives it. The gateway client
refuses a secret under 32 bytes at startup, the same rule the gateway applies.

## Step 7 - id lists go out comma-separated

Both batch endpoints read `ids=a,b,c`; the lookups write the ids that way in one parameter, in input order. A
caller passes at most 100 ids (a page never has more); nothing chunks.

## Step 8 - one shared application context for every full-context test

Every test that needs the Spring context extends `AbstractDownstreamIntegrationTest`, which carries
`@SpringBootTest`, `@AutoConfigureMockMvc`, `@ActiveProfiles` and the two `@MockitoSpyBean`s. All of them
therefore hit one cached context. A second context would join the same Kafka consumer group and take a share of
the partitions, so a record could be handled by a context whose spies the test never looks at. The smoke test
moved to this base for the same reason. Repository slices (`@DataJpaTest`) start no listeners and stay as they
are.

## Step 8 - "no retry" is shown with a spy and a sentinel, not a clock

An invalid event is followed on the same key by a valid sentinel. Once the sentinel's effect is visible, the
service spy has been called exactly twice (the invalid event, then the sentinel), so the invalid one was not
retried, and the dead-letter record is there. Nothing waits for a retry that didn't happen.

## Step 8 - the delete-before-create scenario is split across two steps

`tweet.deleted` before `tweet.created` leaves entries behind; "the feed skips them" needs the feed endpoint,
which step 9 builds. The listener test now asserts the orphan entries exist, under the name
`should_leave_orphan_entries_when_the_delete_arrives_before_the_create`; the skipping is the feed's `MissingData`
scenarios. `TESTING.md` says so.

## Step 8 - dead-letter and consumer env names follow the mail service

`<TOPIC>_DLT_NAME` / `<TOPIC>_DLT_PARTITIONS` for `TWEET_CREATED`, `TWEET_DELETED` and `USER_UNFOLLOWED`,
`TIMELINE_LISTENER_CONCURRENCY` (default 3), and the shared `KAFKA_RETRY_*` trio. The main topic names are the
producers' `*_TOPIC_NAME` variables, so a rename there reaches this service from the same `.env`.

## Step 8 - the fan-out stores the event's tweet time cut to microseconds

The tweet service sends milliseconds, so the cut changes nothing today, but it keeps the key identical on a
redelivery whatever the producer's precision, and keeps the cursor exact. The same cut is applied to the
unfollow's `occurredAt` (the gateway sends more digits than Postgres keeps).

## Step 9 - the two lookups run on virtual threads from one executor bean

`FeedServiceImpl` starts the tweet call and the author call with `CompletableFuture.supplyAsync` on a
`newVirtualThreadPerTaskExecutor()` bean (`downstreamCallExecutor`), then waits for the tweets first and the
authors second. Each is bounded by the read timeout, so the request is too. If both fail, the tweet failure is
the one the caller sees; the other call is left to finish on its own timeout. The future's wrapper is removed,
so the handler sees the lookup's own `UpstreamUnavailableException` / `UpstreamTimeoutException`. Chosen over
`StructuredTaskScope`, which is still a preview API on this JDK.

## Step 9 - authors are looked up by the entry's `author_id`, not the tweet's

The entry already carries the author, so the user call doesn't wait for the tweet call, which is what lets them
run side by side. The item joins the author through the same id.

## Step 9 - the item and page DTOs are named for reuse

`TweetItemResponseDTO` (with `AuthorResponseDTO` and `TweetImageResponseDTO`) is not feed-specific: the saved
list in phase 2 returns the same item. `FeedResponseDTO` is the feed's page. Both have `nextCursor` last-row
semantics from the plan: the cursor comes from the last row read, never from the last item returned.

## Step 9 - packages regrouped as they passed five files

`configurations/downstream` (HTTP and REST client config, the executor), `utilities/paging` (cursor, codec, page
size check) and `utilities/mappers`, `exceptions/upstream`, `exceptions/events`, `configurations/kafka`.


## Step 10 - the retention job's settings live under `app.feed.*`

`FEED_RETENTION` (`7d`), `FEED_CLEANUP_CRON` (`0 0 4 * * *`), `FEED_CLEANUP_ZONE` (`Europe/Sofia`) and
`FEED_CLEANUP_BATCH_SIZE` (`10000`), as the plan names them, map to `app.feed.retention` and
`app.feed.cleanup.{cron,zone,batch-size}`. A retention that is zero or negative, or a batch size below 1, makes
`FeedRetentionServiceImpl` throw at construction, so the app refuses to start (same pattern as the mail
service's follow window). The batch size is checked too because `0` would make the loop exit without deleting
and a negative one would turn the `LIMIT` into an error at the first night run.

## Step 10 - the loop ends on a short batch, so an exactly-full last batch costs one extra empty statement

The service repeats `deleteOlderThan` while a batch removes exactly the batch size. When the expired rows are an
exact multiple of the batch size, the last call removes 0 and ends the loop; cheaper than a count first and
correct either way. One cutoff is computed per run, so rows that age past the retention mid-run wait for the
next night.

## Step 10 - the test profile uses a batch size of 5

`app.feed.cleanup.batch-size=5` in `application-test.properties`, so 11 entries span three batches without
seeding thousands of rows. The job's cron is `-` there, as in the gateway, and the tests call the job directly.

## Step 12 - e2e: one client per account, Ana's feed is the "fan-out done" signal

The `createAccount` fixture (in `e2e/tests/fixtures.ts`) registers, confirms from the Mailpit link and logs in
through the API, on its own `APIRequestContext` per user, because the session is a cookie. `feed.spec.ts` polls
(`expect.poll`, 30 s bound) instead of sleeping. The plan called Bob's feed the sentinel for "Carol never gets
it", but the fan-out writes the author's entry first, so Bob's entry proves nothing about the followers; Ana's
entry, which sits in the same single follower page that would hold any other follower, is the signal used.

## Step 12 - e2e found a Mongo first-write race in the tweet service

Fixed there (`MongoCollectionInitializer`, tweet-service DECISIONS.md), not worked around in the e2e. The e2e
stack's own settings: tweet-service `OUTBOX_POLL_FIXED_DELAY_MS=500`; timeline-service `FEED_CLEANUP_CRON` set to a
never-firing date; its own Postgres (`twitter_timeline_e2e`) next to the gateway's.

## Step 13 - concurrency suite calls the services, not the listeners

`FeedConcurrencyIntegrationTest` (package `concurrency`) releases its threads from one latch and calls
`FeedFanOutService` and `FeedEntryCleanupService` directly: Kafka's per-key ordering would serialise the very
overlap under test. Scenarios: the same `tweet.created` eight times at once; fan-out racing `tweet.deleted`;
fan-out racing `user.unfollowed` (20 rounds each on fresh ids).
The race outcomes the plan accepts are not asserted away: a tweet fanned out as its delete runs may land and is
removed by the repeated delete; a tweet created at or before an unfollow may land for the unfollower (Accepted
gaps). The assertions that must always hold are: nothing throws, no entry goes to a stranger, other followers keep
the tweet, a tweet posted after the unfollow always stays, the unfollower's older entry always goes.

## Step 13 - audit result

`exploit-report-2026-10-03.md`: nothing above Low. One fixed (`show-details=always` removed from the health
endpoint), four written into PLAN.md Accepted gaps with triggers (rate limit on feed reads, any `createdAt` on
`tweet.created`, a WARN line per rejected internal call, a forwarded client `X-Internal-Secret`).

## Step 14 - saved-tweets catalog leaves concurrency to step 19

The plan lists the concurrency scenarios under phase 2's scenarios, but `TESTING.md` scopes itself to HTTP and
listener tests (the phase 1 concurrency suite isn't in it either). They are written in step 19 as
`SavedTweetConcurrencyIntegrationTest`, not as `@Disabled` stubs here. The catalog also gains `Identity` (all three
endpoints need `X-User-Id`), a `5xx` save case, and the `Calls` groups, which the plan's list implies but doesn't name.

## Step 16 - the feed's tweet-and-author join moves to `TweetItemAssemblyService`

The saved list needs the same parallel tweet + author lookup, skip-the-missing and mapping as the feed, so it is
extracted (`TweetItemAssemblyService.assemble(rows, tweetIdOf, authorIdOf)`) and `FeedServiceImpl` now calls it.
`FeedServiceImplTest` keeps every assertion; only its `setUp` changed, building the real assembler over the
mocked lookups, so the join and failure scenarios still run through the feed. `SavedTweetServiceImplTest` mocks
the assembler. No separate assembler test: both services' tests and both controller suites cover it.

## Step 16 - saved page is `SavedTweetsResponseDTO`, not `FeedResponseDTO`

Same JSON (`items`, `nextCursor`), its own class so the feed's name doesn't leak into the saved endpoint and the
two can diverge (phase 3 adds `views` to both through the shared item).

## Step 16 - the `tweet.deleted` delete stays in `FeedEntryCleanupService`

`onTweetDeleted` is now `@Transactional` and deletes from `feed_entries` and `saved_tweets` in one transaction
(the repositories' own `@Transactional` joins it). The class keeps its name; a rename would touch the listener,
the spy bean and three tests for no behaviour. `save` isn't transactional: it makes the HTTP call first, and
`open-in-view` is off, so no connection is held meanwhile.

## Step 19 - concurrency suite goes through the HTTP layer; the latch helper is shared

`SavedTweetConcurrencyIntegrationTest` drives `PUT` / `DELETE` through MockMvc (the plan asks for "all 204", which
is a client-visible status), unlike the feed suite, which calls services because Kafka's per-key ordering would
hide its overlap. The latch-and-threads helper moved from `FeedConcurrencyIntegrationTest` to
`support/LatchedTasks`; the feed suite's assertions are unchanged.

## Step 19 - audit result

`exploit-report-2026-10-03-phase2.md`: nothing above Low. One Low (no rate limit on saves, now named in the
existing accepted gap) and one Info (save racing a delete, already an accepted gap). Nothing fixed, because
nothing needed it.

## Step 20 - views catalog leaves concurrency to step 27

As in step 14, the plan's concurrency scenarios (50 viewers, one viewer 50x, opposite-order reports) are not
`@Disabled` stubs here; they are written in step 27 as `ViewConcurrencyIntegrationTest`. The catalog adds, beyond
the plan's list: `Identity` on both endpoints, `ReportValidation` / `ReadValidation` edge cases, `ReportCalls`,
and four inline-count scenarios per list. The 50-id limit counts the list as sent, before duplicates collapse.

## Step 21 - ids are sorted by Postgres, not by Java

Both view statements (`tweet_views` insert-returning, `tweet_view_counts` upsert) put `ORDER BY tweet_id` on the
`unnest` select, so overlapping reports take their locks in one order. Sorting in SQL, not in the service, because
Java's `UUID.compareTo` (signed longs) orders differently from Postgres's `uuid`, and two callers sorting by
different rules would not share an order. The counter's `views` is `updatable = false`: only the upsert changes it.
`incrementAll` needs distinct ids (Postgres refuses to update a row twice in one statement), so the service
deduplicates first.

## Step 22 - one validator and one exception for both tweet-id lists

`TweetIdsValidator` (1 to a limit, none `null`, counted as sent) serves the report body (limit 50) and the read
parameter (limit 100); a bad list is `InvalidTweetIdsException`, 400 "Between 1 and N tweet ids are required, none
of them null." The request DTO carries no bean-validation annotations, so a missing `tweetIds` field and a bad size
get the same answer. A malformed id or a body that isn't JSON is Spring's own 400.

## Step 22 - the transaction lives in `ViewRecordingService`

`ViewServiceImpl.report` makes the tweet-service call first and then hands the existing ids to
`ViewRecordingService.record`, whose `@Transactional` wraps the insert-returning and the counter upsert. A separate
bean because a self-call would skip the proxy, and so no connection is held during the HTTP call. Reports log
nothing: they arrive every few seconds per open browser, and no outcome is worth a line.

## Step 23 - inline counts come through `ViewService.countViews`

`TweetItemAssemblyServiceImpl` asks `ViewService.countViews` once per page, after the two downstream calls, with the
page's distinct tweet ids; a tweet without a counter gets `0`. `countViews` is `getViews` without the 1-100 size
check (a page is at most 100 ids and never empty there), so both read the counters one way. `views` sits second in
`TweetItemResponseDTO`, after `id`. The tweet service still sends its own `views` until step 24; the client DTO
ignores it, and the stubs keep sending `7` so the "zero views" scenarios prove the count comes from this service.
The two "exact item shape" assertions in the feed and saved suites gained `views`; they stay exact.

## Step 23 - the view seeding helpers go through `ViewRecordingService`

`AbstractListenerIntegrationTest.seedView` / `seedViews` record views with the real `ViewRecordingService`, so a
seeded counter and its rows agree the way a report leaves them. `ViewControllerIntegrationTest` uses them too.

## Step 25 - a gate run lost to Docker clock skew was restarted, not retried in place

The first gateway `mvn verify` sequence had two green runs, then the machine slept mid-run and the Docker VM clock fell
8.5 minutes behind the host: MinIO answered `RequestTimeTooSkewed` and run 3 failed with 311 errors. After the clock
resynced, the 3x-in-a-row count started over and all three runs were green (711 tests each). No test or code changed.

## Step 26 - `follow` moves to the shared e2e fixtures

`views.spec.ts` needs Ana to follow Bob before he posts, as the feed spec does, so `follow` moved from `feed.spec.ts`
to `fixtures.ts` (the same move `postTweet` and `deleteTweet` made in phase 2). `unfollow` stays in the feed spec, the
only place that uses it. The spec reports through `POST /views`, reads `GET /views`, then polls Ana's feed for the
inline count and the count after Bob's delete.

## Step 27 - the concurrency suite does not prove the lock order

`ViewConcurrencyIntegrationTest` has three scenarios: 50 viewers report one tweet (count 50, 50 rows, all 204), one
viewer reports it 50x in parallel (count 1), and two workers report the same 200 tweets in opposite order, 100 times
each, calling `ViewRecordingService` directly because the tweet-service call in front of a request spreads two
requests too far apart to meet in the database. The overlap scenario asserts that both finish and every counter is
exact. Removing the `ORDER BY` from the counter upsert did **not** make it fail (five variants tried: fresh rows,
1,000 and 20,000 ids per report, existing rows with 30, 100 and 300 reports per worker, with the workers shown to
overlap), so the scenario guards against a deadlock only if one shows up; the ascending order stays as a defence
against the hazard Postgres documents for `INSERT ... ON CONFLICT DO UPDATE`, covered by the repository test of the
returned order.

## Frontend plan step 2 - `savedByMe` is read for both lists, in the assembly service

`TweetItemAssemblyService.assemble` takes the viewer id first and asks `SavedTweetRepository.findSavedTweetIds` once
per page, with the page's distinct tweet ids, right after the view counts. Both lists use the same path, so the
saved list's items are `true` because their rows are in that query's result, not because of a special case. The
assembly service reads the repository itself: going through `SavedTweetService` would be a cycle (it already depends
on the assembly service). `savedByMe` sits after `views` in `TweetItemResponseDTO`; the new constructor argument goes
last, so the existing positional constructions only gain one argument.

## Frontend plan step 2 - a gate run lost to a Kafka image crash was restarted, not retried in place

The second `mvn verify` after the change failed with 14 errors, all at context load: the `apache/kafka-native:4.3.1`
test container crashed on startup with a segfault inside the image (before any test ran), and the Docker daemon also
reported a missing container snapshot. No test assertion failed. The 3x-in-a-row count started over and all three
runs were green (446 tests each). No test or code changed between the runs.

## Frontend plan step 16 - the timeline's DLT env vars carry a `TIMELINE_` prefix

The plan named `USER_FOLLOWED_DLT_NAME` for `user.followed-timeline-dlt`, but the mail service reads the same names
(and `USER_FOLLOWED_DLT_PARTITIONS`) from the shared root `.env`, so setting one would have moved both services'
dead letters onto one topic. The timeline reads `TIMELINE_USER_FOLLOWED_DLT_NAME` and
`TIMELINE_USER_FOLLOWED_DLT_PARTITIONS`, as it already does for `TIMELINE_DATASOURCE_*`. The topic name stays
`USER_FOLLOWED_TOPIC_NAME`, shared on purpose with the gateway and the mail service. The user chose this on
2026-10-04; the plan's step text was changed with it.

## Frontend plan step 16 - back-fill is its own service, with two lookups beside the existing ones

`FeedBackfillService` (insert, check, undo) is called by `UserFollowedListener`. The by-author read is a method of
`TweetLookupService`; the follow check is a new `FollowLookupService`, because it asks the gateway something other
than follower ids. `FeedBackfillServiceImpl` is not `@Transactional`: each insert and the undo delete already run in
their own repository transaction, and no database connection should be held across the two HTTP calls.

## Frontend plan step 16 - the follow check reads the status itself; only 204 and 404 are answers (superseded by step 24 below)

`FollowLookupServiceImpl` uses `exchange`, which hands the status back instead of throwing, so a `404` means "no
follow" (`false`) and `204` means `true`. Every other status, a `401` from a wrong secret included, is "unavailable"
and retried, then dead-lettered: a misconfigured secret must never read as "unfollowed" and delete the back-fill.

## Frontend plan step 16 - one insert per tweet, through the existing statement

The back-fill inserts up to 50 rows with the existing `insertIfAbsent` (one user, one tweet), each in its own
transaction, not with a new multi-tweet statement. Fifty tiny inserts per follow is cheap, the statement is already
proven idempotent, and a retry from the top re-runs them harmlessly. **Trigger:** the back-fill size grows past a few
hundred, or follow volume makes it show up in the database → one statement over two arrays.

## Frontend plan step 16 - the undo bound is the clock's now

When the check says the follow is gone, the delete removes the follower's entries by that author up to the injected
clock's now (cut to microseconds). That also removes entries the author's own fan-out put there in the meantime,
which is right: the follow no longer exists. It is the same delete `user.unfollowed` runs.

## Frontend plan step 16 - the feed-order scenario is covered one level down

The plan's test strategy lists "back-filled rows page in time order in `GET /api/v1/feed`". A back-filled row is
indistinguishable from a fanned-out one, and the feed endpoint's own suite already covers order and paging over
those rows. The listener test asserts the order through the repository's feed query, and e2e scenario 1 (step 17)
asserts it through the endpoint, so no scenario was added to `FeedControllerIntegrationTest`.

## Frontend plan step 16 - no compose change was needed

The dev compose passes the root `.env` to the timeline service (`env_file`), and the e2e compose runs on defaults;
every new setting has one (`FEED_BACKFILL_SIZE` 50, the topic and DLT names, 3 DLT partitions). The new variables are
in `.env.example`.

## Frontend plan step 16 - the concurrency suite calls the services directly

`FeedBackfillConcurrencyIntegrationTest` releases 6 parallel back-fills (and a fan-out, or an unfollow event) from one
latch and asserts only the final state. Follow, unfollow, follow again within seconds is the plan's accepted gap and is
not asserted: the unfollow's delete may run after the second back-fill, so posts can go missing.

## Frontend plan step 24 - only a 200 with `{"following": true|false}` is an answer to the follow check

Replaces the step 16 entry above (audit finding 1). The gateway answers a missing or wrong secret, and a build
without the route, with a bare `404`, so reading `404` as "not following" made a misconfiguration delete every
back-fill with no error. The gateway now answers `200` with `{"following": true|false}`; `FollowLookupServiceImpl`
reads it with `retrieve()` and `FollowCheckClientDTO`, and everything else is "unavailable": any error status, a `204`,
a `200` without the field or without a body. That is retried and then dead-lettered, so the symptom is visible.
