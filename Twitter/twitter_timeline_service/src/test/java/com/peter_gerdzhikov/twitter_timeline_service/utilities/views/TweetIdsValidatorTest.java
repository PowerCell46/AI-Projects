package com.peter_gerdzhikov.twitter_timeline_service.utilities.views;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.twitter_timeline_service.exceptions.views.InvalidTweetIdsException;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

class TweetIdsValidatorTest {

    private static final int MAX_SIZE = 50;

    @Test
    void should_accept_one_id() {
        assertThatCode(() -> TweetIdsValidator.validate(List.of(TestIds.tweetId()), MAX_SIZE)).doesNotThrowAnyException();
    }

    @Test
    void should_accept_exactly_the_maximum() {
        assertThatCode(() -> TweetIdsValidator.validate(ids(MAX_SIZE), MAX_SIZE)).doesNotThrowAnyException();
    }

    @Test
    void should_count_repeated_ids_as_sent() {
        UUID tweetId = TestIds.tweetId();
        List<UUID> repeated = Collections.nCopies(MAX_SIZE + 1, tweetId);

        assertThatThrownBy(() -> TweetIdsValidator.validate(repeated, MAX_SIZE)).isInstanceOf(InvalidTweetIdsException.class);
    }

    @Test
    void should_reject_a_missing_list() {
        assertThatThrownBy(() -> TweetIdsValidator.validate(null, MAX_SIZE))
                .isInstanceOf(InvalidTweetIdsException.class)
                .hasMessage("Between 1 and 50 tweet ids are required, none of them null.");
    }

    @Test
    void should_reject_an_empty_list() {
        assertThatThrownBy(() -> TweetIdsValidator.validate(List.of(), MAX_SIZE)).isInstanceOf(InvalidTweetIdsException.class);
    }

    @Test
    void should_reject_a_list_over_the_maximum() {
        assertThatThrownBy(() -> TweetIdsValidator.validate(ids(MAX_SIZE + 1), MAX_SIZE))
                .isInstanceOf(InvalidTweetIdsException.class);
    }

    @Test
    void should_reject_a_list_holding_a_null() {
        List<UUID> withNull = new ArrayList<>(ids(2));
        withNull.add(null);

        assertThatThrownBy(() -> TweetIdsValidator.validate(withNull, MAX_SIZE)).isInstanceOf(InvalidTweetIdsException.class);
    }

    private List<UUID> ids(int count) {
        return IntStream
                .range(0, count)
                .mapToObj(i -> TestIds.tweetId())
                .toList();
    }
}
