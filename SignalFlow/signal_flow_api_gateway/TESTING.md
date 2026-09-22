# E2E test catalog

Scope: HTTP-layer tests only — full stack through `DispatcherServlet` via `RestTestClient` against
Testcontainers Postgres. `UserRepositoryIntegrationTest` exercises real Postgres too but skips the HTTP
layer, so it's not listed here.

Hand-maintained — see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

All four endpoints — Register (step 5), Login (step 6), Logout (step 7) and `/me` (step 8) — are enabled.
No `@Disabled` remains anywhere in the suite.

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

## Known gaps

See `PLAN.md`'s "Known gaps" section — accepted risks for the whole auth phase, not specific to these
scenarios.
