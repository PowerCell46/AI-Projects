package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.likes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeCountRepository;
import com.peter_gerdzhikov.twitter_timeline_service.repositories.likes.TweetLikeRepository;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class LikeRecordingServiceImplTest {

    private static final UUID USER = TestIds.userId();

    private static final UUID TWEET = TestIds.tweetId();

    private static final UUID AUTHOR = TestIds.userId();

    private static final Instant LIKED_AT = Instant.parse("2026-01-01T00:00:00.123456Z");

    @Mock
    private TweetLikeRepository tweetLikeRepository;

    @Mock
    private TweetLikeCountRepository tweetLikeCountRepository;

    @InjectMocks
    private LikeRecordingServiceImpl likeRecordingService;

    @Nested
    class Like {

        @Test
        void should_bump_the_counter_and_return_true_when_the_like_is_new() {
            when(tweetLikeRepository.insertIfAbsent(USER, TWEET, AUTHOR, LIKED_AT)).thenReturn(1);

            boolean added = likeRecordingService.like(USER, TWEET, AUTHOR, LIKED_AT);

            assertThat(added).isTrue();
            verify(tweetLikeCountRepository).increment(TWEET);
        }

        @Test
        void should_not_touch_the_counter_and_return_false_when_the_tweet_was_already_liked() {
            when(tweetLikeRepository.insertIfAbsent(USER, TWEET, AUTHOR, LIKED_AT)).thenReturn(0);

            boolean added = likeRecordingService.like(USER, TWEET, AUTHOR, LIKED_AT);

            assertThat(added).isFalse();
            verifyNoInteractions(tweetLikeCountRepository);
        }
    }

    @Nested
    class Unlike {

        @Test
        void should_lower_the_counter_and_return_true_when_a_like_was_removed() {
            when(tweetLikeRepository.deleteByUserAndTweet(USER, TWEET)).thenReturn(1);

            boolean removed = likeRecordingService.unlike(USER, TWEET);

            assertThat(removed).isTrue();
            verify(tweetLikeCountRepository).decrement(TWEET);
        }

        @Test
        void should_not_touch_the_counter_and_return_false_when_the_user_had_not_liked_the_tweet() {
            when(tweetLikeRepository.deleteByUserAndTweet(USER, TWEET)).thenReturn(0);

            boolean removed = likeRecordingService.unlike(USER, TWEET);

            assertThat(removed).isFalse();
            verifyNoInteractions(tweetLikeCountRepository);
        }
    }
}
