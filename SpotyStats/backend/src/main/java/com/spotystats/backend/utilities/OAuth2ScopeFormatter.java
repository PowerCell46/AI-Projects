package com.spotystats.backend.utilities;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;


/**
 * Converts OAuth2 scope sets to and from the comma-separated form persisted in the database.
 */
public final class OAuth2ScopeFormatter {

    private OAuth2ScopeFormatter() {
    }

    /**
     * Joins scopes into a single comma-separated value, or {@code null} when there are none.
     */
    public static String joinScopes(Set<String> scopes) {
        return (scopes == null || scopes.isEmpty()) ? null : String.join(",", scopes);
    }

    /**
     * Splits a comma-separated scope value into an ordered set, or an empty set when blank.
     */
    public static Set<String> splitScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return Set.of();
        }

        return new LinkedHashSet<>(Arrays.asList(scopes.split(",")));
    }
}
