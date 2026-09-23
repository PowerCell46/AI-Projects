package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public final class InterestTopicResponseMapper {

    private InterestTopicResponseMapper() {
    }

    public static InterestTopicResponseDTO toResponse(InterestTopic interestTopic) {
        Category category = interestTopic.getCategory();
        return new InterestTopicResponseDTO(
                interestTopic.getId(),
                interestTopic.getName(),
                interestTopic.getDescription(),
                category.getId(),
                category.getName(),
                interestTopic.getCreatedAt());
    }
}
