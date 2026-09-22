package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    long countByUser_Id(UUID userId);

    boolean existsByUser_IdAndInterestTopicId(UUID userId, UUID interestTopicId);

    Optional<Subscription> findByUser_IdAndInterestTopicId(UUID userId, UUID interestTopicId);

    List<Subscription> findAllByUser_IdOrderByCreatedAtDesc(UUID userId);
}
