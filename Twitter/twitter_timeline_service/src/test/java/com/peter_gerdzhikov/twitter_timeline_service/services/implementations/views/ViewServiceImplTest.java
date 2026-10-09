package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.views;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.entities.views.TweetViewCount;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.views.InvalidTweetIdsException;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.views.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.views.ViewRecordingService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class ViewServiceImplTest {

    private static final UUID VIEWER = TestIds.userId();

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private ViewRecordingService viewRecordingService;

    @Mock
    private TweetViewCountRepository tweetViewCountRepository;

    @InjectMocks
    private ViewServiceImpl viewService;

    @Nested
    class Report {

        @Test
        void should_record_only_the_tweets_the_tweet_service_knows() {
            UUID knownTweetId = TestIds.tweetId();
            UUID unknownTweetId = TestIds.tweetId();
            when(tweetLookupService.findByIds(new LinkedHashSet<>(List.of(knownTweetId, unknownTweetId))))
                    .thenReturn(Map.of(knownTweetId, new TweetClientDTO()));

            viewService.report(VIEWER, List.of(knownTweetId, unknownTweetId));

            verify(viewRecordingService).record(VIEWER, Set.of(knownTweetId));
        }

        @Test
        void should_ask_the_tweet_service_for_each_id_once_when_the_batch_repeats_an_id() {
            UUID tweetId = TestIds.tweetId();
            when(tweetLookupService.findByIds(new LinkedHashSet<>(List.of(tweetId)))).thenReturn(Map.of());

            viewService.report(VIEWER, List.of(tweetId, tweetId));

            verify(tweetLookupService).findByIds(new LinkedHashSet<>(List.of(tweetId)));
        }

        @Test
        void should_record_nothing_when_no_id_exists() {
            when(tweetLookupService.findByIds(any())).thenReturn(Map.of());

            viewService.report(VIEWER, List.of(TestIds.tweetId()));

            verifyNoInteractions(viewRecordingService);
        }

        @Test
        void should_throw_the_lookups_own_exception_and_record_nothing_when_the_tweet_service_fails() {
            when(tweetLookupService.findByIds(any())).thenThrow(new UpstreamUnavailableException(new RuntimeException()));

            assertThatThrownBy(() -> viewService.report(VIEWER, List.of(TestIds.tweetId())))
                    .isInstanceOf(UpstreamUnavailableException.class);

            verifyNoInteractions(viewRecordingService);
        }

        @Test
        void should_reject_the_batch_without_any_call_when_it_holds_more_than_50_ids() {
            List<UUID> tooMany = Collections.nCopies(51, TestIds.tweetId());

            assertThatThrownBy(() -> viewService.report(VIEWER, tooMany)).isInstanceOf(InvalidTweetIdsException.class);

            verifyNoInteractions(tweetLookupService, viewRecordingService);
        }
    }

    @Nested
    class GetViews {

        @Test
        void should_return_the_counter_of_a_viewed_tweet_and_zero_for_the_others_in_request_order() {
            UUID viewedTweetId = TestIds.tweetId();
            UUID unviewedTweetId = TestIds.tweetId();
            when(tweetViewCountRepository.findAllById(new LinkedHashSet<>(List.of(unviewedTweetId, viewedTweetId))))
                    .thenReturn(List.of(count(viewedTweetId, 7)));

            Map<UUID, Long> views = viewService.getViews(List.of(unviewedTweetId, viewedTweetId));

            assertThat(views).containsExactly(Map.entry(unviewedTweetId, 0L), Map.entry(viewedTweetId, 7L));
        }

        @Test
        void should_return_one_entry_and_look_the_id_up_once_when_an_id_is_repeated() {
            UUID tweetId = TestIds.tweetId();
            when(tweetViewCountRepository.findAllById(new LinkedHashSet<>(List.of(tweetId)))).thenReturn(List.of());

            Map<UUID, Long> views = viewService.getViews(List.of(tweetId, tweetId));

            assertThat(views).containsExactly(Map.entry(tweetId, 0L));
        }

        @Test
        void should_make_no_downstream_call() {
            when(tweetViewCountRepository.findAllById(any())).thenReturn(List.of());

            viewService.getViews(List.of(TestIds.tweetId()));

            verifyNoInteractions(tweetLookupService, viewRecordingService);
        }

        @Test
        void should_reject_the_request_without_a_query_when_it_holds_more_than_100_ids() {
            List<UUID> tooMany = IntStream
                    .range(0, 101)
                    .mapToObj(i -> TestIds.tweetId())
                    .toList();

            assertThatThrownBy(() -> viewService.getViews(tooMany)).isInstanceOf(InvalidTweetIdsException.class);

            verifyNoInteractions(tweetViewCountRepository);
        }
    }

    @Nested
    class CountViews {

        @Test
        void should_return_the_counters_without_a_size_check_when_the_ids_are_a_page() {
            List<UUID> page = IntStream
                    .range(0, 100)
                    .mapToObj(i -> TestIds.tweetId())
                    .toList();
            when(tweetViewCountRepository.findAllById(new LinkedHashSet<>(page))).thenReturn(List.of(count(page.getFirst(), 3)));

            Map<UUID, Long> views = viewService.countViews(page);

            assertThat(views).hasSize(100);
            assertThat(views.get(page.getFirst())).isEqualTo(3L);
        }
    }

    private TweetViewCount count(UUID tweetId, long views) {
        return TweetViewCount
                .builder()
                .tweetId(tweetId)
                .views(views)
                .build();
    }
}
