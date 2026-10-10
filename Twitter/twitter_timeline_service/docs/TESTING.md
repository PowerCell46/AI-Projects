# Test catalog

Last updated: 2026-10-10

Scope: HTTP-layer (`*ControllerIntegrationTest`) and listener (`*ListenerIntegrationTest`) tests, plus the Kafka consumer-group test - the
full stack against Testcontainers Postgres and Kafka, with WireMock standing in for the gateway's internal API
and the tweet service. Unit, repository, client and concurrency tests are not listed here.

Hand-maintained - see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

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
- Each item carries the tweet and the author from the stubs; the item's exact key set is `id, views, savedByMe, content, createdAt, updatedAt, images, author` (`should_return_the_tweet_and_the_author_from_the_stubs_when_an_entry_exists`)
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
- A tweet older than the retention adds no entry and costs no gateway call; a newer event under the same key (the
  sentinel) still lands (`should_add_nothing_and_ask_the_gateway_for_nothing_when_the_tweet_is_older_than_the_retention`).
  The class pins the mutable clock one second after `TWEET_CREATED_AT`, because the fixed tweet time would otherwise
  be older than the retention against the real clock

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
- A delete before the create leaves orphan entries in the feeds; that the feed then skips them is the `MissingData` scenarios of `GET /api/v1/feed`, since the endpoint doesn't exist yet (`should_leave_orphan_entries_when_the_delete_arrives_before_the_create`; it pins the mutable clock next to the tweet time, because the fan-out skips a tweet older than the retention)

### `user.unfollowed`

`UserUnfollowedListenerIntegrationTest`. Enabled (step 8).

`Unfollows`

- The follower's entries by that author up to `occurredAt` are removed (`should_remove_the_followers_entries_by_that_author_up_to_the_occurred_at_time_when_the_user_unfollows`)
- Entries after `occurredAt` stay (`should_keep_the_entries_after_the_occurred_at_time_when_the_user_unfollows`)
- Other authors' entries stay (`should_keep_other_authors_entries_when_the_user_unfollows`)
- Other users' entries stay (`should_keep_other_users_entries_when_a_user_unfollows`)

`InvalidEvents`

- An invalid event goes to `user.unfollowed-dlt` (`should_dead_letter_the_record_when_the_event_is_invalid`)

### `user.followed`

`UserFollowedListenerIntegrationTest`. Enabled (frontend plan step 16). WireMock stands in for the tweet service's by-author read and the gateway's follow check; the sentinel event carries the same key (the followee).

`Backfill`

- The followee's tweets are inserted into the follower's feed, newest first (`should_insert_the_followees_recent_tweets_into_the_followers_feed_newest_first_when_the_user_follows`)
- Each entry stores the author and the tweet time (`should_store_the_author_and_the_tweet_time_in_each_entry_when_the_user_follows`)
- The tweet service is asked for the newest 50 since 7 days before now (`should_ask_for_the_newest_fifty_tweets_since_seven_days_before_now`)
- A far-past `occurredAt` (`Instant.MIN`) changes nothing: the window is still 7 days before now, no retry (`should_still_ask_since_seven_days_before_now_when_the_event_time_is_far_in_the_past`)
- A tweet service answer with more than 50 tweets: only 50 are inserted (`should_insert_no_more_than_fifty_tweets_when_the_tweet_service_answers_with_more`)
- Other users' feeds are untouched (`should_leave_other_users_feeds_alone_when_a_user_follows`)
- A redelivered event adds nothing (`should_add_nothing_when_the_same_event_is_redelivered`)
- A followee with no recent tweets: no follow check is made (`should_skip_the_follow_check_when_the_followee_has_no_recent_tweets`)

`UnfollowedMeanwhile`

- The follow is gone at the check: none of the followee's entries are left (`should_leave_none_of_the_followees_entries_when_the_follow_is_gone_at_the_check`)
- Other authors' entries stay (`should_keep_other_authors_entries_when_the_follow_is_gone_at_the_check`)

`Retries`

- The check fails once, then recovers: the event is retried from the top and ends with the entries once (`should_retry_from_the_top_and_end_with_the_entries_once_when_the_follow_check_fails_then_recovers`)
- The tweet service stays down: retried, then `user.followed-timeline-dlt` (`should_dead_letter_the_record_when_the_tweet_service_stays_down_past_the_retries`)
- The gateway stays down: retried, then `user.followed-timeline-dlt` (`should_dead_letter_the_record_when_the_gateway_stays_down_past_the_retries`)

`InvalidEvents`

- An invalid event goes to `user.followed-timeline-dlt` without retries or a downstream call (`should_dead_letter_without_retry_or_a_downstream_call_when_the_event_is_invalid`)
- A self-follow goes to `user.followed-timeline-dlt` the same way and the user's own feed entries stay (`should_dead_letter_without_retry_or_a_downstream_call_and_keep_the_feed_when_the_user_follows_themselves`)

### Retention

`FeedRetentionIntegrationTest` - the job is disabled in the test profile and invoked directly; time comes from the test clock, and the batch size is 5 in the test profile so a few entries span several batches. Enabled in step 10.

`Retention`

- Entries older than 7 days are deleted across several batches (`should_delete_entries_older_than_the_retention_across_several_batches_when_the_job_runs`)
- Newer entries stay (`should_keep_the_newer_entries_when_the_job_runs`)
- An entry exactly at the cutoff stays - the bound is strict (`should_keep_an_entry_exactly_at_the_cutoff_when_the_job_runs`) - added in step 10
- Entries delete once the clock moves past the retention (`should_delete_entries_that_age_past_the_retention_when_the_clock_moves_on`) - added in step 10
- Nothing is deleted when no entry is old enough (`should_delete_nothing_when_no_entry_is_old_enough`) - added in step 10

### Consumer groups

`KafkaListenerGroupsIntegrationTest` - the shared application context against the real broker, read through the Kafka admin client.

- Each of the four listeners reads only its own topic, in its own group `<group-id>-<topic>`; checked from the members'
  assignments, one case per listener (`should_read_only_its_own_topic_in_its_own_group_when_the_listener_has_started`)

## Phase 2 - Saved tweets

Written `@Disabled` in step 14 and approved. Enabled in step 16: the saved-tweets endpoints and the `tweet.deleted` additions. Concurrency scenarios (50 parallel saves, save racing unsave) live in `SavedTweetConcurrencyIntegrationTest` (step 19: 50 parallel saves of one tweet, save racing unsave) and are not listed here.

### `PUT` / `DELETE` / `GET /api/v1/saved-tweets`

`SavedTweetControllerIntegrationTest` - WireMock stands in for the gateway's internal API and the tweet service. Enabled (step 16).

`Identity`

- A missing `X-User-Id` returns 400 on save, unsave and list (`should_return_400_when_the_user_id_header_is_missing_on_save`, `..._on_unsave`, `..._on_list`)
- A non-UUID, non-canonical or empty `X-User-Id` returns 400 on save, unsave and list (`should_return_400_when_the_user_id_header_is_not_a_uuid`, parameterized over the three values)

`Save` - `PUT /api/v1/saved-tweets/{tweetId}`

- An existing tweet returns 204 and is stored with its author (`should_return_204_and_store_the_tweet_with_its_author_when_the_tweet_exists`)
- Saving again returns 204, keeps one row and the original `saved_at` (`should_keep_one_row_and_the_original_saved_at_when_the_tweet_is_saved_again`)
- An unknown tweet returns 404 "Tweet not found." and stores nothing (`should_return_404_and_store_nothing_when_the_tweet_is_unknown`)
- A viewer who did nothing with the tweet gets `savedByMe`, `likedByMe` false and `views`, `replyCount` 0 (`should_return_the_viewers_own_flags_as_false_when_the_viewer_did_nothing_with_the_tweet`)
- One call goes to each downstream, with the tweet id and the author id (`should_make_one_call_to_each_downstream_when_the_tweet_is_read`)
- A malformed tweet id returns 400 (`should_return_400_when_the_tweet_id_is_malformed`)
- Tweet service down returns 502, nothing stored (`should_return_502_and_store_nothing_when_the_tweet_service_is_down`)
- A `5xx` from the tweet service returns 502, nothing stored (`should_return_502_and_store_nothing_when_the_tweet_service_answers_5xx`)
- Tweet service slower than the read timeout returns 504, nothing stored (`should_return_504_and_store_nothing_when_the_tweet_service_is_slower_than_the_read_timeout`)
- One tweet call carrying only that id, and no user call (`should_make_one_tweet_call_for_that_tweet_and_no_user_call_when_a_tweet_is_saved`)
- "Down" is a connection reset, as in the feed

`Unsave` - `DELETE /api/v1/saved-tweets/{tweetId}`

- A saved tweet returns 204 and the row is gone (`should_return_204_and_remove_the_row_when_the_tweet_is_saved`)
- A tweet that was not saved returns 204 (`should_return_204_when_the_tweet_is_not_saved`)
- A tweet already deleted still unsaves, 204 (`should_return_204_and_remove_the_row_when_the_tweet_was_already_deleted`)
- Only the caller's row is removed (`should_remove_only_the_callers_row_when_other_users_saved_the_same_tweet`)
- A malformed tweet id returns 400 (`should_return_400_when_the_tweet_id_is_malformed`)
- No tweet call and no user call: unsave has no existence check (`should_make_no_downstream_call_when_a_tweet_is_unsaved`)

`Read` - `GET /api/v1/saved-tweets?cursor=&size=`

- Nothing saved returns 200, `items: []`, `nextCursor: null`, and no downstream call (`should_return_an_empty_page_when_nothing_is_saved`)
- The most recently saved comes first (`should_return_the_most_recently_saved_first_when_several_are_saved`)
- Saves with the same `saved_at` are ordered by tweet id (`should_break_ties_on_the_saved_time_by_tweet_id_when_saves_share_a_timestamp`)
- Each item carries the tweet and the author from the stubs, in the feed's item shape (`should_return_the_tweet_and_the_author_from_the_stubs_when_a_tweet_is_saved`)
- An author without a picture has `profilePictureUrl: null` (`should_return_a_null_picture_url_when_the_author_has_no_picture`)
- Only the caller's saves appear (`should_return_only_the_callers_saves_when_other_users_have_saves`)

`Paging`

- No `size` returns 20 items (`should_return_20_items_when_no_size_is_given`)
- `size` 0, 101 and -1 return 400 "Page size must be between 1 and 100." (`should_return_400_when_the_size_is_out_of_range`)
- A non-numeric `size` returns 400 (`should_return_400_when_the_size_is_not_a_number`)
- Walking the cursor visits every saved tweet exactly once (`should_visit_every_saved_tweet_exactly_once_when_walking_the_cursor`)
- The same when a page boundary falls inside a run of equal timestamps (`should_visit_every_saved_tweet_exactly_once_when_a_page_boundary_falls_inside_a_run_of_equal_timestamps`)
- A truncated, padded, upper-case-id or garbage cursor returns 400 "Invalid cursor." (`should_return_400_when_the_cursor_is_tampered_with_or_not_canonical`)

`MissingData`

- A tweet absent from the tweet-service answer is skipped and `nextCursor` still advances (`should_skip_the_save_and_still_advance_the_cursor_when_the_tweet_is_missing`)
- The same for a missing author (`should_skip_the_save_and_still_advance_the_cursor_when_the_author_is_missing`)
- A page whose every save is missing returns `items: []` with a `nextCursor` that leads on (`should_return_an_empty_page_with_a_cursor_when_every_save_of_the_page_is_missing`)

`Calls`

- One tweet call and one user call per page (`should_make_one_tweet_call_and_one_user_call_when_a_page_is_read`)
- Both calls carry only the ids of that page (`should_send_only_the_ids_of_the_page_in_both_calls`)
- An author of several tweets on the page is asked for once (`should_ask_for_each_author_once_when_an_author_wrote_several_tweets_of_the_page`)
- The user call carries `X-Internal-Secret` (`should_send_the_internal_secret_on_the_user_call`)

`Failures` - on a read (a failed save is under `Save`)

- Tweet service down returns 502 (`should_return_502_when_the_tweet_service_is_down_on_a_read`)
- Gateway down returns 502 (`should_return_502_when_the_gateway_is_down_on_a_read`)
- Tweet service slower than the read timeout returns 504 (`should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout_on_a_read`)
- Gateway slower than the read timeout returns 504 (`should_return_504_when_the_gateway_is_slower_than_the_read_timeout_on_a_read`)
- A `5xx` from either returns 502 (`should_return_502_when_a_downstream_answers_5xx_on_a_read`, parameterized over the two)
- Neither leaks the downstream body (`should_not_leak_the_downstream_body_when_a_downstream_fails_on_a_read`, parameterized over the two)

### `savedByMe` on items

Frontend plan step 2. New group `SavedByMe` in `FeedControllerIntegrationTest` and in `SavedTweetControllerIntegrationTest`; the exact-key-set scenario of each `Read` group gained `savedByMe` (saved list: `should_return_the_tweet_and_the_author_from_the_stubs_when_a_tweet_is_saved`). One query on `saved_tweets` per page, no downstream call.

`FeedControllerIntegrationTest.SavedByMe`

- An item is `true` only when the caller saved its tweet, `false` otherwise (`should_mark_each_item_saved_only_when_the_caller_saved_its_tweet`)
- Every item is `false` when the caller saved nothing (`should_mark_no_item_saved_when_the_caller_saved_nothing`)
- Another user's save does not count (`should_not_mark_an_item_saved_when_only_another_user_saved_its_tweet`)
- Unsaving through `DELETE /saved-tweets/{id}` flips the next read to `false` (`should_mark_the_item_not_saved_when_the_caller_unsaves_its_tweet`)
- Still one tweet call and one user call per page (`should_make_no_extra_downstream_call_when_the_saved_state_is_added_to_the_page`)

`SavedTweetControllerIntegrationTest.SavedByMe`

- Every item of the saved list is `true` (`should_mark_every_item_saved_when_the_caller_lists_their_saved_tweets`)
- Still one tweet call and one user call per page (`should_make_no_extra_downstream_call_when_the_saved_state_is_added_to_the_page`)

### `tweet.deleted` additions

`TweetDeletedListenerIntegrationTest`, new group `SavedTweets`. Enabled (step 16).

`SavedTweets`

- Every saved row of the tweet is removed, across users (`should_remove_the_saved_rows_of_the_tweet_across_users_when_it_is_deleted`)
- Saved rows of other tweets stay (`should_leave_the_saved_rows_of_other_tweets_when_a_tweet_is_deleted`)
- Feed entries and saved rows of the tweet are both gone (`should_remove_the_feed_entries_and_the_saved_rows_together_when_a_tweet_is_deleted`)
- A repeated delete of a saved tweet is a no-op (`should_do_nothing_when_the_same_delete_is_repeated_and_the_tweet_was_saved`)

## Phase 3 - Views

Written `@Disabled` in step 20 and approved. Enabled: the views endpoints (step 22), the inline counts and the `tweet.deleted` additions (step 23). Concurrency scenarios (50 viewers on one tweet, one viewer 50x in parallel, two overlapping reports in opposite order) live in `ViewConcurrencyIntegrationTest` (step 27) and are not listed here. The `[tweet]` scenario (repeated `GET /tweets/{id}` leaves the document unchanged, no `views` in the response) lives in the tweet service's `docs/TESTING.md`, step 24.

### `POST` / `GET /api/v1/views`

`ViewControllerIntegrationTest` - WireMock stands in for the tweet service. The gateway is never called. Enabled (step 22).

`Identity`

- A missing `X-User-Id` returns 400 on report and on read (`should_return_400_when_the_user_id_header_is_missing_on_report`, `..._on_read`)
- A non-UUID, non-canonical or empty `X-User-Id` returns 400 on both (`should_return_400_when_the_user_id_header_is_not_a_uuid`, parameterized over the three values)

`Report` - `POST /api/v1/views`, body `{ "tweetIds": [...] }`

- A first report returns 204 and the count is 1 (`should_return_204_and_count_one_view_when_a_viewer_reports_a_tweet_for_the_first_time`)
- The same viewer again keeps the count at 1 (`should_keep_the_count_at_one_when_the_same_viewer_reports_the_tweet_again`)
- A second viewer makes it 2 (`should_count_two_when_a_second_viewer_reports_the_tweet`)
- The author's own view counts (`should_count_the_view_when_the_author_reports_their_own_tweet`)
- A batch of several tweets counts each once (`should_count_each_tweet_once_when_one_batch_holds_several_tweets`)
- A batch repeating one id counts it once (`should_count_the_tweet_once_when_one_batch_repeats_its_id`)
- Unknown ids are dropped silently and the known ones in the same batch are counted (`should_drop_the_unknown_ids_and_count_the_known_ones_when_a_batch_mixes_them`)
- A batch of only unknown ids returns 204 and records nothing (`should_return_204_and_record_nothing_when_every_id_is_unknown`)
- Exactly 50 ids returns 204 (`should_return_204_when_the_batch_holds_exactly_50_ids`)
- A batch of seen and unseen tweets bumps only the unseen ones (`should_count_only_the_new_pairs_when_a_batch_mixes_seen_and_unseen_tweets`)

`ReportValidation`

- An empty list returns 400 (`should_return_400_when_the_ids_list_is_empty`)
- 51 ids return 400 (`should_return_400_when_the_batch_holds_51_ids`)
- The 50-id limit applies to the list as sent: 51 entries that collapse to fewer ids return 400 (`should_return_400_when_the_batch_holds_51_entries_that_collapse_to_fewer_ids`)
- A malformed id returns 400 (`should_return_400_when_an_id_is_malformed`)
- A `null` id returns 400 (`should_return_400_when_an_id_is_null`)
- A missing `tweetIds` field returns 400 (`should_return_400_when_the_ids_field_is_missing`)
- A missing or non-JSON body returns 400 (`should_return_400_when_the_body_is_missing_or_not_json`)
- A rejected batch records nothing (`should_record_nothing_when_the_batch_is_rejected`)

`ReportCalls`

- One tweet call carrying only the distinct ids, and no user call (`should_make_one_tweet_call_with_the_distinct_ids_and_no_user_call_when_views_are_reported`)
- A rejected batch makes no tweet call (`should_make_no_tweet_call_when_the_batch_is_rejected`)

`ReportFailures` - "down" is a connection reset, as in the feed

- Tweet service down returns 502, nothing recorded (`should_return_502_and_record_nothing_when_the_tweet_service_is_down`)
- A `5xx` from the tweet service returns 502, nothing recorded (`should_return_502_and_record_nothing_when_the_tweet_service_answers_5xx`)
- Tweet service slower than the read timeout returns 504, nothing recorded (`should_return_504_and_record_nothing_when_the_tweet_service_is_slower_than_the_read_timeout`)
- The downstream body is not leaked (`should_not_leak_the_downstream_body_when_the_tweet_service_fails`)

`Read` - `GET /api/v1/views?tweetIds=a,b`

- Known tweets return their counts (`should_return_the_counts_of_known_tweets_when_they_have_views`)
- A tweet nobody viewed returns `0` (`should_return_zero_for_a_tweet_nobody_viewed`)
- One entry per requested id, `0` for unknown ids (`should_return_one_entry_per_requested_id_when_known_and_unknown_ids_are_mixed`)
- An id requested twice gives one entry (`should_return_one_entry_when_the_same_id_is_requested_twice`)
- Every caller sees the same counts (`should_return_the_same_counts_to_every_caller_when_different_users_read`)
- Exactly 100 ids returns 200 (`should_return_200_when_exactly_100_ids_are_requested`)
- Reading unknown ids creates no row (`should_create_nothing_when_unknown_ids_are_read`)
- No tweet-service call and no gateway call (`should_make_no_downstream_call_when_counts_are_read`)

`ReadValidation`

- A missing `tweetIds` returns 400 (`should_return_400_when_the_tweet_ids_parameter_is_missing`)
- An empty `tweetIds` returns 400 (`should_return_400_when_the_tweet_ids_parameter_is_empty`)
- 101 ids return 400 (`should_return_400_when_101_ids_are_requested`)
- A malformed id returns 400 (`should_return_400_when_an_id_is_malformed`)
- An empty element (`a,,b`) returns 400 (`should_return_400_when_the_list_holds_an_empty_element`)

### Inline counts

New group `Views` in `FeedControllerIntegrationTest` and in `SavedTweetControllerIntegrationTest`, same four scenarios in each. Enabled (step 23).

- Each item carries `views` from the tweet's counter (`should_return_the_view_count_of_each_item_when_tweets_have_views`, `..._when_saved_tweets_have_views`)
- `views` is `0` when nobody viewed the tweet (`should_return_zero_views_when_nobody_viewed_the_tweet`, `..._the_saved_tweet`)
- The count equals what `GET /views` returns for the same tweet (`should_return_the_same_count_as_the_views_endpoint_when_a_tweet_has_views`, `..._a_saved_tweet_has_views`)
- Views add no downstream call: still one tweet call and one user call per page (`should_make_no_extra_downstream_call_when_views_are_added_to_the_page`)

### `tweet.deleted` additions

`TweetDeletedListenerIntegrationTest`, new group `Views`. Enabled (step 23).

`Views`

- The tweet's view rows and its counter are removed (`should_remove_the_view_rows_and_the_counter_of_the_tweet_when_it_is_deleted`)
- Views and counters of other tweets stay (`should_leave_the_views_and_the_counter_of_other_tweets_when_a_tweet_is_deleted`)
- Feed entries, saved rows and views of the tweet are all gone (`should_remove_the_feed_entries_the_saved_rows_and_the_views_together_when_a_tweet_is_deleted`)
- A repeated delete of a viewed tweet is a no-op (`should_do_nothing_when_the_same_delete_is_repeated_and_the_tweet_had_views`)
- A delete of a tweet with no views is a no-op (`should_do_nothing_when_the_tweet_had_no_views`)

## Phase 4 - Likes

Written `@Disabled` in step 28 and approved. All enabled: the like endpoints and `LikeServiceImpl` (step 30), the inline fields and the `tweet.deleted` additions (step 31). Repository, unit and concurrency tests (`LikeConcurrencyIntegrationTest`, step 34) are not listed here. The gateway side is in the gateway's `TESTING.md`, the API journeys in `e2e/tests/likes.spec.ts`.

### `PUT` / `DELETE` / `GET /api/v1/likes`

`LikeControllerIntegrationTest` - WireMock stands in for the gateway's internal API and the tweet service. A copy of the saved-tweets suite; "down" is a connection reset, as in the feed. Enabled (step 30), except the two `Read` scenarios marked step 31.

`Identity`

- A missing `X-User-Id` returns 400 "Missing or invalid caller identity." on like, unlike and list, nothing stored (`should_return_400_when_the_user_id_header_is_missing_on_like`, `..._on_unlike`, `..._on_list`)
- A non-UUID, non-canonical or empty `X-User-Id` returns the same 400 on all three (`should_return_400_when_the_user_id_header_is_not_a_uuid`, parameterized over the three values)

`Like` - `PUT /api/v1/likes/{tweetId}`

- An existing tweet returns 204; one row with the stub's author and `liked_at` = the test clock; counter 1 (`should_return_204_and_store_the_like_with_its_author_when_the_tweet_exists`)
- Liking again after the clock moves returns 204, keeps one row, the original `liked_at` and counter 1 (`should_keep_one_row_and_the_original_liked_at_when_the_tweet_is_liked_again`)
- A second user's like makes the counter 2 (`should_count_both_likes_when_a_second_user_likes_the_tweet`)
- The author liking their own tweet returns 204 and counts (`should_return_204_and_count_the_like_when_the_author_likes_their_own_tweet`)
- An unknown tweet returns 404 "Tweet not found.", no row, no counter row (`should_return_404_and_store_nothing_when_the_tweet_is_unknown`)
- A malformed id (`abc`) returns 400 "Malformed request parameter.", no tweet call (`should_return_400_and_make_no_tweet_call_when_the_tweet_id_is_malformed`)
- The upper-case form of an already liked id changes nothing: 204, one row, counter 1 (`should_keep_one_row_when_the_tweet_is_liked_again_with_an_upper_case_id`)
- The short form `1-1-1-1-1` of an already liked `00000001-0001-0001-0001-000000000001` changes nothing (`should_keep_one_row_when_the_tweet_is_liked_again_with_the_short_form_of_its_id`)
- A body such as `{"likes": 1000, "userId": "<someone else>"}` is ignored: 204, the row is the caller's, counter 1 (`should_ignore_the_body_when_a_like_carries_one`)
- A body over 8 KB returns 413 "The request body is too large.", nothing stored (`should_return_413_and_store_nothing_when_the_body_is_over_8_kb`)
- `POST /api/v1/likes/{id}` returns 405 "This method is not supported for this endpoint." (`should_return_405_when_the_like_path_is_posted_to`)

`LikeCalls`

- One tweet call carrying only that id, and no user call (`should_make_one_tweet_call_for_that_tweet_and_no_user_call_when_a_tweet_is_liked`)

`LikeFailures`

- Tweet service down returns 502 "Upstream service unavailable.", no row for the caller (`should_return_502_and_store_nothing_when_the_tweet_service_is_down`)
- A `5xx`, a `4xx` or an unreadable body returns 502 (`should_return_502_and_store_nothing_when_the_tweet_service_answers_5xx_4xx_or_an_unreadable_body`, parameterized over 500, 404 and 200)
- Slower than the read timeout returns 504 "Upstream service timed out." (`should_return_504_and_store_nothing_when_the_tweet_service_is_slower_than_the_read_timeout`)
- A counter seeded at 1 by another user stays 1 in every failure (`should_leave_the_counter_untouched_when_the_like_fails`)
- The downstream body is not leaked (`should_not_leak_the_downstream_body_when_the_like_fails`)

`Unlike` - `DELETE /api/v1/likes/{tweetId}`

- A liked tweet returns 204, the row is gone, counter 1 -> 0 (`should_return_204_and_remove_the_row_and_decrement_the_counter_when_the_tweet_is_liked`)
- A tweet the caller did not like returns 204; another user's like keeps the counter at 1 (`should_return_204_and_keep_the_counter_when_the_caller_did_not_like_the_tweet`)
- Unliking twice returns 204 both times, counter 0, never -1 (`should_return_204_and_keep_the_counter_at_zero_when_the_tweet_is_unliked_twice`)
- Two likers, one unlikes: only that row goes, counter 1 (`should_remove_only_the_callers_row_when_two_users_liked_the_tweet`)
- A like whose tweet the tweet service no longer knows (seeded orphan) is still removed, 204 (`should_return_204_and_remove_the_row_when_the_tweet_is_unknown_to_the_tweet_service`)
- A malformed id returns 400 (`should_return_400_when_the_tweet_id_is_malformed`)
- No tweet call and no user call (`should_make_no_downstream_call_when_a_tweet_is_unliked`)

`Read` - `GET /api/v1/likes?cursor=&size=`

- Nothing liked returns 200, `items: []`, `nextCursor: null`, no downstream call (`should_return_an_empty_page_when_nothing_is_liked`)
- The most recently liked comes first (`should_return_the_most_recently_liked_first_when_several_are_liked`)
- Likes with the same `liked_at` are ordered by tweet id (`should_break_ties_on_the_liked_time_by_tweet_id_when_likes_share_a_timestamp`)
- Each item is in the feed's shape, exact key set `id, views, likes, likedByMe, savedByMe, content, createdAt, updatedAt, images, author`, `likedByMe: true` (`should_return_the_tweet_and_the_author_from_the_stubs_when_a_tweet_is_liked`; step 31)
- An author without a picture has `profilePictureUrl: null` (`should_return_a_null_picture_url_when_the_author_has_no_picture`)
- Only the caller's likes appear (`should_return_only_the_callers_likes_when_other_users_have_likes`)
- Liking again does not move a tweet up (`should_not_move_a_tweet_up_when_it_is_liked_again`)
- Unlike then like again moves it to the top, new `liked_at` (`should_move_a_tweet_to_the_top_when_it_is_unliked_and_liked_again`)
- An item's `likes` is the tweet's whole counter: Ana's list shows 2 when Bob liked it too (`should_return_the_whole_counter_as_likes_when_another_user_liked_the_tweet_too`; step 31)
- `savedByMe` is `true` only when the caller also saved the tweet (`should_mark_saved_by_me_only_when_the_caller_also_saved_the_tweet`)

`Paging`, `MissingData`, `Calls`, `Failures` - the saved list's groups, word for word (`should_..._liked_...` for `liked`, `like` for `save`, `..._on_a_read` for the failures), see "Phase 2 - Saved tweets"

- No `size` returns 20 items; `size` 0, 101 and -1 return 400 "Page size must be between 1 and 100."; a non-numeric `size` returns 400
- Walking the cursor visits every like once, also across a run of equal timestamps; a truncated, padded, upper-case-id or garbage cursor returns 400 "Invalid cursor."
- A missing tweet or author is skipped and the cursor advances; an all-missing page returns `items: []` with a cursor that leads on
- One tweet call and one user call per page, only that page's ids, each author once, the secret on the user call
- Either downstream down or `5xx` returns 502, slow returns 504, no body leaked

### `likes` and `likedByMe` on items

New group `Likes` in `FeedControllerIntegrationTest` and in `SavedTweetControllerIntegrationTest`, the same five scenarios in each. Enabled (step 31). The exact-key-set scenario of each `Read` group gains `likes, likedByMe`. One count query and one liked-ids query per page, no downstream call.

- `likes` is the tweet's counter, `0` when nobody liked it (`should_return_the_like_count_of_each_item_and_zero_when_nobody_liked_the_tweet`, `..._the_saved_tweet`)
- `likedByMe` is `true` only for the caller's own like (`should_mark_an_item_liked_only_when_the_caller_liked_its_tweet`)
- Another user's like does not count (`should_not_mark_an_item_liked_when_only_another_user_liked_its_tweet`)
- An unlike through `DELETE /likes/{id}` makes the next read `false` with the count one lower (`should_mark_the_item_not_liked_and_lower_the_count_when_the_caller_unlikes_its_tweet`)
- Still one tweet call and one user call per page (`should_make_no_extra_downstream_call_when_the_likes_are_added_to_the_page`)

### `tweet.deleted` additions

`TweetDeletedListenerIntegrationTest`, new group `Likes`. Enabled (step 31).

`Likes`

- Every like row of the tweet, across users, and its counter are removed (`should_remove_the_like_rows_and_the_counter_of_the_tweet_across_users_when_it_is_deleted`)
- Other tweets' likes and counters stay (`should_leave_the_likes_and_the_counter_of_other_tweets_when_a_tweet_is_deleted`)
- Feed entries, saved rows, views and likes of the tweet are all gone (`should_remove_the_feed_entries_the_saved_rows_the_views_and_the_likes_together_when_a_tweet_is_deleted`)
- A repeated delete of a liked tweet is a no-op (`should_do_nothing_when_the_same_delete_is_repeated_and_the_tweet_had_likes`)
- A delete of a tweet with no likes is a no-op (`should_do_nothing_when_the_tweet_had_no_likes`)
- The likes delete failing once removes nothing of the tweet (feed, saved, views, likes all still there); the retry removes all four (`should_remove_nothing_of_the_tweet_and_all_of_it_on_the_retry_when_the_likes_delete_fails_once`)

## Replies (tweet service phase 2) - reply count and tweet details

Written `@Disabled` in replies step 11 (tweet service `PLAN.md`); the groups are enabled in steps 18 and 19, all enabled now. The gateway side is in the gateway's `docs/TESTING.md`.

### `replyCount` on items

Enabled in step 18. New group `Replies` in `FeedControllerIntegrationTest`, `SavedTweetControllerIntegrationTest` and `LikeControllerIntegrationTest` (one scenario each), plus one mapper test. The exact-key-set scenario of each `Read` group gains `replyCount`.

- The tweet service's `replyCount` is passed through, `0` when nobody replied (feed: `should_return_the_reply_count_of_each_item_and_zero_when_nobody_replied_to_the_tweet`; saved: `..._the_saved_tweet`; liked: `..._the_liked_tweet`)
- `TweetItemMapperTest` copies it (`toItem_copiesTheReplyCountOfTheTweet`)

### `GET /api/v1/tweet-details/{tweetId}`

`TweetDetailsControllerIntegrationTest` - WireMock stands in for the gateway's internal API and the tweet service. Enabled (step 19).

`Identity`

- A missing `X-User-Id` returns 400 (`should_return_400_when_the_user_id_header_is_missing`)

`Read`

- A tweet returns 200 with every feed-item field, including `likedByMe`, `savedByMe`, `views` and `replyCount` (`should_return_200_and_every_feed_item_field_when_the_tweet_exists`)
- A malformed tweet id returns 400 (`should_return_400_when_the_tweet_id_is_malformed`)

`MissingData`

- A tweet the tweet service no longer has returns 404 "Tweet not found." and the gateway is not called (`should_return_404_when_the_tweet_is_gone`)
- An author the gateway no longer returns returns 404 "Author not found." (`should_return_404_when_the_author_is_gone`)

`Failures`

- The tweet service down or answering `5xx` returns 502 (`should_return_502_when_the_tweet_service_is_down`, `..._answers_5xx`)
- The gateway down or answering `5xx` returns 502 (`should_return_502_when_the_gateway_is_down`, `..._answers_5xx`)
- Either downstream slower than the read timeout returns 504 (`should_return_504_when_the_tweet_service_is_slower_than_the_read_timeout`, `..._the_gateway_...`)

## Profile - one author's tweets (frontend step 27)

### `GET /api/v1/author-tweets/{authorId}?cursor=&size=`

`AuthorTweetsControllerIntegrationTest` - WireMock stands in for the gateway's internal API and the tweet service. Enabled (step 27).

`Identity`

- A missing `X-User-Id` returns 400 (`should_return_400_when_the_user_id_header_is_missing`)

`Read`

- A page returns `nextCursor` and `items`; each item has every feed-item field (`should_return_200_and_every_feed_item_field_when_the_author_has_a_tweet`)
- `likedByMe` and `savedByMe` follow the viewer; `likes` counts everyone (`should_return_the_viewers_own_flags_per_viewer_when_two_viewers_read_the_same_page`)
- The tweet service's cursor is passed to the client and back (`should_pass_the_cursor_through_in_both_directions_when_the_author_has_more_pages`)
- An author with no tweets returns an empty page without a cursor (`should_return_an_empty_page_when_the_author_has_no_tweets`)
- A missing `size` asks the tweet service for 20 (`should_ask_the_tweet_service_for_20_when_the_size_is_missing`)
- One call to each downstream per page (`should_make_one_call_to_each_downstream_when_a_page_is_read`)
- A `size` of 0, -1, 101 or not a number returns 400 (parameterized) (`should_return_400_when_the_size_is_outside_1_to_100_or_not_a_number`)
- A malformed author id returns 400 (`should_return_400_when_the_author_id_is_malformed`)
- A cursor the tweet service refuses returns 400 "Invalid cursor." (`should_return_400_invalid_cursor_when_the_tweet_service_refuses_the_cursor`)

`MissingData`

- An author the gateway doesn't return (unknown or unconfirmed) returns 404 "Author not found." and no tweets are read (`should_return_404_when_the_author_is_unknown_and_read_no_tweets`)

`Failures`

- The gateway or the tweet service down or answering `5xx` returns 502 (`should_return_502_when_the_gateway_is_down`, `..._the_tweet_service_is_down`, `..._the_tweet_service_answers_5xx`)
- Either downstream slower than the read timeout returns 504 (`should_return_504_when_the_gateway_is_slower_than_the_read_timeout`, `..._the_tweet_service_...`)

Also `TweetLookupServiceIntegrationTest.FindPageByAuthor` (page and cursor read, size and cursor sent as parameters, no internal secret sent, `400` becomes `InvalidCursorException`, `5xx` / malformed page unavailable, slow answer timeout) and `AuthorTweetsServiceImplTest` (assembly for the viewer, unknown author, size range, downstream failure).

