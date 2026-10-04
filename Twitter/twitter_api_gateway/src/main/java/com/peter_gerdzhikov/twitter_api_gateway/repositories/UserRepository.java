package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;

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
     * Keyset pages of confirmed users other than the caller, newest first. Ties on {@code createdAt} are broken
     * by id, so a page boundary that falls inside a run of equal timestamps loses and repeats nothing. The cursor
     * is compared as a row value because Postgres can turn that into an index bound; the equivalent
     * {@code OR} form makes every page read all the rows newer than the cursor.
     */
    @Query("""
            SELECT u FROM User u LEFT JOIN FETCH u.profilePicture
            WHERE u.enabled = true AND u.id <> :callerId
            ORDER BY u.createdAt DESC, u.id DESC
            """)
    List<User> findUserListFirstPage(@Param("callerId") UUID callerId, Pageable limit);

    @Query("""
            SELECT u FROM User u LEFT JOIN FETCH u.profilePicture
            WHERE u.enabled = true AND u.id <> :callerId
              AND (u.createdAt, u.id) < (:createdAt, :id)
            ORDER BY u.createdAt DESC, u.id DESC
            """)
    List<User> findUserListAfter(
            @Param("callerId") UUID callerId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable limit
    );

    /**
     * Bulk delete: the database cascades to the users' tokens, and nothing else is touched. Returns the
     * number of users removed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM User u WHERE u.enabled = false AND u.createdAt < :cutoff")
    int deleteUnconfirmedCreatedBefore(@Param("cutoff") Instant cutoff);

    /**
     * One atomic {@code UPDATE ... SET x = x + :delta}, so concurrent callers never overwrite each other.
     * Callers that touch two users must update them in a fixed id order to avoid deadlocks. Returns the
     * number of rows changed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.followersCount = u.followersCount + :delta WHERE u.id = :id")
    int addToFollowersCount(@Param("id") UUID id, @Param("delta") long delta);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE User u SET u.followingCount = u.followingCount + :delta WHERE u.id = :id")
    int addToFollowingCount(@Param("id") UUID id, @Param("delta") long delta);
}
