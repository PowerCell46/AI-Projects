package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.dashboard.AssetDistributionResponse;
import com.wealthbuilder.backend.exceptions.GlobalExceptionHandler;
import com.wealthbuilder.backend.services.interfaces.DashboardService;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Web-slice test for the dashboard endpoint. A minimal security chain requires authentication on
 * every request, and {@code spring-security-test} post-processors stand in for the JWT filter.
 */
@WebMvcTest(DashboardController.class)
@Import({DashboardControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    // The web slice eagerly instantiates the JwtAuthenticationFilter @Component, so its
    // collaborators must be present even though this test injects identities directly.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Nested
    @DisplayName("Authentication required")
    class AuthenticationRequired {

        @Test
        void should_Return401_When_RequestedAnonymously() throws Exception {
            mockMvc
                    .perform(get("/api/dashboard/distribution"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Distribution")
    class Distribution {

        @Test
        void should_Return200WithDistribution_When_RequestedByUser() throws Exception {
            given(dashboardService.distribution("alice"))
                    .willReturn(List.of(
                            new AssetDistributionResponse(7L, "Stocks", new BigDecimal("900.00")),
                            new AssetDistributionResponse(3L, "Crypto", new BigDecimal("250.00"))));

            mockMvc
                    .perform(get("/api/dashboard/distribution").with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].assetId").value(7))
                    .andExpect(jsonPath("$[0].assetName").value("Stocks"))
                    .andExpect(jsonPath("$[0].amountInvested").value(900.00))
                    .andExpect(jsonPath("$[1].assetId").value(3));
        }

        @Test
        void should_Return200WithEmptyArray_When_UserHasNoInvestments() throws Exception {
            given(dashboardService.distribution("alice")).willReturn(List.of());

            mockMvc
                    .perform(get("/api/dashboard/distribution").with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }

    /**
     * Minimal stand-in for the production security chain: authentication is required for every
     * request. The JWT filter is intentionally absent — identities arrive via test
     * post-processors.
     */
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().authenticated())
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

            return http.build();
        }
    }
}
