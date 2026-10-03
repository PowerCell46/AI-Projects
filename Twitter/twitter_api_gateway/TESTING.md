# E2E test catalog

Scope: HTTP-layer tests only — full stack through `DispatcherServlet` against Testcontainers Postgres and
Kafka (and MinIO from phase 2). Repository, unit and concurrency tests exercise real Postgres too but skip the
HTTP layer, so they're not listed here.

Hand-maintained — see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

Each phase's scenarios are written `@Disabled` first and approved before any feature code; each feature step
enables its group (see `PLAN.md`).

Phase 1 catalog: written `@Disabled` in step 2. All groups are enabled (steps 4–8); nothing in the catalog is `@Disabled`.

Phase 2 catalog (profile and files, at the bottom): written `@Disabled` in step 12 and approved. Bodies are empty until the step that enables the group; the profile read and edit groups are enabled (step 14), the upload and delete groups (step 16), the files group (step 17), and the concurrency test (step 18).

Phase 3 catalog (follows, at the bottom): written `@Disabled` in step 19 and approved. Bodies are empty until the step that enables the group: Follow and Unfollow are enabled (step 21), Profile is enabled (step 22), Lists is enabled (step 23). No `@Disabled` is left.

## `POST /api/v1/auth/register`

`AuthControllerIntegrationTest.Register` — enabled

- Returns 201 with `{id, username, email}` and no `Set-Cookie` (`should_return_201_with_the_user_and_no_cookie_when_the_registration_is_valid`)
- DB user has `enabled = false`, normalized username, lowercased email (`should_store_a_disabled_user_with_normalized_username_and_lowercased_email_when_registered`)
- One token row; stored hash is not the raw token (`should_store_one_token_row_whose_hash_differs_from_the_raw_token_when_registered`)
- One `PENDING` outbox row; payload has every contract field (`should_enqueue_one_pending_outbox_row_with_every_contract_field_when_registered`)
- `confirmationUrl` ends in a token whose hash matches the stored one (`should_end_the_confirmation_url_with_a_token_matching_the_stored_hash_when_registered`)
- Duplicate email returns 409 (`should_return_409_when_the_email_is_already_registered`)
- Duplicate email in another case returns 409 (`should_return_409_when_the_email_differs_only_in_case`)
- Duplicate username returns 409 (`should_return_409_when_the_username_is_already_taken`)
- Duplicate username in another case (`PeterG` vs `peterg`) returns 409 (`should_return_409_when_the_username_differs_only_in_case`)
- Username under 3 characters returns 400 (`should_return_400_when_the_username_is_shorter_than_3_characters`)
- Username over 15 characters returns 400 (`should_return_400_when_the_username_is_longer_than_15_characters`)
- Username with characters outside `[A-Za-z0-9_]` returns 400 (`should_return_400_when_the_username_has_characters_outside_letters_digits_and_underscore`)
- Malformed email returns 400 (`should_return_400_when_the_email_is_malformed`)
- Password under 8 characters returns 400 (`should_return_400_when_the_password_is_shorter_than_8_characters`)
- Password over 72 characters returns 400 (`should_return_400_when_the_password_is_longer_than_72_characters`)
- Password without a lowercase letter returns 400 (`should_return_400_when_the_password_has_no_lowercase_letter`)
- Password without an uppercase letter returns 400 (`should_return_400_when_the_password_has_no_uppercase_letter`)
- Password without a digit returns 400 (`should_return_400_when_the_password_has_no_digit`)
- Body over 8 KB returns 413 and creates no user (`should_return_413_when_the_body_is_over_the_size_cap`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)
- Password over 72 bytes but under 72 characters (multi-byte) returns 400, not 500 (`should_return_400_when_the_password_is_over_72_bytes_even_with_fewer_characters`)
- Password never appears in a success or an error response (`should_never_include_the_password_in_any_response`)

## `POST /api/v1/auth/confirm`

`AuthControllerIntegrationTest.Confirm` — enabled

- Returns 204; the user becomes enabled (`should_return_204_and_enable_the_user_when_the_token_is_valid`)
- Token row is gone after confirm (`should_delete_the_token_row_when_the_confirmation_succeeds`)
- No `Set-Cookie` on confirm (`should_return_204_without_a_cookie_when_the_confirmation_succeeds`)
- Reusing a token returns 400 (`should_return_400_when_the_token_is_reused`)
- Unknown token returns 400 (`should_return_400_when_the_token_is_unknown`)
- Malformed token (not 43 base64url chars) returns 400 (`should_return_400_when_the_token_is_malformed`)
- Clock moved past 24h: 400, user stays disabled (`should_return_400_and_keep_the_user_disabled_when_the_token_is_expired`)
- After a resend the old token gives 400 and the new one 204 (`should_return_400_for_the_old_token_and_204_for_the_new_one_when_the_confirmation_was_resent`)

## `POST /api/v1/auth/confirm/resend`

`AuthControllerIntegrationTest.Resend` — enabled

- Unknown email: 202, no outbox row (`should_return_202_and_enqueue_nothing_when_the_email_is_unknown`)
- Confirmed user: 202, no outbox row (`should_return_202_and_enqueue_nothing_when_the_user_is_already_confirmed`)
- Pending user: 202, old tokens deleted, one new token, one new outbox row (`should_return_202_with_one_new_token_and_one_new_outbox_row_when_the_user_is_pending`)
- Second resend within 60s: 202, no new row (`should_return_202_and_enqueue_nothing_when_resent_within_the_cooldown`)
- Resend after the clock moves 61s: 202, new row (`should_return_202_and_enqueue_a_new_row_when_resent_after_the_cooldown`)
- Malformed body returns 400 (`should_return_400_when_the_body_is_malformed`)

## `POST /api/v1/auth/login`

`AuthControllerIntegrationTest.Login` — enabled

- Login by email returns 200 with a cookie (`should_return_200_and_a_cookie_when_logging_in_by_email`)
- Login by username returns 200 with a cookie (`should_return_200_and_a_cookie_when_logging_in_by_username`)
- Login by username in another case returns 200 with a cookie (`should_return_200_and_a_cookie_when_the_username_differs_only_in_case`)
- Cookie is `HttpOnly`, `SameSite=Strict`, `Max-Age` = TTL (`should_set_the_cookie_as_http_only_same_site_strict_with_max_age_equal_to_the_ttl`)
- Wrong password returns 401 (`should_return_401_when_the_password_is_wrong`)
- Unknown email returns 401 (`should_return_401_when_the_email_is_unknown`)
- Unknown username returns 401 (`should_return_401_when_the_username_is_unknown`)
- Wrong password and unknown identifier return an identical 401 body (`should_return_the_same_401_body_for_a_wrong_password_and_an_unknown_identifier`)
- Password over 72 bytes but under 72 characters returns 400, not 500 (`should_return_400_when_the_password_is_over_72_bytes_even_with_fewer_characters`)
- Blank identifier or password returns 400 (`should_return_400_when_the_identifier_or_password_is_blank`)
- Malformed JSON returns 400 (`should_return_400_when_the_body_is_malformed_json`)
- Unconfirmed user with the correct password returns 403 (`should_return_403_when_the_password_is_correct_but_the_user_is_unconfirmed`)
- Unconfirmed user with a wrong password returns 401, never 403 (`should_return_401_when_the_password_is_wrong_and_the_user_is_unconfirmed`)

## `POST /api/v1/auth/logout`

`AuthControllerIntegrationTest.Logout` — enabled

- Logged in: 204 with a clearing cookie (`should_return_204_with_a_clearing_cookie_when_logged_in`)
- Not logged in: 204 with a clearing cookie (`should_return_204_with_a_clearing_cookie_when_not_logged_in`)

## `GET /api/v1/auth/me`

`AuthControllerIntegrationTest.Me` — enabled

- Returns 200 `{id, username, email}` from the claims (`should_return_200_with_the_claims_when_the_cookie_is_valid`)
- Still 200 after the user row is deleted, so claims alone answer (`should_return_200_without_reading_the_database_when_the_cookie_is_valid`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)
- Tampered signature returns 401 (`should_return_401_when_the_signature_is_tampered`)
- Expired token returns 401 (`should_return_401_when_the_token_is_expired`)
- Non-UUID `sub` returns 401 (`should_return_401_when_the_subject_is_not_a_uuid`)
- Missing `username` claim returns 401 (`should_return_401_when_the_username_claim_is_missing`)

## `every other route`

`AuthControllerIntegrationTest.Security` — enabled

- Any other route without a cookie returns 401 (`should_return_401_when_an_authenticated_route_is_called_without_a_cookie`, parameterized over GET, POST and DELETE paths)
- `GET /actuator/health` returns 200 without a cookie (`should_return_200_when_the_health_endpoint_is_called_without_a_cookie`)

## Concurrency

`AuthConcurrencyIntegrationTest` — 8 parallel calls released together through a latch, no sleeps

- Same email registered in parallel: one 201, the rest 409 (`should_give_exactly_one_201_and_the_rest_409_when_the_same_email_registers_in_parallel`)
- Usernames differing only in case registered in parallel: one 201, the rest 409 (`should_give_exactly_one_201_and_the_rest_409_when_usernames_differing_only_in_case_register_in_parallel`)
- One token confirmed in parallel: one 204, the rest 400 (`should_give_exactly_one_204_when_one_token_is_confirmed_in_parallel`)
- Resent in parallel after the cooldown: all 202, one new token, one new outbox row (`should_create_exactly_one_new_token_and_one_outbox_row_when_resent_in_parallel`)

# Phase 2 — profile and files

Every group below marked "disabled" is `@Disabled` with an empty body until its step. "Slot" scenarios are parameterized over
`profile-picture` and `cover-picture`.

## `GET /api/v1/users/{username}`

`ProfileControllerIntegrationTest.GetProfile` — enabled

- Returns 200 with `{id, username, bio, location, birthdate, profilePictureUrl, coverPictureUrl, createdAt, followersCount, followingCount, followedByMe}` (`should_return_200_with_the_profile_shape_when_the_user_exists`)
- Picture URLs are `null` when there are no pictures (`should_return_null_picture_urls_when_the_user_has_no_pictures`)
- Lookup in another case returns 200 (`should_return_200_when_the_username_differs_only_in_case`)
- Unknown user returns 404 (`should_return_404_when_the_user_is_unknown`)
- Unconfirmed user returns 404 (`should_return_404_when_the_user_is_unconfirmed`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)

## `PUT /api/v1/users/me`

`ProfileControllerIntegrationTest.EditProfile` — enabled

- Sets all fields and returns 200 with the profile (`should_return_200_and_set_all_fields_when_the_request_is_valid`)
- `null` clears the fields (`should_return_200_and_clear_the_fields_when_they_are_null`)
- Blank clears the fields (`should_return_200_and_clear_the_fields_when_they_are_blank`)
- Strings are trimmed (`should_trim_the_strings_when_they_have_surrounding_whitespace`)
- Bio over 160 characters returns 400 (`should_return_400_when_the_bio_is_longer_than_160_characters`)
- Location over 30 characters returns 400 (`should_return_400_when_the_location_is_longer_than_30_characters`)
- Future birthdate returns 400 (`should_return_400_when_the_birthdate_is_in_the_future`)
- Pictures are untouched (`should_not_touch_the_pictures_when_the_profile_is_edited`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)

## `PUT /api/v1/users/me/{profile-picture|cover-picture}`

`ProfileControllerIntegrationTest.UploadPicture` — enabled

- JPEG, PNG and WebP each return 200; the object is in MinIO, the `DbFile` row exists, the URL is in the response (`should_return_200_and_store_the_object_and_row_when_the_image_is_valid`, slot × format)
- Replacing deletes the old object and row and gives a new URL (`should_delete_the_old_object_and_row_and_return_a_new_url_when_a_picture_is_replaced`)
- GIF returns 415 (`should_return_415_when_the_image_is_a_gif`)
- Text renamed `.png` returns 415 (`should_return_415_when_the_file_is_text_renamed_to_png`)
- Truncated header returns 415 (`should_return_415_when_the_file_is_a_truncated_header`)
- Over 5 MB returns 413 (`should_return_413_when_the_file_is_over_5_mb`)
- Empty file returns 400 (`should_return_400_when_the_file_is_empty`)
- Missing `file` part returns 400 (`should_return_400_when_the_file_part_is_missing`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)

## `DELETE /api/v1/users/me/{profile-picture|cover-picture}`

`ProfileControllerIntegrationTest.DeletePicture` — enabled

- 204; object and row are gone and the URL is `null` (`should_return_204_and_remove_the_object_row_and_url_when_a_picture_exists`)
- 204 when there is no picture (`should_return_204_when_no_picture_exists`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)
- Deleting one slot leaves the other (`should_keep_the_other_slot_when_one_picture_is_deleted`)

## Upload limits over a real server

`PictureUploadLimitsIntegrationTest.PutPicture` — enabled. Runs on a real servlet container, because MockMvc applies neither the multipart limits nor the body-size filter to a parsed upload. Slot × scenario.

- A 100 KB file, far over the 8 KB normal cap, returns 200 (`should_return_200_when_the_file_is_far_over_the_normal_body_cap_but_within_the_upload_cap`)
- One byte over the file limit returns 413 with the app's error body (`should_return_413_with_the_apps_error_shape_when_the_file_is_one_byte_over_the_file_limit`)
- Exactly the file limit returns 200 (`should_return_200_when_the_file_is_exactly_the_file_limit`)

## `GET /api/v1/files/{id}`

`FileControllerIntegrationTest.GetFile` — enabled

- 200 with the exact bytes and the stored `Content-Type` (`should_return_200_with_the_exact_bytes_and_stored_content_type_when_the_file_exists`)
- `Cache-Control: private, max-age=31536000, immutable` and `X-Content-Type-Options: nosniff` (`should_return_the_private_immutable_cache_and_nosniff_headers_when_the_file_exists`)
- Another user's file is readable by any logged-in user (`should_return_200_when_the_file_belongs_to_another_user`)
- Unknown id returns 404 (`should_return_404_when_the_id_is_unknown`)
- Non-UUID id returns 400 (`should_return_400_when_the_id_is_not_a_uuid`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)

## Concurrency (phase 2)

`ProfileConcurrencyIntegrationTest` — enabled

- `PUT /users/me` in parallel with a picture `PUT` for the same user: both changes survive (`should_keep_both_changes_when_a_profile_edit_and_a_picture_upload_run_in_parallel`)
- Parallel picture `PUT`s to one slot: all `200`, exactly one `DbFile` row remains, no orphans (`should_leave_no_orphan_rows_when_uploads_to_one_slot_run_in_parallel`)

# Phase 3 — follows

Every group below marked "disabled" is `@Disabled` with an empty body until its step. All live in `FollowControllerIntegrationTest`. "List" scenarios are parameterized over `followers` and `following`.

## `PUT /api/v1/users/{username}/follow`

`FollowControllerIntegrationTest.Follow` — enabled

- 204; the row exists, target `followersCount` +1, follower `followingCount` +1 (`should_return_204_and_store_the_row_and_increment_both_counts_when_the_target_exists`)
- Repeat returns 204, counts unchanged (`should_return_204_and_keep_the_counts_when_the_follow_is_repeated`)
- New follow enqueues one PENDING `user.followed` outbox row with every contract field, keyed by the followee (`should_enqueue_one_pending_outbox_row_with_every_contract_field_when_the_follow_is_new`)
- Repeated follow keeps one outbox row (`should_keep_one_outbox_row_when_the_follow_is_repeated`)
- Following yourself enqueues nothing (`should_enqueue_nothing_when_the_user_follows_themselves`)
- Username in another case returns 204 (`should_return_204_when_the_username_differs_only_in_case`)
- Following yourself returns 400 and stores nothing (`should_return_400_and_store_nothing_when_the_user_follows_themselves`)
- Following yourself by a username in another case returns 400 (`should_return_400_when_the_user_follows_themselves_with_a_username_in_another_case`)
- Unknown target returns 404 (`should_return_404_when_the_target_is_unknown`)
- Unconfirmed target returns 404 (`should_return_404_when_the_target_is_unconfirmed`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)

## `DELETE /api/v1/users/{username}/follow`

`FollowControllerIntegrationTest.Unfollow` — enabled

- 204; the row is gone, both counts -1 (`should_return_204_and_remove_the_row_and_decrement_both_counts_when_the_follow_exists`)
- Repeat returns 204, counts unchanged (`should_return_204_and_keep_the_counts_when_the_unfollow_is_repeated`)
- Unfollow adds no `user.followed` row for the followee (`should_enqueue_no_user_followed_row_when_the_follow_is_removed`)
- A real unfollow enqueues one PENDING `user.unfollowed` outbox row with every contract field (`eventId`, `followerId`, `followeeId`, `occurredAt`), keyed by the follower (`should_enqueue_one_pending_unfollowed_row_with_every_contract_field_when_the_follow_is_removed`)
- A repeated unfollow enqueues nothing more (`should_enqueue_nothing_more_when_the_unfollow_is_repeated`)
- Unfollowing someone never followed enqueues nothing (`should_enqueue_nothing_when_the_user_never_followed_the_target_before_unfollowing`)
- Follow, unfollow, follow, unfollow leaves two `user.unfollowed` rows (`should_enqueue_one_unfollowed_row_per_removal_when_the_user_follows_and_unfollows_twice`)
- Never followed returns 204, counts unchanged (`should_return_204_and_keep_the_counts_when_the_user_never_followed_the_target`)
- Username in another case returns 204 (`should_return_204_when_the_username_differs_only_in_case`)
- Unfollowing yourself returns 400 (`should_return_400_when_the_user_unfollows_themselves`)
- Unknown target returns 404 (`should_return_404_when_the_target_is_unknown`)
- Unconfirmed target returns 404 (`should_return_404_when_the_target_is_unconfirmed`)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`)
- Other follows of the same users are untouched (`should_keep_the_other_users_follows_when_one_follow_is_removed`)

## `GET /api/v1/users/{username}` (follow fields)

`FollowControllerIntegrationTest.Profile` — enabled. The existing `ProfileControllerIntegrationTest.GetProfile` shape test now asserts the three extra fields too (`followersCount`, `followingCount`, `followedByMe`).

- New user: zero counts, `followedByMe = false` (`should_return_zero_counts_and_not_followed_when_the_user_has_no_follows`)
- Counts are correct from both sides (`should_show_the_followers_count_on_the_target_and_the_following_count_on_the_follower_when_a_follow_exists`)
- `followedByMe = true` when the viewer follows the target (`should_return_followed_by_me_true_when_the_viewer_follows_the_target`)
- `followedByMe = false` when only the target follows the viewer (`should_return_followed_by_me_false_when_only_the_target_follows_the_viewer`)
- Mutual follow: `true` on both sides (`should_return_followed_by_me_true_on_both_sides_when_two_users_follow_each_other`)
- Own profile: `followedByMe = false` (`should_return_followed_by_me_false_when_the_viewer_opens_their_own_profile`)
- Unfollow updates counts and flag (`should_update_the_counts_and_followed_by_me_when_the_follow_is_removed`)
- Response carries `followersCount`, `followingCount`, `followedByMe` (`should_return_the_counts_and_followed_by_me_in_the_profile_shape_when_the_user_exists`)

## `GET /api/v1/users/{username}/{followers|following}`

`FollowControllerIntegrationTest.Lists` — enabled. Slot = `followers` / `following`.

- Newest first (`should_return_the_items_newest_first_when_the_list_has_several_entries`, slot)
- Item shape `{id, username, bio, profilePictureUrl, followedByMe}` (`should_return_items_with_id_username_bio_picture_url_and_followed_by_me_when_the_list_is_not_empty`, slot)
- `followers` holds only users who follow the target (`should_list_only_the_users_who_follow_the_target_when_the_followers_are_requested`)
- `following` holds only users the target follows (`should_list_only_the_users_the_target_follows_when_the_following_are_requested`)
- Default size is 20, with a next cursor when more exist (`should_return_20_items_and_a_next_cursor_when_no_size_is_given_and_more_exist`, slot)
- Following cursors to the end covers every entry once (`should_return_every_entry_exactly_once_when_the_pages_are_followed_to_the_end`, slot)
- New follows inserted between page requests: still once each (`should_return_every_entry_exactly_once_when_new_follows_are_inserted_between_page_requests`, slot)
- Equal `createdAt` on every follow: no gaps or duplicates (`should_return_every_entry_exactly_once_when_all_follows_share_the_same_timestamp`, slot)
- Last page has `nextCursor = null` (`should_return_a_null_next_cursor_when_the_page_is_the_last_one`, slot)
- Entries exactly filling the page: `nextCursor = null` (`should_return_a_null_next_cursor_when_the_entries_exactly_fill_the_page`, slot)
- Empty list: `items: []`, `nextCursor = null` (`should_return_empty_items_and_a_null_next_cursor_when_the_list_is_empty`, slot)
- `followedByMe` correct per item (`should_mark_followed_by_me_per_item_when_the_viewer_follows_only_some_of_them`, slot)
- An unfollowed entry drops out (`should_drop_the_entry_when_the_follow_is_removed`, slot)
- Size 1 and 100 return 200 (`should_return_200_when_the_size_is_1_or_100`, slot)
- Size 0 returns 400 (`should_return_400_when_the_size_is_0`, slot)
- Size 101 returns 400 (`should_return_400_when_the_size_is_101`, slot)
- Non-numeric size returns 400 (`should_return_400_when_the_size_is_not_a_number`, slot)
- Garbage cursor returns 400 "Invalid cursor." (`should_return_400_when_the_cursor_is_garbage`, slot)
- Valid base64url with wrong content returns 400 (`should_return_400_when_the_cursor_is_valid_base64url_but_has_the_wrong_content`, slot)
- Username in another case returns 200 (`should_return_200_when_the_username_differs_only_in_case`, slot)
- Unknown user returns 404 (`should_return_404_when_the_user_is_unknown`, slot)
- Unconfirmed user returns 404 (`should_return_404_when_the_user_is_unconfirmed`, slot)
- No cookie returns 401 (`should_return_401_when_there_is_no_cookie`, slot)

## Concurrency (phase 3)

`FollowConcurrencyIntegrationTest` — enabled; calls released together through a latch, no sleeps

- 50 distinct users follow one target in parallel: 50 rows, `followersCount = 50`, 50 outbox rows (`should_store_exactly_50_rows_and_count_50_when_50_users_follow_one_target_in_parallel`)
- 20 parallel follows of one pair: 1 row, counts 1, 1 outbox row (`should_store_one_row_and_count_one_when_the_same_pair_follows_in_parallel`)
- Follow and follow-back in parallel, 50 rounds on fresh pairs: no deadlock, counts correct (`should_count_both_sides_without_deadlock_when_two_users_follow_each_other_in_parallel_50_times`)
- Seeded 200-operation follow/unfollow storm over 8 users: every `followers_count` and `following_count` equals its `COUNT(*)` (`should_keep_every_counter_equal_to_its_row_count_when_follows_and_unfollows_storm_in_parallel`)


# Phase 4 — tweet routing

Written `@Disabled` in step 25 and approved; every group is now enabled (steps 26–29). `TweetRoutesIntegrationTest` uses WireMock as the tweet service. `TweetServiceContractIntegrationTest` runs the real tweet service from its Dockerfile.

## `/api/v1/tweets/**` (proxy)

`TweetRoutesIntegrationTest.Authentication` — enabled

- No cookie returns 401 on GET, POST, PUT and DELETE, and nothing reaches the tweet service (`should_return_401_and_forward_nothing_when_there_is_no_cookie`, method)

`TweetRoutesIntegrationTest.Forwarding` — enabled

- GET forwards path, query and status unchanged (`should_forward_a_get_with_the_path_query_and_status_unchanged_when_the_user_is_authenticated`)
- POST forwards body and status unchanged (`should_forward_a_post_with_the_body_and_status_unchanged_when_the_user_is_authenticated`)
- PUT forwards body and status unchanged (`should_forward_a_put_with_the_body_and_status_unchanged_when_the_user_is_authenticated`)
- DELETE forwards status unchanged (`should_forward_a_delete_with_the_status_unchanged_when_the_user_is_authenticated`)
- Downstream 400/403/404/413/415 pass through with their body (`should_pass_the_downstream_error_status_and_body_through_unchanged`, status)

`TweetRoutesIntegrationTest.Identity` — enabled

- Tweet service receives `X-User-Id` = the JWT `sub` (`should_send_the_jwt_subject_as_x_user_id_when_the_request_is_forwarded`)
- Spoofed `X-User-Id` / `x-user-id` is replaced by the `sub` (`should_replace_a_spoofed_x_user_id_with_the_jwt_subject`, header name)
- Spoofed `X-User-Role` is dropped (`should_drop_a_spoofed_x_user_role_header`)
- `Cookie` never arrives (`should_not_forward_the_cookie_header`)
- `Authorization` never arrives (`should_not_forward_the_authorization_header`)

`TweetBodyCapsIntegrationTest` — enabled. Runs on a real servlet container, because MockMvc never applies the multipart limits or the body-size filter to a parsed upload. The tweet service is an in-process recording server that keeps each body's length and SHA-256, because WireMock's client cannot read a body over Jackson's 20 MB string limit.

- Multipart POST exactly at the tweet cap arrives byte-identical (`CreateTweet.should_forward_a_byte_identical_multipart_post_when_the_body_is_exactly_at_the_tweet_cap`)
- One byte over returns 413, nothing forwarded (`CreateTweet.should_return_413_and_forward_nothing_when_a_multipart_post_is_one_byte_over_the_tweet_cap`)
- A chunked POST one byte over the tweet cap returns 413 and no complete request reaches the tweet service (`CreateTweet.should_return_413_and_complete_nothing_when_a_chunked_post_is_one_byte_over_the_tweet_cap`)
- PUT over 8 KB returns 413, nothing forwarded (`EditTweet.should_return_413_and_forward_nothing_when_a_put_body_is_over_8_kb`)
- A chunked PUT over 8 KB returns 413 and no complete request reaches the tweet service (`EditTweet.should_return_413_and_complete_nothing_when_a_chunked_put_body_is_over_8_kb`)
- A body over the picture-upload cap but under the tweet cap still returns 413 on the profile-picture route (`PictureRoutes.should_keep_the_5_mb_cap_on_the_profile_picture_routes_when_the_tweet_cap_is_raised`)

`TweetRoutesIntegrationTest.Failures` — enabled. The test profile lowers the read timeout to 1s.

- Tweet service slower than the read timeout returns 504, no exception text (`should_return_504_without_leaking_exception_text_when_the_tweet_service_is_slower_than_the_read_timeout`)

`TweetRoutesIntegrationTest.UnreachableTweetService` — enabled. Its own context points the route at a closed port.

- Tweet service down returns 502, no exception text (`should_return_502_without_leaking_exception_text_when_the_tweet_service_is_down`)

## `/api/v1/feed` (proxy to the timeline service)

Added in timeline step 11. `TimelineRoutesIntegrationTest` uses WireMock as the timeline service; the test profile lowers the read timeout to 1s. The identity filters are the ones the tweet routes use, so both suites guard the same behaviour.

`TimelineRoutesIntegrationTest.Authentication` - enabled

- No cookie returns 401 and nothing reaches the timeline service (`should_return_401_and_forward_nothing_when_there_is_no_cookie`)
- A cookie that is not a valid JWT returns 401 and nothing is forwarded (`should_return_401_and_forward_nothing_when_the_cookie_is_not_a_valid_jwt`)

`TimelineRoutesIntegrationTest.Forwarding` - enabled

- GET arrives with path, query and status unchanged (`should_forward_a_get_with_the_path_query_and_status_unchanged_when_the_user_is_authenticated`)
- A `400`, `404`, `502` or `504` from the timeline service passes through with its body (`should_pass_the_downstream_error_status_and_body_through_unchanged`, status)
- A path below the feed is not forwarded (`should_not_forward_a_path_below_the_feed`)
- A tweet path is not sent to the timeline service (`should_not_send_the_tweet_routes_to_the_timeline_service`)

`TimelineRoutesIntegrationTest.Identity` - enabled

- The timeline service receives `X-User-Id` = the JWT `sub` (`should_send_the_jwt_subject_as_x_user_id_when_the_request_is_forwarded`)
- A spoofed `X-User-Id` is replaced, in either letter case (`should_replace_a_spoofed_x_user_id_with_the_jwt_subject`, header name)
- A spoofed `X-User-Role` is dropped (`should_drop_a_spoofed_x_user_role_header`)
- `Cookie` and `Authorization` are not forwarded (`should_not_forward_the_cookie_header`, `should_not_forward_the_authorization_header`)
- `X-Internal-Secret` is not sent: the secret guards the gateway's own internal endpoints only (`should_not_send_the_internal_secret_to_the_timeline_service`)

`TimelineRoutesIntegrationTest.Failures` - enabled

- Timeline service slower than the read timeout returns 504, no exception text (`should_return_504_without_leaking_exception_text_when_the_timeline_service_is_slower_than_the_read_timeout`)

`TimelineRoutesIntegrationTest.UnreachableTimelineService` - enabled. Its own context points the route at a closed port.

- Timeline service down returns 502, no exception text (`should_return_502_without_leaking_exception_text_when_the_timeline_service_is_down`)

The auth, profile, file and follow suites are the "Unaffected" group: they must stay green unchanged.

## `/internal/v1/**` (service-to-service)

`InternalUserControllerIntegrationTest` (`controllers/internal`), plus `InternalApiSecretFilterTest` for the filter alone. The timeline service is the caller; the secret is the only guard.

`Secret`

- A missing `X-Internal-Secret` returns 404 on both endpoints (`should_return_404_when_the_secret_header_is_missing`)
- A wrong secret returns 404 on both endpoints (`should_return_404_when_the_secret_is_wrong`)
- The right secret returns 200 on both endpoints (`should_return_200_when_the_secret_is_right`)
- A rejected request gets the error body of an unknown path, 404 "No resource found for this path." (`should_answer_with_the_error_shape_of_an_unknown_path_when_the_secret_is_missing`)
- No login is needed with the right secret (`should_not_ask_for_a_login_when_the_secret_is_right`)
- A garbage `access_token` cookie is ignored with the right secret (`should_ignore_an_invalid_login_cookie_when_the_secret_is_right`)
- `GET /actuator/health` stays open without the secret (`should_leave_a_public_route_open_when_no_secret_is_sent`)
- The secret does not open a non-internal route: `/api/v1/users/{username}` still returns 401 (`should_still_require_a_login_for_a_non_internal_route_when_the_secret_is_sent`)

`FollowerIds` - `GET /internal/v1/users/{id}/follower-ids?cursor=&size=`

- An unknown user returns 200 with `ids: []` and `nextCursor: null` (`should_return_an_empty_page_when_the_user_is_unknown`)
- A user with no followers returns an empty page (`should_return_an_empty_page_when_the_user_has_no_followers`)
- The body holds only `ids` and `nextCursor` (`should_return_only_ids_and_a_cursor_when_the_user_has_followers`)
- Follower ids come newest follow first (`should_return_the_follower_ids_newest_follow_first`)
- Only that user's followers appear, not other users' followers or who the user follows (`should_return_only_the_followers_of_that_user`)
- The default size covers a small list with `nextCursor: null` (`should_return_every_follower_with_no_cursor_when_the_default_size_covers_them_all`)
- A page that exactly fills `size` has `nextCursor: null` (`should_return_no_cursor_when_the_followers_exactly_fill_the_page`)
- Walking the cursor visits every follower exactly once (`should_visit_every_follower_exactly_once_when_walking_the_cursor`)
- The same, with every follow at one timestamp (`should_visit_every_follower_exactly_once_when_every_follow_shares_one_timestamp`)
- `size=1000` returns 200 (`should_return_200_when_the_size_is_1000`)
- `size` 0, -1 or 1001 returns 400 "Page size must be between 1 and 1000." (`should_return_400_when_the_size_is_out_of_range`)
- A non-numeric `size` returns 400 (`should_return_400_when_the_size_is_not_a_number`)
- A bad cursor returns 400 "Invalid cursor." (`should_return_400_when_the_cursor_is_invalid`)
- A non-UUID user id returns 400 (`should_return_400_when_the_user_id_is_not_a_uuid`)

`Users` - `GET /internal/v1/users?ids=a,b`

- Each item is exactly `id`, `username`, `profilePictureUrl` (`/api/v1/files/{id}`) (`should_return_the_id_username_and_picture_url_and_nothing_else`)
- `profilePictureUrl` is `null` without a picture (`should_return_a_null_picture_url_when_the_user_has_no_picture`)
- Unknown ids are omitted (`should_return_only_the_known_users_when_some_ids_are_unknown`)
- No known id returns an empty list (`should_return_an_empty_list_when_no_id_is_known`)
- An unconfirmed user is omitted (`should_leave_out_a_user_who_has_not_confirmed_the_account`)
- A repeated id returns its user once (`should_return_each_user_once_when_an_id_is_repeated`)
- A comma-separated `ids` value is accepted (`should_accept_a_comma_separated_list_when_ids_are_joined`)
- 100 ids return 200 (`should_return_200_when_exactly_100_ids_are_given`)
- 101 ids return 400 "Provide between 1 and 100 user ids." (`should_return_400_when_101_ids_are_given`)
- An empty `ids` returns 400 (`should_return_400_when_the_ids_parameter_is_empty`)
- A missing `ids` returns 400 (`should_return_400_when_the_ids_parameter_is_missing`)
- A non-UUID id returns 400 (`should_return_400_when_an_id_is_not_a_uuid`)

## Cross-service contract

`TweetServiceContractIntegrationTest` — enabled. Real server; the tweet service is built from `../twitter_tweet_service/Dockerfile` on a Docker network with its own Mongo, MinIO and Kafka. Users come from `TestJwts`. The first run builds the image and takes minutes.

- Alice posts text + 1 image: 201 (`should_return_201_when_alice_posts_a_tweet_with_text_and_one_image`)
- Bob reads it: 200, `views = 1`, image byte-identical (`should_return_200_with_one_view_and_the_byte_identical_image_when_bob_reads_alices_tweet`)
- Bob's PUT returns 403 (`should_return_403_when_bob_edits_alices_tweet`)
- Bob's DELETE returns 403 (`should_return_403_when_bob_deletes_alices_tweet`)
- Bob's DELETE with spoofed `X-User-Id: <alice>` still returns 403 (`should_return_403_when_bob_deletes_alices_tweet_with_a_spoofed_x_user_id`)
- Alice edits: 200 (`should_return_200_when_alice_edits_her_tweet`)
- Alice deletes: 204, then GET returns 404 (`should_return_204_then_404_on_read_when_alice_deletes_her_tweet`)
- `tweet.created` and `tweet.deleted` for that `tweetId` reach Kafka (`should_publish_tweet_created_and_tweet_deleted_for_the_tweet_id_when_alice_creates_and_deletes_a_tweet`)
