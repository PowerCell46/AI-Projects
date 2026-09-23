package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public interface InterestTopicService {

    InterestTopic create(String name, String description, String prompt, UUID categoryId);

    Page<InterestTopic> findPage(UUID categoryId, Pageable pageable);

    InterestTopic update(UUID topicId, String name, String description, String prompt, UUID categoryId);

    void delete(UUID topicId);

    List<UUID> findExistingIds(Collection<UUID> topicIds);
}
