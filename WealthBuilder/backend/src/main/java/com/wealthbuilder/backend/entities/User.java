package com.wealthbuilder.backend.entities;

import com.wealthbuilder.backend.entities.enumerations.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


/**
 * An application account. The password is stored only as a BCrypt hash; the raw
 * value never touches persistence. {@code balance} is intentionally absent — it is
 * derived from the user's holdings, never stored.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Incremented to invalidate every token already issued for this account. Each token carries
    // the version it was signed with; the auth filter rejects any token whose version is stale.
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /** Invalidates every token already issued for this account (e.g. on logout or compromise). */
    public void revokeIssuedTokens() {
        this.tokenVersion++;
    }
}
