# E2E test catalog

Last updated: 2026-10-09

Scope: the Kafka-to-SMTP pipeline only - `UserConfirmationRequestedListenerIntegrationTest` and
`UserFollowedListenerIntegrationTest` (real Kafka, Redis and Mailpit containers, a spied `JavaMailSender`) and
`SmtpUnreachableIntegrationTest` (its own context, SMTP pointed at a closed port). Service-level tests that use real Redis but skip Kafka and the listener are not
listed here.

Hand-maintained - see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

## `user.confirmation-requested` consumption

`UserConfirmationRequestedListenerIntegrationTest`

Happy path

- One email goes to `email` from `MAIL_FROM` with the subject "Confirm your email"
  (`should_send_one_email_from_the_configured_sender_with_the_confirmation_subject`)
- The HTML part has the button `href` and the visible URL equal to `confirmationUrl`, the username, the expiry
  formatted in `MAIL_ZONE` and the "If you didn't sign up, ignore this email." line
  (`should_render_the_html_part_with_the_link_username_expiry_and_ignore_line`)
- The text part carries the same content (`should_render_the_text_part_with_the_same_content_as_the_html_part`)
- The Redis key `mail:confirmation:<eventId>` is `SENT`, with a TTL within 7d
  (`should_mark_the_redis_key_sent_with_a_ttl_within_the_configured_window`)

Dedupe

- The same record published twice sends exactly one email
  (`should_send_only_one_email_when_the_same_record_is_published_twice`)
- Two events for the same user with different `eventId`s (a resend) send two emails
  (`should_send_two_emails_when_two_events_for_the_same_user_have_different_event_ids`)
- A key that is already `SENT` sends nothing, asserted with a same-key sentinel record
  (`should_send_nothing_when_the_redis_key_is_already_sent`)

Stale

- `expiresAt` equal to the clock sends nothing and leaves no Redis key, asserted with a sentinel
  (`should_send_nothing_and_leave_no_redis_key_when_expires_at_equals_the_clock`)
- `expiresAt` one second after the clock sends the email
  (`should_send_the_email_when_expires_at_is_one_second_after_the_clock`)

Claims

- A claim held with a long (60s) TTL dead-letters after the retries (1 attempt + 3 retries + the sentinel = 5
  `process` calls), sends nothing and leaves the claim untouched - it was never ours to release (`should_dead_letter_and_leave_a_long_held_claim_untouched`)
- A claim held with a short (150ms) TTL is taken on a later retry and sends exactly one email
  (`should_send_one_email_after_a_short_held_claim_expires`)

Invalid - every case below dead-letters **without retries** and leaves the inbox untouched. "Without retries" is
asserted by counting `process` calls on a spy of the notification service (the bad record's one call plus the
sentinel's); the only email sent is the sentinel's

- Malformed JSON (`should_dead_letter_malformed_json_without_sending_or_touching_the_inbox`)
- A missing `eventId` (`should_dead_letter_a_missing_event_id_without_retries`)
- A blank `email` (`should_dead_letter_a_blank_email_without_retries`)
- A username outside `^[A-Za-z0-9_]{3,15}$` (`should_dead_letter_a_username_outside_the_pattern_without_retries`)
- A missing `expiresAt` (`should_dead_letter_a_missing_expires_at_without_retries`)
- A `confirmationUrl` of 2049 characters (`should_dead_letter_a_2049_character_confirmation_url_without_retries`)

Permanent SMTP failure

- A recipient outside `@example.com` gets a real `550` from Mailpit: exactly 1 send attempt, the record
  dead-letters and the claim is released
  (`should_dead_letter_after_exactly_one_attempt_when_the_recipient_gets_a_permanent_smtp_rejection`)

## `user.followed` consumption

`UserFollowedListenerIntegrationTest` - the Kafka key is `followeeId`; the Redis key is
`mail:followed:<followerId>:<followeeId>`. The sentinel is a valid event from another follower of the same
followee, under the same Kafka key.

Happy path

- One email goes to `followeeEmail` with the subject "ana followed you"
  (`should_send_one_email_to_the_followee_with_the_follower_in_the_subject`)
- The HTML part has the greeting, the follower line and the button `href` equal to `APP_BASE_URL/feed`
  (`should_render_the_html_part_with_the_greeting_follower_line_and_feed_link`)
- The text part carries the same content (`should_render_the_text_part_with_the_same_content_as_the_html_part`)
- The pair key is `SENT`, with a TTL within 24h
  (`should_mark_the_pair_key_sent_with_a_ttl_within_the_follow_window`)

Window

- The same record published twice sends exactly one email
  (`should_send_only_one_email_when_the_same_record_is_published_twice`)
- A re-follow (new `eventId`, same pair) inside the window sends one email in total; the sentinel is another
  follower of the same followee (same Kafka key)
  (`should_send_one_email_in_total_when_the_same_pair_follows_again_inside_the_window`)
- Two followers of the same followee send two emails
  (`should_send_two_emails_when_two_followers_follow_the_same_followee`)
- One follower following two followees sends two emails
  (`should_send_two_emails_when_one_follower_follows_two_followees`)

Invalid - every case below dead-letters **without retries** and sends nothing

- Malformed JSON (`should_dead_letter_malformed_json_without_sending_or_touching_the_inbox`)
- A missing `followerId` (`should_dead_letter_a_missing_follower_id_without_retries`)
- A blank `followeeEmail` (`should_dead_letter_a_blank_followee_email_without_retries`)
- A `followerUsername` outside `^[A-Za-z0-9_]{3,15}$`
  (`should_dead_letter_a_follower_username_outside_the_pattern_without_retries`)
- A `followeeUsername` outside `^[A-Za-z0-9_]{3,15}$`
  (`should_dead_letter_a_followee_username_outside_the_pattern_without_retries`)

Permanent SMTP failure

- A recipient outside `@example.com` gets a real `550`: exactly 1 send attempt, the record dead-letters and the
  claim is released
  (`should_dead_letter_after_exactly_one_attempt_and_release_the_claim_when_the_recipient_gets_a_permanent_smtp_rejection`)

## SMTP unreachable

`SmtpUnreachableIntegrationTest` - its own Kafka topic, consumer group and Spring context, `spring.mail.port`
pointed at a closed port

- A transient connect failure retries the configured number of times (1 initial attempt + the test profile's
  3 retries = 4 sends), then dead-letters, and releases the claim
  (`should_dead_letter_and_release_the_claim_after_exhausting_retries_when_smtp_is_unreachable`)
