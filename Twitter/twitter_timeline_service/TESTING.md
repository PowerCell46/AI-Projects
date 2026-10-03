# Test catalog

Scope: HTTP-layer (`*ControllerIntegrationTest`) and listener (`*ListenerIntegrationTest`) tests only - the
full stack against Testcontainers Postgres and Kafka, with WireMock standing in for the gateway's internal API
and the tweet service. Unit, repository, client and concurrency tests are not listed here.

Hand-maintained - see `CLAUDE.md`'s "Standing rules" for the rule keeping this in sync.

Each phase's scenarios are written `@Disabled` first and approved before any feature code; each feature step
enables its group (see `PLAN.md`). One `@Nested` class per group, bodies empty until the step that enables it.

Negative async assertions ("nothing was added") use a sentinel event under the same key, sent after the event
under test. Kafka and dead-letter assertions are filtered by the test's own key.

## Phase 1 - Feed

Written `@Disabled` in step 5 and approved. Enabled so far: the three listener suites (step 8) and the feed endpoint (step 9).

### `GET /api/v1/feed`

`FeedControllerIntegrationTest` - WireMock stands in for the gateway's internal API and the tweet service. Enabled (step 9).

`Identity`

- A missing `X-User-Id` returns 400 (`should_return_400_when_the_user_id_header_is_missing`)
- A non-UUID, non-canonical (`1-1-1-1-1`) or empty `X-User-Id` returns 400 (`should_return_400_when_the_user_id_header_is_not_a_uuid`)

`Read`

- An empty feed returns 200, `items: []`, `nextCursor: null` (`should_return_an_empty_page_when_the_feed_is_empty`)
- Entries come newest first (`should_return_the_newest_entries_first_when_the_feed_has_several`)
- Entries with the same tweet time are ordered by tweet id (`should_break_ties_on_the_tweet_time_by_tweet_id_when_entries_share_a_timestamp`)
- Each item carries the tweet and the author from the stubs (`should_return_the_tweet_and_the_author_from_the_stubs_when_an_entry_exists`)
- An author without a picture has `profilePictureUrl: null`; added in step 9 (`should_return_a_null_picture_url_when_the_author_has_no_picture`)
- Only the caller's entries appear (`should_return_only_the_callers_entries_when_other_users_have_entries`)

`Paging`

- No `size` returns 20 items (`should_return_20_items_when_no_size_is_given`)
- `size` 0, 101 and -1 return 400 "Page size must be between 1 and 100." (`should_return_400_when_the_size_is_out_of_range`)
- A non-numeric `size` returns 400; added in step 9 (`should_return_400_when_the_size_is_not_a_number`)
- Walking the cursor visits every entry exactly once (`should_visit_every_entry_exactly_once_when_walking_the_cursor`)
- The same when a page boundary falls inside a run of equal timestamps (`should_visit_every_entry_exactly_once_when_a_page_boundary_falls_inside_a_run_of_equal_timestamps`)
- A truncated, padded, upper-case-id or garbage cursor returns 400 "Invalid cursor." (`should_return_400_when_the_cursor_is_tampered_with_or_not_canonical`)

`MissingData`

- A tweet absent from the tweet-service answer is skipped and `nextCursor` still advances (`should_skip_the_entry_and_still_advance_the_cursor_when_the_tweet_is_missing`)
- The same for a missing author (`should_skip_the_entry_and_still_advance_the_cursor_when_the_author_is_missing`)
- A page whose every entry is missing returns `items: []` with a `nextCursor` that leads on; added in step 9 (`should_return_an_empty_page_with_a_cursor_when_every_entry_of_the_page_is_missing`)
- The orphan entries a delete-before-create leaves are skipped the same way, because their tweet is missing (see `tweet.deleted`)

`Calls`

- One tweet call and one user call per page (`should_make_one_tweet_call_and_one_user_call_when_a_page_is_read`)
- Both calls carry only the ids of that page (`should_send_only_the_ids_of_the_page_in_both_calls`)
- An author of several tweets on the page is asked for once; added in step 9 (`should_ask_for_each_author_once_when_an_author_wrote_several_tweets_of_the_page`)
- The user call carries `X-Internal-Secret` and the tweet call does not (`should_send_the_internal_secret_on_the_user_call`)

`Failures`

- Tweet service down returns 502 (`should_return_502_when_the_tweet_service_is_down`)
- Gateway down returns 502 (`should_return_502_when_the_gateway_is_down`)
- Tweet service slower than the read timeout returns 504 (`should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout`)
- Gateway slower than the read timeout returns 504 (`should_return_504_when_the_gateway_is_slower_than_the_read_timeout`)
- A `5xx` from either returns 502 (`should_return_502_when_a_downstream_answers_5xx`, parameterized over the two)
- Neither leaks the downstream body (`should_not_leak_the_downstream_body_when_a_downstream_fails`, parameterized over the two)
- "Down" is a connection reset: the WireMock containers can't be made to refuse a connection

### `tweet.created`

`TweetCreatedListenerIntegrationTest` - real Kafka and Postgres, WireMock gateway. Enabled (step 8).

`FanOut`

- The author and every follower, across several follower pages, get one entry (`should_create_an_entry_for_the_author_and_every_follower_when_a_tweet_is_created_across_several_follower_pages`)
- Each entry stores the author and the exact tweet time; added in step 8 (`should_store_the_author_and_the_tweet_time_in_each_entry_when_a_tweet_is_created`)
- A redelivered event adds nothing (`should_add_nothing_when_the_same_event_is_redelivered`)

`Retries`

- Gateway `5xx` then recovery: retried, complete, no duplicates (`should_retry_and_end_complete_without_duplicates_when_the_gateway_fails_then_recovers`)
- Gateway down past the retries: the record lands on `tweet.created-dlt` (`should_dead_letter_the_record_when_the_gateway_stays_down_past_the_retries`)

`InvalidEvents`

- An invalid event goes to the DLT with no retry and no gateway call (`should_dead_letter_without_retry_or_a_gateway_call_when_the_event_is_invalid`)

### `tweet.deleted`

`TweetDeletedListenerIntegrationTest`. Enabled (step 8).

`Deletes`

- Every entry for the tweet is removed (`should_remove_every_entry_of_the_tweet_when_it_is_deleted`)
- Other tweets are untouched (`should_leave_other_tweets_untouched_when_a_tweet_is_deleted`)
- A repeated delete is a no-op (`should_do_nothing_when_the_same_delete_is_repeated`)
- A delete before the create leaves orphan entries in the feeds; that the feed then skips them is the `MissingData` scenarios of `GET /api/v1/feed`, since the endpoint doesn't exist yet (`should_leave_orphan_entries_when_the_delete_arrives_before_the_create`)

### `user.unfollowed`

`UserUnfollowedListenerIntegrationTest`. Enabled (step 8).

`Unfollows`

- The follower's entries by that author up to `occurredAt` are removed (`should_remove_the_followers_entries_by_that_author_up_to_the_occurred_at_time_when_the_user_unfollows`)
- Entries after `occurredAt` stay (`should_keep_the_entries_after_the_occurred_at_time_when_the_user_unfollows`)
- Other authors' entries stay (`should_keep_other_authors_entries_when_the_user_unfollows`)
- Other users' entries stay (`should_keep_other_users_entries_when_a_user_unfollows`)

`InvalidEvents`

- An invalid event goes to `user.unfollowed-dlt` (`should_dead_letter_the_record_when_the_event_is_invalid`)

### Retention

`FeedRetentionIntegrationTest` - the job is disabled in the test profile and invoked directly; time comes from the test clock, and the batch size is 5 in the test profile so a few entries span several batches. Enabled in step 10.

`Retention`

- Entries older than 7 days are deleted across several batches (`should_delete_entries_older_than_the_retention_across_several_batches_when_the_job_runs`)
- Newer entries stay (`should_keep_the_newer_entries_when_the_job_runs`)
- An entry exactly at the cutoff stays - the bound is strict (`should_keep_an_entry_exactly_at_the_cutoff_when_the_job_runs`) - added in step 10
- Entries delete once the clock moves past the retention (`should_delete_entries_that_age_past_the_retention_when_the_clock_moves_on`) - added in step 10
- Nothing is deleted when no entry is old enough (`should_delete_nothing_when_no_entry_is_old_enough`) - added in step 10
