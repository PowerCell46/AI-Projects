package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.follows.Follow;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.projections.FollowerEdge;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

    /**
     * Returns 1 when the row was inserted and 0 when the pair already existed, so the caller bumps the
     * counters only on 1. A self-follow still fails on the check constraint; only duplicates are swallowed.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO follows (id, follower_id, following_id, created_at)
            VALUES (:id, :followerId, :followingId, :createdAt)
            ON CONFLICT (follower_id, following_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("followerId") UUID followerId,
            @Param("followingId") UUID followingId,
            @Param("createdAt") Instant createdAt
    );

    boolean existsByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    /**
     * Returns 1 when the follow existed and was removed, 0 when there was nothing to remove.
     */
    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Follow f WHERE f.follower.id = :followerId AND f.following.id = :followingId")
    int deleteByPair(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    /**
     * The subset of {@code candidateIds} that {@code followerId} follows, in one query.
     */
    @Query("SELECT f.following.id FROM Follow f WHERE f.follower.id = :followerId AND f.following.id IN :candidateIds")
    List<UUID> findFollowedIds(
            @Param("followerId") UUID followerId,
            @Param("candidateIds") Collection<UUID> candidateIds
    );

    /**
     * Keyset pages, newest first. Ties on {@code createdAt} are broken by id, so a page boundary that falls
     * inside a run of equal timestamps loses and repeats nothing.
     */
    @Query("""
            SELECT f FROM Follow f JOIN FETCH f.follower
            WHERE f.following.id = :userId
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findFollowersFirstPage(@Param("userId") UUID userId, Pageable limit);

    @Query("""
            SELECT f FROM Follow f JOIN FETCH f.follower
            WHERE f.following.id = :userId
              AND (f.createdAt < :createdAt OR (f.createdAt = :createdAt AND f.id < :id))
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findFollowersAfter(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable limit
    );

    @Query("""
            SELECT f FROM Follow f JOIN FETCH f.following
            WHERE f.follower.id = :userId
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findFollowingFirstPage(@Param("userId") UUID userId, Pageable limit);

    @Query("""
            SELECT f FROM Follow f JOIN FETCH f.following
            WHERE f.follower.id = :userId
              AND (f.createdAt < :createdAt OR (f.createdAt = :createdAt AND f.id < :id))
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<Follow> findFollowingAfter(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable limit
    );

    /**
     * Same order and keyset as {@link #findFollowersFirstPage}, but selecting only ids and the timestamp, so a
     * page of a thousand followers loads no user rows.
     */
    @Query("""
            SELECT f.id AS followId, f.follower.id AS followerId, f.createdAt AS createdAt
            FROM Follow f
            WHERE f.following.id = :userId
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<FollowerEdge> findFollowerEdgesFirstPage(@Param("userId") UUID userId, Pageable limit);

    @Query("""
            SELECT f.id AS followId, f.follower.id AS followerId, f.createdAt AS createdAt
            FROM Follow f
            WHERE f.following.id = :userId
              AND (f.createdAt < :createdAt OR (f.createdAt = :createdAt AND f.id < :id))
            ORDER BY f.createdAt DESC, f.id DESC
            """)
    List<FollowerEdge> findFollowerEdgesAfter(
            @Param("userId") UUID userId,
            @Param("createdAt") Instant createdAt,
            @Param("id") UUID id,
            Pageable limit
    );
}
