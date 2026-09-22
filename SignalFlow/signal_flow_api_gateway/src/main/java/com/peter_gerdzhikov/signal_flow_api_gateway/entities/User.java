package com.peter_gerdzhikov.signal_flow_api_gateway.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "users")
public class User extends CommonEntity {

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

    /**
     * Deactivates an account without deleting it, so its subscriptions and audit trail survive. Checked
     * only while issuing a token - login folds it into the same failure branch as a wrong password, so a
     * disabled account is indistinguishable from bad credentials and cannot be probed for.
     * <p>
     * An already-issued token is unaffected: authenticated requests read no rows, so disabling takes
     * effect only when the current token expires.
     */
    @Column(nullable = false)
    private boolean enabled = true;

    @PrePersist
    @PreUpdate
    private void lowercaseEmail() {
        if (email != null) {
            email = email.toLowerCase();
        }
    }
}
