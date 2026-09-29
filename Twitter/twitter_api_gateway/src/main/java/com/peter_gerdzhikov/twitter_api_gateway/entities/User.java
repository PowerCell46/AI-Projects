package com.peter_gerdzhikov.twitter_api_gateway.entities;

import java.time.LocalDate;
import java.util.Locale;

import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Past;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * {@code @DynamicUpdate} writes only the changed columns, so a profile edit and a picture upload racing each
 * other can't overwrite each other's columns.
 */
@Getter
@Setter
@Entity
@Builder
@DynamicUpdate
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = User.EMAIL_CONSTRAINT, columnNames = "email"),
                @UniqueConstraint(name = User.USERNAME_CONSTRAINT, columnNames = "username_normalized")
        }
)
public class User extends CommonEntity {

    public static final String EMAIL_CONSTRAINT = "uk_users_email";

    public static final String USERNAME_CONSTRAINT = "uk_users_username_normalized";

    @Column(nullable = true, length = 160)
    private String bio;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(nullable = false, length = 15)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = true, length = 30)
    private String location;

    @Past
    @Column(nullable = true)
    private LocalDate birthdate;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_picture_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_users_profile_picture")
    )
    private DbFile profilePicture;

    /**
     * The single "confirmed" flag: false from registration until the confirmation token is used.
     */
    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "username_normalized", nullable = false, length = 15)
    private String usernameNormalized;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "profile_cover_picture_id",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_users_profile_cover_picture")
    )
    private DbFile profileCoverPicture;

    @PrePersist
    @PreUpdate
    private void normalize() {
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
        }
        if (username != null) {
            usernameNormalized = username.toLowerCase(Locale.ROOT);
        }
    }
}
