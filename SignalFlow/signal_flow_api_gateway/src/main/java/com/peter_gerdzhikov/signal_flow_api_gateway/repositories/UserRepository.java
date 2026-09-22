package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    /**
     * Takes a `SELECT ... FOR UPDATE` row lock on the user and returns only its id, so a caller can
     * serialise concurrent writes for that user without loading the user itself. Requires an active
     * transaction; the lock is held until it commits. Empty when no such user exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u.id FROM User u WHERE u.id = :id")
    Optional<UUID> lockById(@Param("id") UUID id);
}
