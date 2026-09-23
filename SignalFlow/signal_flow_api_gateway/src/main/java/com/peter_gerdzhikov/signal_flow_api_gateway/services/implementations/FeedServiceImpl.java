package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.FeedCountsResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.FeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.FeedTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.InterestTopicFeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.interesttopics.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.FeedFilter;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.InterestTopicFeedMode;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.FeedService;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InterestTopicLookupService;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final SubscriptionService subscriptionService;

    private final InterestTopicLookupService interestTopicLookupService;

    @Override
    public FeedResponseDTO findFeed(UUID userId, FeedFilter filter, String after, int size) {
        Set<UUID> subscribedIds = findSubscribedTopicIds(userId);
        InterestTopicFeedResponseDTO page = interestTopicLookupService
                .findFeedPage(new InterestTopicFeedRequestDTO(List.copyOf(subscribedIds), toMode(filter), after, size));

        return new FeedResponseDTO(toItems(page, subscribedIds), page.getNextCursor(), toCounts(page));
    }

    private Set<UUID> findSubscribedTopicIds(UUID userId) {
        return subscriptionService
                .findAllForUser(userId)
                .stream()
                .map(Subscription::getInterestTopicId)
                .collect(Collectors.toSet());
    }

    private InterestTopicFeedMode toMode(FeedFilter filter) {
        return switch (filter) {
            case ALL -> InterestTopicFeedMode.ALL;
            case SUBSCRIBED -> InterestTopicFeedMode.INCLUDE;
            case NOT_SUBSCRIBED -> InterestTopicFeedMode.EXCLUDE;
        };
    }

    private List<FeedTopicResponseDTO> toItems(InterestTopicFeedResponseDTO page, Set<UUID> subscribedIds) {
        return page
                .getItems()
                .stream()
                .map(topic -> toItem(topic, subscribedIds.contains(topic.getId())))
                .toList();
    }

    private FeedTopicResponseDTO toItem(InterestTopicResponseDTO topic, boolean subscribed) {
        return new FeedTopicResponseDTO(
                topic.getId(),
                topic.getName(),
                topic.getDescription(),
                topic.getCategoryId(),
                topic.getCategoryName(),
                subscribed
        );
    }

    private FeedCountsResponseDTO toCounts(InterestTopicFeedResponseDTO page) {
        return new FeedCountsResponseDTO(
                page.getTotal(),
                page.getMatching(),
                page.getTotal() - page.getMatching()
        );
    }
}
