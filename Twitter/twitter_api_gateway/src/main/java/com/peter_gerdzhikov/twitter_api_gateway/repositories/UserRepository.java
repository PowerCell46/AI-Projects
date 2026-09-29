package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameNormalized(String usernameNormalized);

    boolean existsByEmail(String email);

    boolean existsByUsernameNormalized(String usernameNormalized);

    /**
     * Takes a {@code SELECT ... FOR UPDATE} row lock on the user and returns only its id, so a caller can
     * serialise concurrent writes for that user without loading the user itself. Requires an active
     * transaction; the lock is held until it commits. Empty when no such user exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u.id FROM User u WHERE u.id = :id")
    Optional<UUID> lockById(@Param("id") UUID id);

    /**
     * Bulk delete: the database cascades to the users' tokens, and nothing else is touched. Returns the
     * number of users removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.enabled = false AND u.createdAt < :cutoff")
    int deleteUnconfirmedCreatedBefore(@Param("cutoff") Instant cutoff);
}
