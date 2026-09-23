package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.FeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.FeedFilter;

public interface FeedService {

    /**
     * Up to {@code size} topics named after {@code after} (null = from the start), in name order,
     * narrowed by {@code filter} against the user's subscriptions.
     */
    FeedResponseDTO findFeed(UUID userId, FeedFilter filter, String after, int size);
}
