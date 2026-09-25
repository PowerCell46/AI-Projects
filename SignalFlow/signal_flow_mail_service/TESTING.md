# E2E test catalog

Scope: the Kafka-to-SMTP pipeline only - `TopicNewsNotificationRequestedListenerIntegrationTest` (real
Kafka, Redis and Mailpit containers, a spied `JavaMailSender`) and `SmtpUnreachableIntegrationTest` (its
own context, SMTP pointed at a closed port). `NotificationInboxServiceIntegrationTest` exercises real Redis
too but skips Kafka and the listener, so it's not listed here - same for
`RedisAuthenticationIntegrationTest`, which only proves a wrong `spring.data.redis.password` fails loudly
on first use.

Hand-maintained - see `CLAUDE.md`'s "Writing code" section for the rule keeping this in sync.

## `topic-news.notification-requested` consumption

`TopicNewsNotificationRequestedListenerIntegrationTest`

- Happy path sends one email with the escaped topic/category, raw `data` markup, formatted dates, correct
  from address, and marks the Redis key `SENT` (`should_send_one_email_on_happy_path`)
- The sent email carries a `text/plain` alternative alongside the `text/html` part, `data` converted to
  plain text with the link URL kept (`should_send_a_text_part_with_the_data_converted_to_plain_text_including_the_link_url`)
- Exact redelivery (same record published twice) sends exactly one email
  (`should_send_only_one_email_on_exact_redelivery`)
- The same news to two different users sends two emails and marks two independent `SENT` keys
  (`should_send_two_emails_for_the_same_news_to_two_users`)
- A pre-existing `SENT` key skips sending entirely, key untouched
  (`should_skip_sending_when_the_sent_key_already_exists`)
- A claim held by another consumer with a long (60s) TTL dead-letters after exhausting retries, sends no
  email, and leaves the held claim untouched - it's never released, since it was never ours to release
  (`should_dead_letter_and_leave_a_long_held_claim_untouched`)
- A claim held with a short (150ms) TTL is claimed on a later retry once it expires, sending exactly one
  email and marking the key `SENT` (`should_send_one_email_after_a_short_held_claim_expires`)
- Malformed JSON dead-letters without sending or touching the Redis inbox
  (`should_dead_letter_malformed_json_without_sending`)
- A blank `emailAddress` fails validation before the inbox is touched, dead-letters, sends nothing
  (`should_dead_letter_a_blank_email_address_without_sending`)
- A missing `newsId` fails validation the same way (`should_dead_letter_a_missing_news_id_without_sending`)
- `data` one char over its 65,536-char cap fails validation the same way - rejected whole, never truncated
  (`should_dead_letter_an_oversized_data_field_without_sending`)
- A recipient outside Mailpit's `--smtp-allowed-recipients` gets a real `550`, classified permanent -
  exactly one send attempt (no retries), dead-lettered, and the claim released
  (`should_dead_letter_after_exactly_one_permanent_smtp_rejection`)
- `<img src=x onerror=...>` in `topicName` arrives HTML-escaped in the sent email, never as live markup
  (`should_escape_html_injection_in_topic_name`)

## SMTP unreachable

`SmtpUnreachableIntegrationTest` - its own Kafka topic, consumer group and Spring context, `spring.mail.port`
pointed at a closed port

- A transient connect failure retries the configured number of times (1 initial attempt + the test
  profile's 3 retries = 4 sends), then dead-letters, and releases the claim
  (`should_dead_letter_after_exhausting_retries_when_smtp_is_unreachable`)

## DLT replay

`DltReplayIntegrationTest` - its own topic names and consumer groups (`DECISIONS.md`), `publishToDlt`
crafts a record directly on the DLT topic with the `kafka_dlt-exception-cause-fqcn` header a real failure
would have left, so each scenario is deterministic without re-triggering that failure through the whole
pipeline

- A non-permanent-failure record (e.g. `TransientMailDeliveryException`) is replayed and sent
  (`should_send_an_email_after_replaying_a_transient_failure_record`)
- An `InvalidNotificationEventException` record is skipped by default, no email sent
  (`should_not_send_an_email_when_replaying_an_invalid_event_record_with_default_settings`)
- The same kind of permanent-failure record is replayed and sent when a
  `DltReplayServiceImpl` is run with `include-permanent=true`
  (`should_send_an_email_when_replaying_a_permanent_failure_record_with_include_permanent_true`)
- Replaying a dead-lettered duplicate of an already-sent `(newsId, userId)` produces no second email - the
  existing `SENT` Redis key dedupes it same as any other redelivery
  (`should_not_send_a_second_email_for_a_record_whose_key_is_already_sent`)

`DltReplayServiceImplTest` (unit, mocked `Consumer`/`Producer` - see `DECISIONS.md`) covers the
offset-bounding and header-stripping rules that aren't practical to reproduce deterministically over a
real broker: a record at or beyond the offset snapshot taken at the start of a run is left unprocessed for
the next run, and `kafka_dlt-*` headers are stripped from the republished record while other headers
(e.g. `__TypeId__`) are kept.
