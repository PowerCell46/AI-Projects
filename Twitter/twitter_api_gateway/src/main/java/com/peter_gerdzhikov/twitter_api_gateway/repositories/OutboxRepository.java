package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    /**
     * Locks the rows it returns and skips rows another instance has locked, so two pollers never send the same row.
     * The locks last until the caller's transaction ends, so it must run inside one. The lock timeout of -2 is
     * Hibernate's SKIP_LOCKED, which the JPA hint takes as a plain number.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
