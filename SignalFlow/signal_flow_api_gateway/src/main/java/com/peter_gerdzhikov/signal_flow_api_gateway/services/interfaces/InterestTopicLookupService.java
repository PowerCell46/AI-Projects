package com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.feed.InterestTopicFeedResponseDTO;

public interface InterestTopicLookupService {

    Set<UUID> findExistingIds(Collection<UUID> interestTopicIds);

    InterestTopicFeedResponseDTO findFeedPage(InterestTopicFeedRequestDTO request);
}
