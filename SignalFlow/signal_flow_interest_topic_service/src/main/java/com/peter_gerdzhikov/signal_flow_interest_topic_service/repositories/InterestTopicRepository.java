package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

public interface InterestTopicRepository extends JpaRepository<InterestTopic, UUID> {

    boolean existsByCategory_Id(UUID categoryId);

    Page<InterestTopic> findByCategory_Id(UUID categoryId, Pageable pageable);

    @Query("SELECT t.id FROM InterestTopic t WHERE t.id IN :ids")
    List<UUID> findExistingIds(@Param("ids") Collection<UUID> ids);
}
