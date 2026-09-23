package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.FeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.FeedTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.InterestTopicFeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.FeedFilter;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.InterestTopicFeedMode;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.exceptions.InterestTopicFeedUnavailableException;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InterestTopicLookupService;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionService;

@ExtendWith(MockitoExtension.class)
class FeedServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private static final UUID SUBSCRIBED_TOPIC_ID = UUID.randomUUID();

    private static final UUID OTHER_TOPIC_ID = UUID.randomUUID();

    private static final int PAGE_SIZE = 20;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private InterestTopicLookupService interestTopicLookupService;

    private FeedServiceImpl feedService;

    @BeforeEach
    void setUp() {
        feedService = new FeedServiceImpl(subscriptionService, interestTopicLookupService);
    }

    @Nested
    class FindFeed {

        @BeforeEach
        void givenTheUserIsSubscribedToOneTopic() {
            when(subscriptionService.findAllForUser(USER_ID)).thenReturn(List.of(subscriptionTo(SUBSCRIBED_TOPIC_ID)));
        }

        @ParameterizedTest
        @CsvSource({"ALL, ALL", "SUBSCRIBED, INCLUDE", "NOT_SUBSCRIBED, EXCLUDE"})
        void should_send_the_subscribed_ids_with_the_mode_matching_the_filter(FeedFilter filter, InterestTopicFeedMode mode) {
            givenTheTopicServiceReturns(feedPage(List.of(), null, 0, 0));

            feedService.findFeed(USER_ID, filter, "go", PAGE_SIZE);

            InterestTopicFeedRequestDTO sent = sentRequest();
            assertThat(sent.getIds()).containsExactly(SUBSCRIBED_TOPIC_ID);
            assertThat(sent.getMode()).isEqualTo(mode);
            assertThat(sent.getAfter()).isEqualTo("go");
            assertThat(sent.getSize()).isEqualTo(PAGE_SIZE);
        }

        @Test
        void should_flag_only_the_topics_the_user_is_subscribed_to() {
            List<InterestTopicResponseDTO> topics = List.of(topic(SUBSCRIBED_TOPIC_ID, "go"), topic(OTHER_TOPIC_ID, "rust"));
            givenTheTopicServiceReturns(feedPage(topics, null, 2, 1));

            FeedResponseDTO feed = feedService.findFeed(USER_ID, FeedFilter.ALL, null, PAGE_SIZE);

            assertThat(feed.getItems())
                    .extracting(FeedTopicResponseDTO::getName, FeedTopicResponseDTO::isSubscribed)
                    .containsExactly(tuple("go", true), tuple("rust", false));
        }

        @Test
        void should_derive_the_counts_from_the_topic_service_totals() {
            givenTheTopicServiceReturns(feedPage(List.of(), null, 10, 3));

            FeedResponseDTO feed = feedService.findFeed(USER_ID, FeedFilter.ALL, null, PAGE_SIZE);

            assertThat(feed.getCounts().getAll()).isEqualTo(10);
            assertThat(feed.getCounts().getSubscribed()).isEqualTo(3);
            assertThat(feed.getCounts().getNotSubscribed()).isEqualTo(7);
        }

        @Test
        void should_pass_the_next_cursor_through() {
            givenTheTopicServiceReturns(feedPage(List.of(topic(OTHER_TOPIC_ID, "rust")), "rust", 5, 1));

            FeedResponseDTO feed = feedService.findFeed(USER_ID, FeedFilter.ALL, null, PAGE_SIZE);

            assertThat(feed.getNextCursor()).isEqualTo("rust");
        }

        @Test
        void should_propagate_a_failed_topic_service_call() {
            when(interestTopicLookupService.findFeedPage(any())).thenThrow(new InterestTopicFeedUnavailableException());

            assertThatThrownBy(() -> feedService.findFeed(USER_ID, FeedFilter.ALL, null, PAGE_SIZE))
                    .isInstanceOf(InterestTopicFeedUnavailableException.class);
        }
    }

    private void givenTheTopicServiceReturns(InterestTopicFeedResponseDTO page) {
        when(interestTopicLookupService.findFeedPage(any())).thenReturn(page);
    }

    private InterestTopicFeedRequestDTO sentRequest() {
        ArgumentCaptor<InterestTopicFeedRequestDTO> captor = ArgumentCaptor.forClass(InterestTopicFeedRequestDTO.class);
        verify(interestTopicLookupService).findFeedPage(captor.capture());
        return captor.getValue();
    }

    private InterestTopicFeedResponseDTO feedPage(
            List<InterestTopicResponseDTO> items,
            String nextCursor,
            long total,
            long matching
    ) {
        return new InterestTopicFeedResponseDTO(items, nextCursor, total, matching);
    }

    private InterestTopicResponseDTO topic(UUID id, String name) {
        return new InterestTopicResponseDTO(id, name, "A description.", UUID.randomUUID(), "programming", null);
    }

    private Subscription subscriptionTo(UUID interestTopicId) {
        Subscription subscription = new Subscription();
        subscription.setInterestTopicId(interestTopicId);
        return subscription;
    }
}
