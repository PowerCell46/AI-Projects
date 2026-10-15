package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

    List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus status, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM NotificationOutbox o WHERE o.userId = :userId")
    int deleteAllByUserId(@Param("userId") UUID userId);
}
