package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    long countByUser_Id(UUID userId);

    boolean existsByUser_IdAndInterestTopicId(UUID userId, UUID interestTopicId);

    Optional<Subscription> findByUser_IdAndInterestTopicId(UUID userId, UUID interestTopicId);

    List<Subscription> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);

    /**
     * Keyset page of distinct subscribed topic ids, in Postgres's {@code uuid} order - which is not
     * {@link UUID#compareTo}'s, so the next page's {@code after} must be the last id this returned, never
     * one picked by sorting in Java. Stays correct while rows are deleted between pages, unlike offset paging.
     */
    @Query(
            value = """
                    SELECT DISTINCT interest_topic_id
                    FROM subscriptions
                    WHERE interest_topic_id > :after
                    ORDER BY interest_topic_id
                    LIMIT :size
                    """,
            nativeQuery = true
    )
    List<UUID> findDistinctInterestTopicIdsAfter(@Param("after") UUID after, @Param("size") int size);

    @Modifying
    @Transactional
    @Query("DELETE FROM Subscription s WHERE s.interestTopicId IN :interestTopicIds")
    int deleteAllByInterestTopicIdIn(@Param("interestTopicIds") Collection<UUID> interestTopicIds);
}
