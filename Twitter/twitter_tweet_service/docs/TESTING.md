# E2E test catalog

Last updated: 2026-10-10

Scope: HTTP-layer tests only - full stack through `DispatcherServlet` against Testcontainers Mongo, Kafka
and MinIO. Repository, unit and concurrency tests exercise real Mongo too but skip the HTTP layer, so they're
not listed here.

Hand-maintained - see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

Catalog: written `@Disabled` in step 2. Bodies are empty until the step that enables the group: Identity and
Create (step 5), Get and Get image (step 6), Update (step 7) and Delete (step 8) are all enabled. All tests live in
`TweetControllerIntegrationTest`, one `@Nested` class per group, and no `@Disabled` is left.
The two limit scenarios that need a real servlet container live in `TweetUploadLimitsIntegrationTest`.

## Identity (every endpoint)

`TweetControllerIntegrationTest.Identity`

- A missing `X-User-Id` returns 400 "Missing or invalid caller identity." on every endpoint (parameterized over the implemented endpoints: all six routes, the count included) (`should_return_400_when_the_user_id_header_is_missing`)
- A non-UUID `X-User-Id` returns 400 on every endpoint (parameterized over the implemented endpoints: all six routes, the count included) (`should_return_400_when_the_user_id_header_is_not_a_uuid`)
- `GET /actuator/health` returns 200 without `X-User-Id` (`should_return_200_when_health_is_requested_without_the_user_id_header`)

## `POST /api/v1/tweets`

`TweetControllerIntegrationTest.CreateTweet`

- Text only returns 201 with the response body shape without `views`, `createdAt == updatedAt`; Mongo holds the document with trimmed content and `authorId` from the header; one `PENDING` outbox message with every contract field (`should_return_201_and_store_the_tweet_and_a_pending_event_when_the_text_is_valid`)
- Text + 1 image returns 201; the object is in MinIO under its `objectKey` with the detected type; `imageIds` in the outbox payload match (`should_return_201_and_store_the_object_in_minio_when_one_image_is_attached`)
- Text + 4 images (JPEG, PNG, WebP mixed) returns 201; every object is in MinIO with its detected type; `imageIds` in the outbox payload match, in order (`should_return_201_and_keep_the_upload_order_when_four_mixed_images_are_attached`)
- Images with no `content` part returns 201 with `content = ""` (`should_return_201_with_empty_content_when_only_images_are_attached`)
- Blank `content` + an image returns 201 with `content = ""` (`should_return_201_with_empty_content_when_the_text_is_blank_and_an_image_is_attached`)
- No text and no images returns 400 "A tweet needs text or an image." (`should_return_400_when_there_is_no_text_and_no_image`)
- Whitespace-only text with no images returns 400 (`should_return_400_when_the_text_is_whitespace_only_and_there_is_no_image`)
- 280 code points returns 201 (`should_return_201_when_the_text_is_280_code_points`)
- 281 code points returns 400 "A tweet can be at most 280 characters." (`should_return_400_when_the_text_is_281_code_points`)
- 280 emoji returns 201 (code points, not UTF-16 units) (`should_return_201_when_the_text_is_280_emoji`)
- 5 images returns 400 "A tweet can have at most 4 images."; nothing in MinIO or Mongo (`should_return_400_and_store_nothing_when_five_images_are_attached`)
- A renamed non-image returns 415 "Unsupported image type.", and the valid images in the same request are not stored (`should_return_415_and_store_no_image_when_one_of_the_images_is_a_renamed_non_image`)
- An image over 5 MB returns 413 (`should_return_413_when_an_image_is_over_5_mb`)
- An empty image part returns 400 (`should_return_400_when_an_image_part_is_empty`)
- A body over the tweet body cap returns 413 (`should_return_413_when_the_whole_body_is_over_the_tweet_cap`)
- A JSON body instead of multipart returns 415 (`should_return_415_when_the_body_is_json_instead_of_multipart`)
- `objectKey` never appears in the response (`should_not_expose_the_object_key_when_the_tweet_is_created`)

Added in step 5:

- A missing `X-User-Id` answers with the message "Missing or invalid caller identity." (`Identity.should_answer_with_the_apps_error_message_when_the_user_id_header_is_missing`)

## `POST /api/v1/tweets` against a real servlet container

`TweetUploadLimitsIntegrationTest.CreateTweet` - MockMvc never applies Spring's multipart limits, so these run over real HTTP.

- 4 images, each exactly the 5 MB file limit, return 201 (`should_return_201_when_four_images_are_each_exactly_the_file_limit`)
- An image one byte over the file limit returns 413 "The uploaded file is too large." (`should_return_413_with_the_apps_error_shape_when_an_image_is_one_byte_over_the_file_limit`)

`TweetUploadLimitsIntegrationTest.OtherRoutes` - the 8 KB cap cannot see a chunked multipart body, so any route but the create route refuses multipart outright.

- A reply route receiving multipart returns 415 "This route does not accept multipart bodies." (`should_return_415_with_the_apps_error_shape_when_a_reply_route_receives_multipart`)

## `GET /api/v1/tweets/{id}`

`TweetControllerIntegrationTest.GetTweet`

- Returns 200 with the response body shape (`should_return_200_with_the_body_shape_when_the_tweet_exists`)
- Repeated GETs return no `views` field and leave the stored document unchanged (`should_leave_the_document_unchanged_and_return_no_views_when_the_tweet_is_read_repeatedly`)
- `updatedAt` is unchanged by reads (`should_leave_updated_at_unchanged_when_the_tweet_is_read`)
- An unknown id returns 404 "Tweet not found." (`should_return_404_when_the_tweet_is_unknown`)
- A non-UUID id returns 400 (`should_return_400_when_the_id_is_not_a_uuid`)

## `GET /api/v1/tweets/{id}/images/{imageId}`

`TweetControllerIntegrationTest.GetTweetImage`

- Returns 200, the bytes identical to the upload, the stored `Content-Type`, `Content-Length`, `Cache-Control: private, max-age=31536000, immutable` and `X-Content-Type-Options: nosniff` (`should_return_200_with_the_uploaded_bytes_and_headers_when_the_image_exists`)
- An unknown tweet returns 404 (`should_return_404_when_the_tweet_is_unknown`)
- An image id that belongs to another tweet returns 404 (`should_return_404_when_the_image_belongs_to_another_tweet`)
- Fetching an image leaves the stored document unchanged (`should_leave_the_document_unchanged_when_an_image_is_fetched`)

## `PUT /api/v1/tweets/{id}`

`TweetControllerIntegrationTest.UpdateTweet`

- The author gets 200 with the new trimmed content, `updatedAt` moved (test clock), images unchanged, no `views` in the response (`should_return_200_and_update_the_content_when_the_author_edits`)
- An edit writes no outbox message (`should_not_write_an_outbox_message_when_the_content_is_edited`)
- Blank content on a tweet with images returns 200 (`should_return_200_when_the_content_is_blank_and_the_tweet_has_images`)
- Blank content on a text-only tweet returns 400 (`should_return_400_when_the_content_is_blank_and_the_tweet_has_no_images`)
- 281 code points returns 400 (`should_return_400_when_the_content_is_281_code_points`)
- Someone else gets 403 "You can only edit your own tweets.", and the document is unchanged (`should_return_403_and_leave_the_tweet_unchanged_when_someone_else_edits`)
- An unknown id returns 404 (`should_return_404_when_the_tweet_is_unknown`)
- A body over 8 KB returns 413 (`should_return_413_when_the_body_is_over_8_kb`)

## `DELETE /api/v1/tweets/{id}`

`TweetControllerIntegrationTest.DeleteTweet`

- The author gets 204; the document is gone; every image object is gone (`should_return_204_and_remove_the_tweet_and_its_images_when_the_author_deletes`)
- One `PENDING` `tweet.deleted` outbox message with every contract field (`should_write_one_pending_deleted_event_when_the_author_deletes`)
- Someone else gets 403 "You can only delete your own tweets."; the tweet, the objects and the outbox are unchanged (`should_return_403_and_change_nothing_when_someone_else_deletes`)
- An unknown id returns 404 (`should_return_404_when_the_tweet_is_unknown`)
- A second delete returns 404 with no second event (`should_return_404_and_write_no_second_event_when_the_tweet_is_deleted_twice`)
- `GET` after delete returns 404 (`should_return_404_when_the_deleted_tweet_is_read`)
- `GET image` after delete returns 404 (`should_return_404_when_the_deleted_tweets_image_is_read`)

## `GET /internal/v1/tweets?ids=a,b`

`TweetControllerIntegrationTest.Internal` - service-to-service batch read for the timeline service; needs no `X-User-Id`.

- Returns 200 with the tweet body shape (`id`, `authorId`, `content`, `images` without `objectKey`) (`should_return_200_with_the_body_shape_when_the_tweet_exists`)
- Unknown ids are left out, known ones returned in any order (`should_return_only_the_tweets_that_exist_when_some_ids_are_unknown`)
- No existing id returns 200 with an empty list (`should_return_an_empty_list_when_no_id_exists`)
- A repeated id returns its tweet once (`should_return_each_tweet_once_when_an_id_is_repeated`)
- A comma-separated `ids` value is accepted (`should_accept_a_comma_separated_list_when_ids_are_joined`)
- Works without `X-User-Id` (`should_not_require_the_user_id_header`)
- Reading returns no `views` and leaves the documents unchanged (`should_return_no_views_and_leave_the_documents_unchanged_when_tweets_are_read`)
- 100 ids return 200 (`should_return_200_when_exactly_100_ids_are_given`)
- 101 ids return 400 "Provide between 1 and 100 tweet ids." (`should_return_400_when_101_ids_are_given`)
- An empty `ids` returns 400 (`should_return_400_when_the_ids_parameter_is_empty`)
- A missing `ids` returns 400 (`should_return_400_when_the_ids_parameter_is_missing`)
- A non-UUID id returns 400 (`should_return_400_when_an_id_is_not_a_uuid`)

## `GET /internal/v1/tweets/by-author/{authorId}?since=&limit=`

`TweetControllerIntegrationTest.InternalByAuthor` - service-to-service read for the timeline service's back-fill on follow; needs no `X-User-Id`.

- Returns 200 with `id` and `createdAt` only, nothing else (`should_return_200_with_only_the_id_and_created_at_when_the_author_has_a_tweet`)
- Tweets come newest first (`should_return_the_tweets_newest_first_when_the_author_has_several`)
- A tweet created exactly at `since` is included, an older one is left out (`should_include_a_tweet_created_exactly_at_since_and_leave_out_an_older_one`)
- `limit` keeps only the newest tweets (`should_return_only_the_newest_tweets_when_there_are_more_than_the_limit`)
- Other authors' tweets are left out (`should_leave_out_the_tweets_of_other_authors`)
- An author with no tweets returns 200 with an empty list (`should_return_an_empty_list_when_the_author_has_no_tweets`)
- Works without `X-User-Id`; reading leaves the documents unchanged (`should_not_require_the_user_id_header_and_leave_the_documents_unchanged`)
- A `limit` of 1 and of 100 return 200 (parameterized) (`should_return_200_when_the_limit_is_at_the_edge_of_the_range`)
- A `limit` of 0, -1 or 101 returns 400 "Provide a limit between 1 and 100." (parameterized) (`should_return_400_when_the_limit_is_outside_1_to_100`)
- A `limit` that is not a number returns 400 (`should_return_400_when_the_limit_is_not_a_number`)
- A missing `limit` returns 400 (`should_return_400_when_the_limit_is_missing`)
- A missing `since` returns 400 (`should_return_400_when_since_is_missing`)
- A `since` that is not an ISO-8601 instant (`yesterday`, a bare date, epoch millis, empty) returns 400 (parameterized) (`should_return_400_when_since_is_not_an_iso_8601_instant`)
- A `since` of 1970-01-01T00:00:00Z and of 9999-12-31T23:59:59Z return 200 (parameterized) (`should_return_200_when_since_is_at_the_edge_of_the_range`)
- A `since` before 1970 or after 9999 (including `+292278994-08-17T07:12:56Z`, which used to answer 500) returns 400 "Provide a since between 1970-01-01T00:00:00Z and 9999-12-31T23:59:59Z." (parameterized) (`should_return_400_when_since_is_outside_1970_to_9999`)
- A non-UUID `authorId` returns 400 (`should_return_400_when_the_author_id_is_not_a_uuid`)

## `GET /internal/v1/tweets/by-author/{authorId}/page?cursor=&size=`

`TweetControllerIntegrationTest.InternalByAuthorPage` - service-to-service paged read for the timeline service's profile page; needs no `X-User-Id`.

- Returns 200 with `nextCursor` and `items`, each item the full tweet shape of `POST` (`should_return_200_with_the_tweet_body_shape_and_no_cursor_when_the_author_has_one_tweet`)
- Tweets come newest first (`should_return_the_tweets_newest_first_when_the_author_has_several`)
- The next cursor leads to the rest and the last page has none (`should_split_the_tweets_over_pages_and_end_without_a_cursor_when_the_size_is_smaller`)
- A last page that is exactly full has no cursor (`should_return_no_cursor_when_the_tweets_exactly_fill_the_last_page`)
- Tweets with equal `createdAt` are neither repeated nor skipped across pages (`should_not_repeat_or_skip_a_tweet_when_several_share_one_created_at`)
- Other authors' tweets never appear (`should_leave_out_the_tweets_of_other_authors`)
- An author with no tweets returns an empty page without a cursor (`should_return_an_empty_page_when_the_author_has_no_tweets`)
- A missing `size` means 20 (`should_use_a_size_of_20_when_the_size_is_missing`)
- Works without `X-User-Id`; reading leaves the documents unchanged (`should_not_require_the_user_id_header_and_leave_the_documents_unchanged`)
- A `size` of 1 and of 100 return 200 (parameterized) (`should_return_200_when_the_size_is_at_the_edge_of_the_range`)
- A `size` of 0, -1, 101 or not a number returns 400 (parameterized) (`should_return_400_when_the_size_is_outside_1_to_100_or_not_a_number`)
- A malformed `cursor` returns 400 (parameterized) (`should_return_400_when_the_cursor_is_malformed`)
- A non-UUID `authorId` returns 400 (`should_return_400_when_the_author_id_is_not_a_uuid`)

## `GET /api/v1/tweets/count?authorId=`

`TweetControllerIntegrationTest.CountTweets`

- Returns 200 `{"count": n}` with the author's tweets only (`should_return_200_with_the_number_of_tweets_of_the_author`)
- The count drops by one after a delete (`should_return_the_count_one_lower_after_a_tweet_is_deleted`)
- An unknown author returns `0` (`should_return_zero_when_the_author_is_unknown`)
- A non-UUID `authorId` returns 400 (`should_return_400_when_the_author_id_is_not_a_uuid`)
- A missing `authorId` returns 400 (`should_return_400_when_the_author_id_is_missing`)
- A missing or malformed `X-User-Id` returns 400 (`Identity`)

## Concurrency

`TweetConcurrencyIntegrationTest` - through MockMvc, every scenario releases its threads from one latch and asserts only the final state.

- 20 overlapping deletes by the author give exactly one 204 and 19 404s, one `tweet.deleted` outbox message, and no image objects left (`Deletes.should_answer_one_204_and_nineteen_404s_and_write_one_event_when_twenty_deletes_overlap`)
- An edit racing a delete, 50 rounds: the edit answers 200 or 404 and never 500, the delete answers 204, and every round ends with no tweet and one deleted event (`Edits.should_end_with_no_tweet_and_never_answer_500_when_an_edit_races_a_delete_fifty_times`)
- 20 overlapping edits all answer 200 and leave one of the 20 contents (`Edits.should_answer_200_to_every_edit_and_keep_one_of_the_contents_when_twenty_edits_overlap`)

## Replies (phase 2)

All groups are enabled (steps 15-17). Scope as above: HTTP layer only.

### Identity (reply routes)

`ReplyControllerIntegrationTest.Identity` (enabled in step 16: the four reply routes, parameterized)

- A missing `X-User-Id` returns 400 on all four reply routes (`should_return_400_when_the_user_id_header_is_missing_on_a_reply_route`)
- A non-UUID `X-User-Id` returns 400 on all four reply routes (`should_return_400_when_the_user_id_header_is_not_a_uuid_on_a_reply_route`)

### `POST /api/v1/tweets/{tweetId}/replies`

`ReplyControllerIntegrationTest.CreateReply` (enabled in step 15)

- Returns 201 with the reply and its author `{id, username, profilePictureUrl}`; `edited = false`; `replyCount` on the tweet is 1 (`should_return_201_and_the_reply_with_its_author_when_the_content_is_valid`)
- Content is trimmed (`should_trim_the_content_when_a_reply_is_created`)
- 280 code points returns 201 (`should_return_201_when_the_content_is_280_code_points`)
- Empty (blank) content returns 400 "A reply needs text."; nothing written (`should_return_400_when_the_content_is_empty`)
- 281 code points returns 400 "A reply can be at most 280 characters."; nothing written (`should_return_400_when_the_content_is_281_code_points`)
- An unknown tweet returns 404 (`should_return_404_when_the_tweet_is_unknown`)
- A caller the lookup does not return gets 403 `CALLER_UNKNOWN`; nothing written (`should_return_403_and_write_nothing_when_the_caller_is_unknown`)
- The gateway down returns 502; nothing written (`should_return_502_and_write_nothing_when_the_gateway_is_down`)

### `GET /api/v1/tweets/{tweetId}/replies?cursor=&size=`

`ReplyControllerIntegrationTest.ListReplies` (enabled in step 15)

- Returns 200 `{items, nextCursor}`, oldest first (`should_return_200_with_the_replies_oldest_first_and_a_next_cursor_when_the_tweet_has_replies`)
- Following the cursor returns the next page (`should_return_the_next_page_when_the_cursor_is_followed`)
- A tweet with no replies returns 200 with no items (`should_return_200_with_no_items_when_the_tweet_has_no_replies`)
- An unknown tweet returns 404 (`should_return_404_when_the_tweet_is_unknown`)
- A bad cursor returns 400 (`should_return_400_when_the_cursor_is_bad`)
- A size of 0, -1 or 101 returns 400 "Page size must be between 1 and 100." (parameterized) (`should_return_400_when_the_size_is_outside_1_to_100`)
- A reply whose author the lookup does not return is left out of the page (`should_leave_out_a_reply_when_the_lookup_does_not_return_its_author`)
- The gateway down returns 502 (`should_return_502_when_the_gateway_is_down`)

### `PUT /api/v1/tweets/{tweetId}/replies/{replyId}`

`ReplyControllerIntegrationTest.UpdateReply` (enabled in step 16)

- The author gets 200 with the new content and `edited = true` (`should_return_200_and_mark_the_reply_edited_when_the_author_edits`)
- Unchanged text still marks it edited and moves `updatedAt` (`should_mark_the_reply_edited_when_the_text_is_unchanged`)
- Someone else gets 404 "Reply not found." (the same as a missing reply, so the answer never confirms an id); the reply is unchanged (`should_return_404_and_leave_the_reply_unchanged_when_someone_else_edits`)
- An unknown reply returns 404 (`should_return_404_when_the_reply_is_unknown`)
- A `replyId` of another tweet returns 404 (`should_return_404_when_the_reply_belongs_to_another_tweet`)
- Empty content returns 400 (`should_return_400_when_the_content_is_empty`)
- 281 code points returns 400 (`should_return_400_when_the_content_is_281_code_points`)
- An unknown caller gets 403 `CALLER_UNKNOWN`; the reply is unchanged (`should_return_403_when_the_caller_is_unknown`)

### `DELETE /api/v1/tweets/{tweetId}/replies/{replyId}`

`ReplyControllerIntegrationTest.DeleteReply` (enabled in step 16)

- The reply's author gets 204; the reply is gone; `replyCount` down by 1 (`should_return_204_and_decrement_the_count_when_the_reply_author_deletes`)
- The tweet's author gets 204 (`should_return_204_when_the_tweet_author_deletes`)
- Anyone else gets 404 "Reply not found."; nothing changes (`should_return_404_and_change_nothing_when_anyone_else_deletes`)
- An unknown reply returns 404 (`should_return_404_when_the_reply_is_unknown`)
- A `replyId` of another tweet returns 404 (`should_return_404_when_the_reply_belongs_to_another_tweet`)
- A second delete returns 404 and the count goes down only once (`should_return_404_when_the_reply_is_deleted_twice`)

### Tweet routes touched by replies

- `GET /api/v1/tweets/{id}` and the internal batch read carry `replyCount`; a new tweet answers 0 (`TweetControllerIntegrationTest.GetTweet.should_return_the_reply_count_when_the_tweet_has_replies`, `...should_return_a_reply_count_of_zero_when_the_tweet_has_none`; enabled in step 15)
- Deleting a tweet removes its replies and keeps the replies of other tweets (`TweetControllerIntegrationTest.DeleteTweet.should_remove_the_replies_of_the_tweet_and_keep_the_replies_of_others_when_the_tweet_is_deleted`; enabled in step 14)
- After the tweet is deleted, `GET .../replies` answers 404 (`TweetControllerIntegrationTest.DeleteTweet.should_answer_404_to_the_replies_list_when_the_tweet_is_deleted`; enabled in step 15)

### Concurrency (replies)

`ReplyConcurrencyIntegrationTest` - latch-released threads, final state only; enabled in step 17.

- 20 overlapping creates on one tweet: 20 x 201, `replyCount = 20`, 20 documents (`Creates.should_answer_201_to_every_create_and_count_twenty_when_twenty_creates_overlap`)
- 10 creates racing the tweet delete: no reply left for the tweet, the tweet gone (`Creates.should_leave_no_reply_and_no_tweet_when_ten_creates_race_the_tweet_delete`)
- Two deletes of one reply: one 204, one 404, count down by 1 (`Deletes.should_answer_one_204_and_one_404_and_decrement_once_when_two_deletes_overlap`)
- Create/delete churn on one tweet: `replyCount` equals the documents left (`Churn.should_end_with_a_reply_count_equal_to_the_documents_left_when_creates_and_deletes_churn`)

## Outbox publisher and health (not HTTP-layer, listed for completeness)

- `OutboxPublisherServiceImplTest` (unit, 2026-10-10): a message Kafka refuses for its own content (too large, invalid topic, also when `send` throws and when the failure is wrapped) counts an attempt and goes `FAILED` at the limit; anything else (a Kafka timeout, a network error, an authorization error, a send timeout, an interrupt) leaves the message `PENDING` with no attempt counted, however many polls find Kafka down; the batch stops at the first message Kafka cannot take and goes on past a rejected one; the interrupt flag is restored
- `OutboxPublisherServiceKafkaIntegrationTest.Outages` (real Kafka, 2026-10-10): a message stays `PENDING` with no attempt counted while the producer cannot reach a broker (more polls than `max-attempts`) and is published, then deleted, once Kafka is back (`should_keep_a_message_pending_with_no_attempt_counted_while_kafka_is_unreachable_then_publish_it_when_kafka_is_back`); a message for an invalid topic is `FAILED` after `max-attempts` polls while the message after it is published on the first (`should_mark_a_rejected_message_failed_after_the_max_attempts_and_still_publish_the_messages_after_it`)
- `OutboxHealthIndicatorTest` and `OutboxHealthIntegrationTest` (2026-10-10): `UP` with no `FAILED` message; `DEGRADED` with the count otherwise; `GET /actuator/health` answers 200 with `status: DEGRADED` while a `FAILED` message exists (`should_report_degraded_with_http_200_when_an_outbox_message_is_failed`)

