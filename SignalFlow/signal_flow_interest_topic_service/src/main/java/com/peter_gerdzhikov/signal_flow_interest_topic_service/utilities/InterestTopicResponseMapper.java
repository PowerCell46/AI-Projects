package com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities;

import java.util.Comparator;
import java.util.List;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.categories.CategorySummaryResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.interesttopics.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public final class InterestTopicResponseMapper {

    private InterestTopicResponseMapper() {
    }

    public static InterestTopicResponseDTO toResponse(InterestTopic interestTopic) {
        return new InterestTopicResponseDTO(
                interestTopic.getId(),
                interestTopic.getName(),
                interestTopic.getDescription(),
                toSortedSummaries(interestTopic),
                interestTopic.getSchedule(),
                interestTopic.getCreatedAt());
    }

    private static List<CategorySummaryResponseDTO> toSortedSummaries(InterestTopic interestTopic) {
        return interestTopic
                .getCategories()
                .stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(category -> new CategorySummaryResponseDTO(category.getId(), category.getName()))
                .toList();
    }
}
