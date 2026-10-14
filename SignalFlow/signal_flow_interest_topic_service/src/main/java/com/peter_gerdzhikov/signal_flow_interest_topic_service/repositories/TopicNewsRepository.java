package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;

public interface TopicNewsRepository extends JpaRepository<TopicNews, UUID> {

    boolean existsByInterestTopic_IdAndNewsDate(UUID interestTopicId, LocalDate newsDate);

    @Query("""
            SELECT tn.data FROM TopicNews tn
            WHERE tn.interestTopic.id = :interestTopicId
                AND tn.newsDate >= :startDate AND tn.newsDate < :endDate
            ORDER BY tn.newsDate DESC
            """)
    List<String> findDataByTopicInDateRange(
            @Param("interestTopicId") UUID interestTopicId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            SELECT tn FROM TopicNews tn
            JOIN FETCH tn.interestTopic topic
            WHERE tn.status = :status AND tn.nextAttemptAt <= :now
            ORDER BY tn.nextAttemptAt ASC
            """)
    List<TopicNews> findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
            @Param("status") NewsStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
