# E2E test catalog

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

- A missing `X-User-Id` returns 400 "Missing or invalid caller identity." on every endpoint (parameterized over the implemented endpoints: all five routes) (`should_return_400_when_the_user_id_header_is_missing`)
- A non-UUID `X-User-Id` returns 400 on every endpoint (parameterized over the implemented endpoints: all five routes) (`should_return_400_when_the_user_id_header_is_not_a_uuid`)
- `GET /actuator/health` returns 200 without `X-User-Id` (`should_return_200_when_health_is_requested_without_the_user_id_header`)

## `POST /api/v1/tweets`

`TweetControllerIntegrationTest.CreateTweet`

- Text only returns 201 with the response body shape, `views = 0`, `createdAt == updatedAt`; Mongo holds the document with trimmed content and `authorId` from the header; one `PENDING` outbox message with every contract field (`should_return_201_and_store_the_tweet_and_a_pending_event_when_the_text_is_valid`)
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

## `GET /api/v1/tweets/{id}`

`TweetControllerIntegrationTest.GetTweet`

- Returns 200 with the response body shape (`should_return_200_with_the_body_shape_when_the_tweet_exists`)
- `views` is 1 on the first GET and 2 on the second (`should_count_each_get_as_a_view_when_the_tweet_is_read_repeatedly`)
- The author's own GET counts as a view (`should_count_the_view_when_the_author_reads_their_own_tweet`)
- `updatedAt` is unchanged by views (`should_leave_updated_at_unchanged_when_the_tweet_is_viewed`)
- An unknown id returns 404 "Tweet not found." (`should_return_404_when_the_tweet_is_unknown`)
- A non-UUID id returns 400 (`should_return_400_when_the_id_is_not_a_uuid`)

## `GET /api/v1/tweets/{id}/images/{imageId}`

`TweetControllerIntegrationTest.GetTweetImage`

- Returns 200, the bytes identical to the upload, the stored `Content-Type`, `Content-Length`, `Cache-Control: private, max-age=31536000, immutable` and `X-Content-Type-Options: nosniff` (`should_return_200_with_the_uploaded_bytes_and_headers_when_the_image_exists`)
- An unknown tweet returns 404 (`should_return_404_when_the_tweet_is_unknown`)
- An image id that belongs to another tweet returns 404 (`should_return_404_when_the_image_belongs_to_another_tweet`)
- Fetching an image does not change `views` (`should_not_change_views_when_an_image_is_fetched`)

## `PUT /api/v1/tweets/{id}`

`TweetControllerIntegrationTest.UpdateTweet`

- The author gets 200 with the new trimmed content, `updatedAt` moved (test clock), images and `views` unchanged (`should_return_200_and_update_the_content_when_the_author_edits`)
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
- Reading counts no view: `views` stays 0 in the response and in Mongo (`should_not_count_a_view_when_tweets_are_read`)
- 100 ids return 200 (`should_return_200_when_exactly_100_ids_are_given`)
- 101 ids return 400 "Provide between 1 and 100 tweet ids." (`should_return_400_when_101_ids_are_given`)
- An empty `ids` returns 400 (`should_return_400_when_the_ids_parameter_is_empty`)
- A missing `ids` returns 400 (`should_return_400_when_the_ids_parameter_is_missing`)
- A non-UUID id returns 400 (`should_return_400_when_an_id_is_not_a_uuid`)

## Concurrency

`TweetConcurrencyIntegrationTest` - through MockMvc, every scenario releases its threads from one latch and asserts only the final state.

- 50 overlapping GETs leave `views == 50` and hand out 50 distinct counts, 1 to 50 (`Views.should_count_every_view_and_hand_out_distinct_counts_when_fifty_reads_overlap`)
- 20 overlapping deletes by the author give exactly one 204 and 19 404s, one `tweet.deleted` outbox message, and no image objects left (`Deletes.should_answer_one_204_and_nineteen_404s_and_write_one_event_when_twenty_deletes_overlap`)
- An edit racing a delete, 50 rounds: the edit answers 200 or 404 and never 500, the delete answers 204, and every round ends with no tweet and one deleted event (`Edits.should_end_with_no_tweet_and_never_answer_500_when_an_edit_races_a_delete_fifty_times`)
- 20 overlapping edits all answer 200 and leave one of the 20 contents (`Edits.should_answer_200_to_every_edit_and_keep_one_of_the_contents_when_twenty_edits_overlap`)

