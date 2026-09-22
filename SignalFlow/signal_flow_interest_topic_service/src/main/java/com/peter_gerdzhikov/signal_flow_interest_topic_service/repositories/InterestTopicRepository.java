package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public interface InterestTopicRepository extends JpaRepository<InterestTopic, UUID> {

    boolean existsByCategory_Id(UUID categoryId);

    Page<InterestTopic> findByCategory_Id(UUID categoryId, Pageable pageable);
}
