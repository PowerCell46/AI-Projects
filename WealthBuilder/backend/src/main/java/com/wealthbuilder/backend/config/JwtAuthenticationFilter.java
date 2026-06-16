package com.wealthbuilder.backend.config;

import com.wealthbuilder.backend.services.interfaces.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
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
 * Reads the {@code Authorization: Bearer <token>} header on each request and, when the
 * token is valid, populates the security context. Anything wrong with the token leaves
 * the request unauthenticated; the security entry point then returns 401.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String token = resolveBearerToken(request);

        if (token != null && isNotAlreadyAuthenticated()) {
            authenticate(token, request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            final String username = jwtService.extractUsername(token);
            final UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            final UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);
        } catch (RuntimeException ex) {
            // Invalid/expired token or unknown user: leave the context empty so the
            // request proceeds anonymously and the entry point answers with 401.
            log.debug("Rejected bearer token: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        final String header = request.getHeader(AUTHORIZATION_HEADER);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    private boolean isNotAlreadyAuthenticated() {
        return SecurityContextHolder
                .getContext()
                .getAuthentication() == null;
    }
}
