package com.peter_gerdzhikov.twitter_api_gateway.entities.follows;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.peter_gerdzhikov.twitter_api_gateway.entities.users.User;

/**
 * Rows are written by a native insert that supplies the id and {@code createdAt} (see
 * {@code FollowRepository}), so the id has no generator. It has no {@code updatedAt}: a follow is only ever
 * created or deleted. {@code createdAt} must be truncated to microseconds by the caller so it round-trips
 * through Postgres exactly, because the list cursor is built from it.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Check(name = Follow.NO_SELF_FOLLOW_CONSTRAINT, constraints = "follower_id <> following_id")
@Table(
        name = "follows",
        uniqueConstraints = @UniqueConstraint(
                name = Follow.PAIR_CONSTRAINT,
                columnNames = {"follower_id", "following_id"}
        ),
        indexes = {
                @Index(name = "ix_follows_following_created", columnList = "following_id, created_at, id"),
                @Index(name = "ix_follows_follower_created", columnList = "follower_id, created_at, id")
        }
)
public class Follow {

    public static final String PAIR_CONSTRAINT = "uk_follows_pair";

    public static final String NO_SELF_FOLLOW_CONSTRAINT = "ck_follows_no_self_follow";

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "follower_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_follows_follower")
    )
    private User follower;

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "following_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_follows_following")
    )
    private User following;

    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
