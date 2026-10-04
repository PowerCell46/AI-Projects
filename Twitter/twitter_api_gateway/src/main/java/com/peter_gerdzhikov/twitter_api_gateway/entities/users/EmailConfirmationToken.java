package com.peter_gerdzhikov.twitter_api_gateway.entities.users;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.peter_gerdzhikov.twitter_api_gateway.entities.CommonEntity;

/**
 * Only the hash of the raw token is stored. {@code issuedAt} is set by the caller from the {@code Clock}
 * rather than by a Hibernate timestamp, so tests that move the clock control the resend cooldown.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "email_confirmation_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_email_confirmation_tokens_token_hash",
                columnNames = "token_hash"
        )
)
public class EmailConfirmationToken extends CommonEntity {

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_email_confirmation_tokens_user")
    )
    private User user;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
