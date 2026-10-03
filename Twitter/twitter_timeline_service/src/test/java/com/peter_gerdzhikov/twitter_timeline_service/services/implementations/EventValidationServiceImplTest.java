package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetDeletedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.UserUnfollowedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;

import jakarta.validation.Validation;

class EventValidationServiceImplTest {

    private final EventValidationServiceImpl eventValidationService =
            new EventValidationServiceImpl(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void should_accept_a_complete_tweet_created_event() {
        TweetCreatedEventDTO event = new TweetCreatedEventDTO(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThatCode(() -> eventValidationService.validate(event, "tweet.created event")).doesNotThrowAnyException();
    }

    @Test
    void should_name_every_missing_field_and_the_label_when_a_tweet_created_event_is_incomplete() {
        TweetCreatedEventDTO event = new TweetCreatedEventDTO(UUID.randomUUID(), null, null, Instant.now());

        assertThatThrownBy(() -> eventValidationService.validate(event, "tweet.created event 42"))
                .isInstanceOf(InvalidEventException.class)
                .hasMessage("Invalid tweet.created event 42: authorId must not be null, tweetId must not be null.");
    }

    @Test
    void should_reject_a_tweet_deleted_event_without_a_tweet_id() {
        TweetDeletedEventDTO event = new TweetDeletedEventDTO(UUID.randomUUID(), null);

        assertThatThrownBy(() -> eventValidationService.validate(event, "tweet.deleted event"))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("tweetId must not be null");
    }

    @Test
    void should_reject_a_user_unfollowed_event_without_the_time() {
        UserUnfollowedEventDTO event = new UserUnfollowedEventDTO(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null);

        assertThatThrownBy(() -> eventValidationService.validate(event, "user.unfollowed event"))
                .isInstanceOf(InvalidEventException.class)
                .hasMessageContaining("occurredAt must not be null");
    }

    @Test
    void should_not_put_any_field_value_in_the_message() {
        UUID tweetId = UUID.randomUUID();
        TweetCreatedEventDTO event = new TweetCreatedEventDTO(UUID.randomUUID(), tweetId, null, Instant.now());

        assertThatThrownBy(() -> eventValidationService.validate(event, "tweet.created event"))
                .satisfies(exception -> assertThat(exception.getMessage()).doesNotContain(tweetId.toString()));
    }
}
