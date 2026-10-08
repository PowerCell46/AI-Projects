package com.peter_gerdzhikov.twitter_timeline_service.services.implementations.authortweets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.tweets.TweetPageClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.client.users.UserClientDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.TweetItemResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.DTOs.response.authortweets.AuthorTweetsResponseDTO;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.InvalidPageSizeException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.tweetdetails.AuthorNotFoundException;
import com.peter_gerdzhikov.twitter_timeline_service.exceptions.upstream.UpstreamUnavailableException;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.TweetItemAssemblyService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.TweetLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.services.interfaces.lookups.UserLookupService;
import com.peter_gerdzhikov.twitter_timeline_service.support.TestIds;

@ExtendWith(MockitoExtension.class)
class AuthorTweetsServiceImplTest {

    private static final UUID VIEWER_ID = TestIds.userId();

    private static final UUID AUTHOR_ID = TestIds.userId();

    @Mock
    private UserLookupService userLookupService;

    @Mock
    private TweetLookupService tweetLookupService;

    @Mock
    private TweetItemAssemblyService tweetItemAssemblyService;

    @InjectMocks
    private AuthorTweetsServiceImpl authorTweetsService;

    @Test
    void should_assemble_the_items_for_the_viewer_and_pass_the_cursor_through_when_the_author_exists() {
        UserClientDTO author = UserClientDTO.builder().id(AUTHOR_ID).username("ana").build();
        TweetClientDTO tweet = TweetClientDTO.builder().id(TestIds.tweetId()).authorId(AUTHOR_ID).build();
        TweetItemResponseDTO item = TweetItemResponseDTO.builder().id(tweet.getId()).build();
        when(userLookupService.findByIds(List.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, author));
        when(tweetLookupService.findPageByAuthor(AUTHOR_ID, "in", 20))
                .thenReturn(TweetPageClientDTO.builder().nextCursor("out").items(List.of(tweet)).build());
        when(tweetItemAssemblyService.assembleFetched(VIEWER_ID, List.of(tweet), Map.of(AUTHOR_ID, author)))
                .thenReturn(List.of(item));

        AuthorTweetsResponseDTO page = authorTweetsService.list(VIEWER_ID, AUTHOR_ID, "in", 20);

        assertThat(page.getNextCursor()).isEqualTo("out");
        assertThat(page.getItems()).containsExactly(item);
    }

    @Test
    void should_throw_not_found_and_read_no_tweets_when_the_author_is_unknown() {
        when(userLookupService.findByIds(List.of(AUTHOR_ID))).thenReturn(Map.of());

        assertThatThrownBy(() -> authorTweetsService.list(VIEWER_ID, AUTHOR_ID, null, 20))
                .isInstanceOf(AuthorNotFoundException.class);
        verifyNoInteractions(tweetLookupService, tweetItemAssemblyService);
    }

    @ParameterizedTest
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 101, Integer.MAX_VALUE})
    void should_throw_and_call_nothing_when_the_size_is_outside_1_to_100(int size) {
        assertThatThrownBy(() -> authorTweetsService.list(VIEWER_ID, AUTHOR_ID, null, size))
                .isInstanceOf(InvalidPageSizeException.class);
        verifyNoInteractions(userLookupService, tweetLookupService, tweetItemAssemblyService);
    }

    @Test
    void should_let_a_downstream_failure_through_when_the_tweet_service_is_down() {
        UserClientDTO author = UserClientDTO.builder().id(AUTHOR_ID).username("ana").build();
        when(userLookupService.findByIds(List.of(AUTHOR_ID))).thenReturn(Map.of(AUTHOR_ID, author));
        when(tweetLookupService.findPageByAuthor(AUTHOR_ID, null, 20)).thenThrow(new UpstreamUnavailableException());

        assertThatThrownBy(() -> authorTweetsService.list(VIEWER_ID, AUTHOR_ID, null, 20))
                .isInstanceOf(UpstreamUnavailableException.class);
        verifyNoInteractions(tweetItemAssemblyService);
    }
}
