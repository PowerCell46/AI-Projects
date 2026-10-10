package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
