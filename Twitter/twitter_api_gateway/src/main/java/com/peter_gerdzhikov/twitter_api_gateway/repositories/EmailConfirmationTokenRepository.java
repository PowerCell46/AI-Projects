package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.EmailConfirmationToken;

import jakarta.persistence.LockModeType;

public interface EmailConfirmationTokenRepository extends JpaRepository<EmailConfirmationToken, UUID> {

    /**
     * Takes a row lock, so a concurrent second confirm of the same token waits for the first to commit and
     * then finds nothing. Requires an active transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailConfirmationToken> findByTokenHash(String tokenHash);

    Optional<EmailConfirmationToken> findFirstByUserIdOrderByIssuedAtDesc(UUID userId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM EmailConfirmationToken t WHERE t.user.id = :userId")
    int deleteByUserId(@Param("userId") UUID userId);
}
