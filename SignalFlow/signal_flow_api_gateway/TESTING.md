# E2E test catalog

Scope: HTTP-layer tests only — full stack through `DispatcherServlet` via `RestTestClient` against
Testcontainers Postgres. `UserRepositoryIntegrationTest` and `SubscriptionRepositoryIntegrationTest`
exercise real Postgres too but skip the HTTP layer, so they're not listed here.

Hand-maintained — see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

All seven endpoints are enabled — the four auth ones (Register, Login, Logout, `/me`) and the three
subscription ones. No `@Disabled` remains anywhere in the suite.

## `POST /api/v1/auth/register`

`AuthControllerIntegrationTest.Register`

- Creates a user and returns it (`should_create_a_user_and_return_it_with_a_cookie`)
- Sets the cookie as HttpOnly/SameSite=Strict/Path=/ (`should_set_the_cookie_as_http_only_same_site_strict_and_path_root`)
- Stored password is not the plaintext (`should_not_store_the_password_as_plaintext`)
- Email is stored lowercased (`should_store_the_email_lowercased`)
- Duplicate email returns 409 (`should_return_409_for_a_duplicate_email`)
- Duplicate email differing only in case returns 409 (`should_return_409_for_a_duplicate_email_differing_only_in_case`)
- Invalid email returns 400 naming the field (`should_return_400_naming_the_field_for_an_invalid_email`)
- Password under 8 characters returns 400 (`should_return_400_for_a_password_under_8_characters`)
- Password over 72 characters returns 400 (`should_return_400_for_a_password_over_72_characters`)
- Password without an uppercase letter returns 400 (`should_return_400_for_a_password_without_an_uppercase_letter`)
- Password without a lowercase letter returns 400 (`should_return_400_for_a_password_without_a_lowercase_letter`)
- Password without a digit returns 400 (`should_return_400_for_a_password_without_a_digit`)
- Email without a top-level domain returns 400 (`should_return_400_for_an_email_without_a_top_level_domain`) —
  `@Email` alone accepts `user@localhost`; the stricter `@Pattern` on top catches it
- Blank fields return 400 (`should_return_400_for_blank_fields`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- A `role` in the request body is ignored — user is still `USER` (`should_ignore_a_role_sent_in_the_request_body`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)
- A body over `app.request.max-body-bytes` returns 413 and creates no user (`should_return_413_for_a_body_over_the_size_cap`) —
  the cap is gateway-wide (`RequestBodySizeLimitFilter`), exercised here because register is the largest
  anonymous body

## `POST /api/v1/auth/login`

`AuthControllerIntegrationTest.Login`

- Valid credentials return 200 and a cookie (`should_return_200_and_a_cookie_for_valid_credentials`)
- Wrong password returns a generic 401 (`should_return_a_generic_401_for_wrong_password`)
- Unknown email returns an identical generic 401 (`should_return_an_identical_generic_401_for_an_unknown_email`)
- Disabled user returns an identical generic 401 (`should_return_an_identical_generic_401_for_a_disabled_user`)
- Email match is case-insensitive (`should_match_the_email_case_insensitively`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- Password under 8 characters returns 400 (`should_return_400_for_a_password_under_8_characters`)
- Password over 72 characters returns 400 (`should_return_400_for_a_password_over_72_characters`)
- Password without an uppercase letter returns 400 (`should_return_400_for_a_password_without_an_uppercase_letter`)
- Password without a lowercase letter returns 400 (`should_return_400_for_a_password_without_a_lowercase_letter`)
- Password without a digit returns 400 (`should_return_400_for_a_password_without_a_digit`)
- Email without a top-level domain returns 400 (`should_return_400_for_an_email_without_a_top_level_domain`)

## `POST /api/v1/auth/logout`

`AuthControllerIntegrationTest.Logout`

- Returns 204 and expires the cookie (`should_return_204_and_expire_the_cookie`)
- Returns 204 with no cookie present (`should_return_204_with_no_cookie_present`)
- A browser that dropped the cleared cookie can no longer authenticate `/me` (`should_prevent_a_browser_that_dropped_the_cleared_cookie_from_authenticating_me`) —
  exercises the browser-cookie-jar path only; the JWT itself is not server-side revoked (see `PLAN.md`'s
  Known gaps)

## `GET /api/v1/auth/me`

`AuthControllerIntegrationTest.Me`

- Returns the caller's id/email/role/createdAt (`should_return_the_callers_id_email_role_and_created_at`)
- No cookie returns 401 (`should_return_401_with_no_cookie`)
- A garbage cookie returns 401 (`should_return_401_with_a_garbage_cookie`)
- A token signed with a different secret returns 401 (`should_return_401_with_a_token_signed_by_a_different_secret`)
- An expired token returns 401 (`should_return_401_with_an_expired_token`)
- The response body never carries the password (`should_never_return_the_password`)

## `GET /api/v1/subscriptions`

`SubscriptionControllerIntegrationTest.ListSubscriptions`

- Returns the caller's subscriptions, newest first (`should_return_the_callers_subscriptions_newest_first`)
- No subscriptions returns an empty list, not 404 (`should_return_an_empty_list_when_the_caller_has_no_subscriptions`)
- Another user's subscriptions are never returned (`should_not_return_another_users_subscriptions`)
- No cookie returns 401 (`should_return_401_with_no_cookie`)

## `POST /api/v1/subscriptions`

`SubscriptionControllerIntegrationTest.Subscribe`

- Creates a subscription and returns it (`should_create_a_subscription_and_return_it`)
- Attaches it to the authenticated caller, never to a body- or path-supplied user (`should_attach_the_subscription_to_the_authenticated_caller`)
- Two users may subscribe to the same topic (`should_allow_two_users_to_subscribe_to_the_same_topic`)
- Subscribing twice returns 409 (`should_return_409_when_already_subscribed`)
- Exceeding the per-user cap returns 409 (`should_return_409_when_the_subscription_limit_is_reached`) —
  the cap is `app.subscriptions.max-per-user`, lowered in the test profile so the scenario fills it in a
  handful of requests
- A missing topic id returns 400 (`should_return_400_for_a_missing_interest_topic_id`)
- Malformed JSON returns 400 (`should_return_400_for_malformed_json`)
- No cookie returns 401 (`should_return_401_with_no_cookie`)
- Error body leaks no exception or package name (`should_not_leak_exception_or_package_names_in_the_error_body`)

## `DELETE /api/v1/subscriptions/{interestTopicId}`

`SubscriptionControllerIntegrationTest.Unsubscribe`

- Returns 204 and removes the row (`should_return_204_and_remove_the_subscription`)
- Unsubscribing from a topic you never subscribed to returns 404 (`should_return_404_when_not_subscribed`)
- Another user's subscription returns 404 and is not deleted (`should_return_404_when_the_subscription_belongs_to_another_user`)
- A malformed topic id returns 400 without naming the target type (`should_return_400_for_a_malformed_interest_topic_id`)
- No cookie returns 401 (`should_return_401_with_no_cookie`)

## Known gaps

See `PLAN.md`'s "Known gaps" section — accepted risks for the whole auth phase, not specific to these
scenarios.
