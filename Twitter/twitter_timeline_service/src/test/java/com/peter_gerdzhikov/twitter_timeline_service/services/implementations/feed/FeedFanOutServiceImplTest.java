package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.event.TweetCreatedEventDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.events.InvalidEventException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.feed.FeedEntryRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.EventValidationService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.FollowerLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class FeedFanOutServiceImplTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00.123456789Z");

    private static final Instant CREATED_AT_IN_MICROS = Instant.parse("2026-01-01T00:00:00.123456Z");

    private static final Instant NOW = CREATED_AT.plusSeconds(60);

    private static final Duration RETENTION = Duration.ofDays(7);

    private FeedFanOutServiceImpl feedFanOutService;

    private TweetCreatedEventDTO event;

    @Mock
    private FeedEntryRepository feedEntryRepository;

    @Mock
    private FollowerLookupService followerLookupService;

    @Mock
    private EventValidationService eventValidationService;

    @BeforeEach
    void setUp() {
        feedFanOutService = newService(RETENTION);
        event = TweetCreatedEventDTO
                .builder()
                .eventId(UUID.randomUUID())
                .tweetId(TestIds.tweetId())
                .authorId(TestIds.userId())
                .createdAt(CREATED_AT)
                .build();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, -604_800})
    void should_refuse_to_start_when_the_retention_is_not_positive(long retentionSeconds) {
        Duration retention = Duration.ofSeconds(retentionSeconds);

        assertThatThrownBy(() -> newService(retention))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.feed.retention must be positive");
    }

    @Test
    void should_skip_the_lookup_and_the_inserts_when_the_tweet_is_older_than_the_retention() {
        event.setCreatedAt(NOW.minus(RETENTION).minusSeconds(1));

        feedFanOutService.fanOut(event);

        verify(eventValidationService).validate(eq(event), any());
        verifyNoInteractions(feedEntryRepository, followerLookupService);
    }

    @Test
    void should_fan_out_when_the_tweet_is_exactly_as_old_as_the_retention() {
        event.setCreatedAt(NOW.minus(RETENTION));

        feedFanOutService.fanOut(event);

        verify(feedEntryRepository).insertIfAbsent(any(), eq(event.getTweetId()), eq(event.getAuthorId()), any());
    }

    @Test
    void should_still_reject_an_invalid_event_when_the_tweet_is_older_than_the_retention() {
        event.setCreatedAt(NOW.minus(RETENTION).minusSeconds(1));
        InvalidEventException invalid = new InvalidEventException("Invalid tweet.created event.");
        doThrow(invalid).when(eventValidationService).validate(eq(event), any());

        assertThatThrownBy(() -> feedFanOutService.fanOut(event)).isSameAs(invalid);
    }

    @Test
    void should_add_the_tweet_to_the_author_first_and_then_to_each_follower_page() {
        List<UUID> firstPage = List.of(TestIds.userId(), TestIds.userId());
        List<UUID> secondPage = List.of(TestIds.userId());
        givenFollowerPages(firstPage, secondPage);

        feedFanOutService.fanOut(event);

        InOrder order = inOrder(feedEntryRepository);
        order.verify(feedEntryRepository).insertIfAbsent(
                new UUID[]{event.getAuthorId()}, event.getTweetId(), event.getAuthorId(), CREATED_AT_IN_MICROS);
        order.verify(feedEntryRepository).insertIfAbsent(
                firstPage.toArray(UUID[]::new), event.getTweetId(), event.getAuthorId(), CREATED_AT_IN_MICROS);
        order.verify(feedEntryRepository).insertIfAbsent(
                secondPage.toArray(UUID[]::new), event.getTweetId(), event.getAuthorId(), CREATED_AT_IN_MICROS);
    }

    @Test
    void should_cut_the_tweet_time_to_microseconds_when_it_has_more_precision() {
        feedFanOutService.fanOut(event);

        ArgumentCaptor<Instant> stored = ArgumentCaptor.forClass(Instant.class);
        verify(feedEntryRepository).insertIfAbsent(any(), eq(event.getTweetId()), eq(event.getAuthorId()), stored.capture());
        assertThat(stored.getValue()).isEqualTo(CREATED_AT_IN_MICROS);
    }

    @Test
    void should_add_only_the_authors_entry_when_there_are_no_followers() {
        feedFanOutService.fanOut(event);

        verify(feedEntryRepository).insertIfAbsent(
                new UUID[]{event.getAuthorId()}, event.getTweetId(), event.getAuthorId(), CREATED_AT_IN_MICROS);
    }

    @Test
    void should_validate_the_event_before_anything_else() {
        InvalidEventException invalid = new InvalidEventException("Invalid tweet.created event.");
        doThrow(invalid).when(eventValidationService).validate(eq(event), any());

        assertThatThrownBy(() -> feedFanOutService.fanOut(event)).isSameAs(invalid);

        verifyNoInteractions(feedEntryRepository, followerLookupService);
    }

    @Test
    void should_label_the_validation_with_the_ids_and_never_the_text() {
        feedFanOutService.fanOut(event);

        ArgumentCaptor<String> label = ArgumentCaptor.forClass(String.class);
        verify(eventValidationService).validate(eq(event), label.capture());
        assertThat(label.getValue()).contains(event.getEventId().toString(), event.getTweetId().toString());
    }

    @Test
    void should_let_a_gateway_failure_escape_after_the_authors_entry_was_added() {
        UpstreamUnavailableException failure = new UpstreamUnavailableException(new IllegalStateException("down"));
        doThrow(failure).when(followerLookupService).forEachFollowerPage(eq(event.getAuthorId()), any());

        assertThatThrownBy(() -> feedFanOutService.fanOut(event)).isSameAs(failure);

        verify(feedEntryRepository).insertIfAbsent(
                new UUID[]{event.getAuthorId()}, event.getTweetId(), event.getAuthorId(), CREATED_AT_IN_MICROS);
    }

    @Test
    void should_let_a_repository_failure_escape_when_a_follower_page_cannot_be_written() {
        List<UUID> page = List.of(TestIds.userId());
        givenFollowerPages(page);
        IllegalStateException failure = new IllegalStateException("insert failed");
        when(feedEntryRepository.insertIfAbsent(any(), any(), any(), any())).thenAnswer(invocation -> {
            UUID[] userIds = invocation.getArgument(0);
            if (userIds[0].equals(page.getFirst())) {
                throw failure;
            }

            return 1;
        });

        assertThatThrownBy(() -> feedFanOutService.fanOut(event)).isSameAs(failure);
    }

    private FeedFanOutServiceImpl newService(Duration retention) {
        return new FeedFanOutServiceImpl(
                Clock.fixed(NOW, ZoneOffset.UTC), feedEntryRepository, followerLookupService, eventValidationService, retention);
    }

    @SafeVarargs
    private void givenFollowerPages(List<UUID>... pages) {
        doAnswer(invocation -> {
            Consumer<List<UUID>> consumer = invocation.getArgument(1);
            for (List<UUID> page : pages) {
                consumer.accept(page);
            }

            return null;
        }).when(followerLookupService).forEachFollowerPage(eq(event.getAuthorId()), any());
    }
}
