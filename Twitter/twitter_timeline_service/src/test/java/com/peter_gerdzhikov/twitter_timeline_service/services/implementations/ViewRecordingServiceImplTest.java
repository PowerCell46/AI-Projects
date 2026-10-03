package com.peter_gerdzhikov.twitter_timeline_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.TweetViewRepository;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class ViewRecordingServiceImplTest {

    private static final UUID VIEWER = TestIds.userId();

    @Mock
    private TweetViewRepository tweetViewRepository;

    @Mock
    private TweetViewCountRepository tweetViewCountRepository;

    @InjectMocks
    private ViewRecordingServiceImpl viewRecordingService;

    @Test
    void should_count_only_the_tweets_that_were_new_to_the_viewer() {
        UUID seenTweetId = TestIds.tweetId();
        UUID newTweetId = TestIds.tweetId();
        when(tweetViewRepository.insertIfAbsent(VIEWER, new UUID[]{seenTweetId, newTweetId})).thenReturn(List.of(newTweetId));

        int recorded = viewRecordingService.record(VIEWER, List.of(seenTweetId, newTweetId));

        assertThat(recorded).isEqualTo(1);
        verify(tweetViewCountRepository).incrementAll(new UUID[]{newTweetId});
    }

    @Test
    void should_not_touch_the_counters_when_every_tweet_was_seen_before() {
        UUID tweetId = TestIds.tweetId();
        when(tweetViewRepository.insertIfAbsent(any(), any())).thenReturn(List.of());

        int recorded = viewRecordingService.record(VIEWER, List.of(tweetId));

        assertThat(recorded).isZero();
        verifyNoInteractions(tweetViewCountRepository);
    }
}
