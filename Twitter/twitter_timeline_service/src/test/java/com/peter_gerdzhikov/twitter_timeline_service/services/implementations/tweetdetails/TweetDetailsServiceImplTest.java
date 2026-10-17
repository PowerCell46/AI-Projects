package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.tweetdetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.users.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.TweetNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class TweetDetailsServiceImplTest {

    private static final UUID VIEWER = TestIds.userId();

    private static final UUID TWEET_ID = TestIds.tweetId();

    private static final UUID AUTHOR_ID = TestIds.userId();

    private TweetDetailsServiceImpl tweetDetailsService;

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private TweetItemAssemblyService tweetItemAssemblyService;

    @BeforeEach
    void setUp() {
        tweetDetailsService = new TweetDetailsServiceImpl(userLookupService, tweetLookupService, tweetItemAssemblyService);
    }

    @Nested
    class Get {

        @Test
        void should_assemble_the_tweet_with_its_author_for_the_viewer_when_both_exist() {
            TweetClientDTO tweet = TweetClientDTO
                    .builder()
                    .id(TWEET_ID)
                    .authorId(AUTHOR_ID)
                    .build();
            UserClientDTO author = UserClientDTO
                    .builder()
                    .id(AUTHOR_ID)
                    .username("ana")
                    .build();
            TweetItemResponseDTO item = TweetItemResponseDTO.builder().build();
            when(tweetLookupService.findByIds(List.of(TWEET_ID))).thenReturn(Map.of(TWEET_ID, tweet));
            when(userLookupService.findByIds(List.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, author));
            when(tweetItemAssemblyService.assembleFetched(VIEWER, List.of(tweet), Map.of(AUTHOR_ID, author)))
                    .thenReturn(List.of(item));

            assertThat(tweetDetailsService.get(VIEWER, TWEET_ID)).isSameAs(item);
        }

        @Test
        void should_throw_not_found_and_look_up_no_author_when_the_tweet_is_gone() {
            when(tweetLookupService.findByIds(List.of(TWEET_ID))).thenReturn(Map.of());

            assertThatThrownBy(() -> tweetDetailsService.get(VIEWER, TWEET_ID))
                    .isInstanceOf(TweetNotFoundException.class);

            verifyNoInteractions(userLookupService, tweetItemAssemblyService);
        }

        @Test
        void should_throw_author_not_found_and_assemble_nothing_when_the_author_is_gone() {
            TweetClientDTO tweet = TweetClientDTO
                    .builder()
                    .id(TWEET_ID)
                    .authorId(AUTHOR_ID)
                    .build();
            when(tweetLookupService.findByIds(List.of(TWEET_ID))).thenReturn(Map.of(TWEET_ID, tweet));
            when(userLookupService.findByIds(List.of(AUTHOR_ID))).thenReturn(Map.of());

            assertThatThrownBy(() -> tweetDetailsService.get(VIEWER, TWEET_ID))
                    .isInstanceOf(AuthorNotFoundException.class);

            verifyNoInteractions(tweetItemAssemblyService);
        }

        @Test
        void should_let_an_unavailable_tweet_service_through_and_look_up_no_author() {
            when(tweetLookupService.findByIds(List.of(TWEET_ID))).thenThrow(new UpstreamUnavailableException());

            assertThatThrownBy(() -> tweetDetailsService.get(VIEWER, TWEET_ID))
                    .isInstanceOf(UpstreamUnavailableException.class);

            verifyNoInteractions(userLookupService);
        }
    }
}
