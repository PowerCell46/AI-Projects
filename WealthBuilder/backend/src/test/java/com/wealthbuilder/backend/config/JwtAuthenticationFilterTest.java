package com.wealthbuilder.backend.config;

import com.wealthbuilder.backend.DTOs.auth.TokenClaims;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;


/**
 * Unit test for the JWT auth filter, focused on the token-version revocation check: a token whose
 * version matches the account authenticates, while a stale-version token is rejected.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String COOKIE_NAME = "wb_token";

    private static final String TOKEN = "signed.jwt.token";

    private static final String USERNAME = "alice";

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private AuthTokenCookie authTokenCookie;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void should_Authenticate_When_TokenVersionMatches() throws Exception {
        stubTokenForUserWithVersions(2, 2);

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void should_NotAuthenticate_When_TokenVersionIsStale() throws Exception {
        stubTokenForUserWithVersions(1, 2);

        runFilter();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void stubTokenForUserWithVersions(int tokenVersion, int storedVersion) {
        given(authTokenCookie.getName()).willReturn(COOKIE_NAME);
        given(jwtService.verify(TOKEN)).willReturn(new TokenClaims(USERNAME, tokenVersion));
        given(userDetailsService.loadUserByUsername(USERNAME)).willReturn(new AppUserDetails(
                USERNAME, "hash", storedVersion, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    private void runFilter() throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(COOKIE_NAME, TOKEN));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }
}
