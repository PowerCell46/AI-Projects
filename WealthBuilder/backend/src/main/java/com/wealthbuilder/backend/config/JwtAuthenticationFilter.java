package com.wealthbuilder.backend.config;

import com.wealthbuilder.backend.DTOs.auth.TokenClaims;
import com.wealthbuilder.backend.exceptions.auth.InvalidTokenException;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;


/**
 * Reads the JWT from the httpOnly auth cookie on each request and, when the token is valid,
 * populates the security context. Anything wrong with the token leaves the request
 * unauthenticated; the security entry point then returns 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    private final AuthTokenCookie authTokenCookie;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String token = resolveTokenFromCookie(request);

        if (token != null && isNotAlreadyAuthenticated()) {
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            final TokenClaims claims = jwtService.verify(token);
            final UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getUsername());
            ensureTokenNotRevoked(claims, userDetails);

            final UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        } catch (RuntimeException ex) {
            // Invalid/expired/revoked token or unknown user: leave the context empty so the
            // request proceeds anonymously and the entry point answers with 401.
            log.debug("Rejected bearer token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    /** Rejects a token whose version is behind the account's current one (revoked on logout). */
    private void ensureTokenNotRevoked(TokenClaims claims, UserDetails userDetails) {
        if (userDetails instanceof AppUserDetails appUser
                && claims.getTokenVersion() != appUser.getTokenVersion()) {
            throw new InvalidTokenException("Token has been revoked");
        }
    }

    private String resolveTokenFromCookie(HttpServletRequest request) {
        final Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        final String cookieName = authTokenCookie.getName();

        for (final Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private boolean isNotAlreadyAuthenticated() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication() == null;
    }
}
