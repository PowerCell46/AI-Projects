package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.TopicNewsInbox;

public interface TopicNewsInboxRepository extends JpaRepository<TopicNewsInbox, UUID> {

    @Modifying
    @Transactional
    @Query("DELETE FROM TopicNewsInbox i WHERE i.processedAt < :cutoff")
    int deleteAllByProcessedAtBefore(@Param("cutoff") Instant cutoff);
}
