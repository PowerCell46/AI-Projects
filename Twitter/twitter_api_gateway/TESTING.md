# E2E test catalog

Scope: HTTP-layer tests only — full stack through `DispatcherServlet` against Testcontainers Postgres and
Kafka (and MinIO from phase 2). Repository, unit and concurrency tests exercise real Postgres too but skip the
HTTP layer, so they're not listed here.

Hand-maintained — see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

Each phase's scenarios are written `@Disabled` first and approved before any feature code; each feature step
enables its group (see `PLAN.md`).

Phase 1 catalog: written `@Disabled` in step 2. All groups are enabled (steps 4–8); nothing in the catalog is `@Disabled`.

Phase 2 catalog (profile and files, at the bottom): written `@Disabled` in step 12 and approved. Bodies are empty until the step that enables the group; the profile read and edit groups are enabled (step 14), the upload and delete groups (step 16), the files group (step 17), and the concurrency test (step 18).

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

- Returns 200 with `{id, username, bio, location, birthdate, profilePictureUrl, coverPictureUrl, createdAt}` (`should_return_200_with_the_profile_shape_when_the_user_exists`)
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
