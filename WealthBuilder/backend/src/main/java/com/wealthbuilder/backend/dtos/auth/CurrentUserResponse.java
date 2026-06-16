package com.wealthbuilder.backend.dtos.auth;

import com.wealthbuilder.backend.entities.Role;
import lombok.Value;

import java.math.BigDecimal;


/**
 * Snapshot of the authenticated account for the SPA. {@code balance} is the user's net
 * invested amount — a derived value, never stored.
 */
@Value
public class CurrentUserResponse {

    String username;

    Role role;

    BigDecimal balance;

    public static CurrentUserResponse of(String username, Role role, BigDecimal balance) {
        return new CurrentUserResponse(username, role, balance);
    }
}
