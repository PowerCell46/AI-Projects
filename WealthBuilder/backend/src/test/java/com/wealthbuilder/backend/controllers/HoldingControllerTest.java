package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.dtos.PageResponse;
import com.wealthbuilder.backend.dtos.holding.HoldingRequest;
import com.wealthbuilder.backend.dtos.holding.HoldingResponse;
import com.wealthbuilder.backend.dtos.holding.HoldingSummaryResponse;
import com.wealthbuilder.backend.exceptions.AssetNotFoundException;
import com.wealthbuilder.backend.exceptions.GlobalExceptionHandler;
import com.wealthbuilder.backend.exceptions.HoldingNotFoundException;
import com.wealthbuilder.backend.services.interfaces.HoldingService;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Web-slice test for the holding endpoints. A minimal security chain requires authentication on
 * every request, while {@code spring-security-test} post-processors stand in for the JWT filter
 * so identities are injected directly and the service layer is mocked.
 */
@WebMvcTest(HoldingController.class)
@Import({HoldingControllerTest.TestSecurityConfig.class, GlobalExceptionHandler.class})
class HoldingControllerTest {

    private static final Long ASSET_ID = 7L;

    private static final Long HOLDING_ID = 42L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HoldingService holdingService;

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
        void should_Return401_When_ListRequestedAnonymously() throws Exception {
            mockMvc
                    .perform(get("/api/assets/{assetId}/holdings", ASSET_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void should_Return401_When_CreateRequestedAnonymously() throws Exception {
            mockMvc
                    .perform(post("/api/assets/{assetId}/holdings", ASSET_ID)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("List")
    class ListHoldings {

        @Test
        void should_Return200WithPageEnvelope_When_RequestedByUser() throws Exception {
            final PageResponse<HoldingResponse> page =
                    new PageResponse<>(List.of(response()), 0, 20, 1, 1);
            given(holdingService.listHoldings(eq("alice"), eq(ASSET_ID), any()))
                    .willReturn(page);

            mockMvc
                    .perform(get("/api/assets/{assetId}/holdings", ASSET_ID).with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(HOLDING_ID))
                    .andExpect(jsonPath("$.content[0].price").value(50))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        void should_Return404Problem_When_AssetMissing() throws Exception {
            given(holdingService.listHoldings(eq("alice"), eq(ASSET_ID), any()))
                    .willThrow(new AssetNotFoundException(ASSET_ID));

            mockMvc
                    .perform(get("/api/assets/{assetId}/holdings", ASSET_ID).with(user("alice").roles("USER")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Asset not found: " + ASSET_ID));
        }
    }

    @Nested
    @DisplayName("Summary")
    class Summary {

        @Test
        void should_Return200WithSummary_When_RequestedByUser() throws Exception {
            given(holdingService.summarize("alice", ASSET_ID))
                    .willReturn(HoldingSummaryResponse.of(
                            2,
                            new BigDecimal("62.5"),
                            new BigDecimal("6"),
                            new BigDecimal("400"),
                            LocalDate.of(2026, 1, 5),
                            LocalDate.of(2026, 3, 10)));

            mockMvc
                    .perform(get("/api/assets/{assetId}/holdings/summary", ASSET_ID)
                            .with(user("alice").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.holdingCount").value(2))
                    .andExpect(jsonPath("$.averagePrice").value(62.5))
                    .andExpect(jsonPath("$.amountSum").value(400));
        }
    }

    @Nested
    @DisplayName("Create")
    class Create {

        @Test
        void should_Return201WithDerivedPrice_When_UserPostsValidBody() throws Exception {
            given(holdingService.create(eq("alice"), eq(ASSET_ID), any(HoldingRequest.class)))
                    .willReturn(response());

            mockMvc
                    .perform(post("/api/assets/{assetId}/holdings", ASSET_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(HOLDING_ID))
                    .andExpect(jsonPath("$.price").value(50));

            verify(holdingService).create(eq("alice"), eq(ASSET_ID), any(HoldingRequest.class));
        }

        @Test
        void should_Return400WithErrors_When_BodyHasBlankNameAndNegativeAmount() throws Exception {
            final String badBody = "{\"name\":\"\",\"boughtForAmount\":-5,"
                    + "\"quantity\":2,\"date\":\"2026-02-01\"}";

            mockMvc
                    .perform(post("/api/assets/{assetId}/holdings", ASSET_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(badBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.name").exists())
                    .andExpect(jsonPath("$.errors.boughtForAmount").exists());
        }

        @Test
        void should_Return400WithErrors_When_DateIsInTheFuture() throws Exception {
            final String futureBody = "{\"name\":\"Apple\",\"boughtForAmount\":100,"
                    + "\"quantity\":2,\"date\":\"2999-01-01\"}";

            mockMvc
                    .perform(post("/api/assets/{assetId}/holdings", ASSET_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(futureBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.date").exists());
        }

        @Test
        void should_Return404Problem_When_AssetMissing() throws Exception {
            given(holdingService.create(eq("alice"), eq(ASSET_ID), any(HoldingRequest.class)))
                    .willThrow(new AssetNotFoundException(ASSET_ID));

            mockMvc
                    .perform(post("/api/assets/{assetId}/holdings", ASSET_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Asset not found: " + ASSET_ID));
        }
    }

    @Nested
    @DisplayName("Update")
    class Update {

        @Test
        void should_Return200_When_OwnerUpdatesHolding() throws Exception {
            given(holdingService.update(eq("alice"), eq(HOLDING_ID), any(HoldingRequest.class)))
                    .willReturn(response());

            mockMvc
                    .perform(put("/api/holdings/{id}", HOLDING_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(HOLDING_ID));
        }

        @Test
        void should_Return404Problem_When_HoldingMissing() throws Exception {
            willThrow(new HoldingNotFoundException(HOLDING_ID))
                    .given(holdingService).update(eq("alice"), eq(HOLDING_ID), any(HoldingRequest.class));

            mockMvc
                    .perform(put("/api/holdings/{id}", HOLDING_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Holding not found: " + HOLDING_ID));
        }

        @Test
        void should_Return403Problem_When_CallerIsNotOwner() throws Exception {
            willThrow(new AccessDeniedException("Holding is owned by another user."))
                    .given(holdingService).update(eq("mallory"), eq(HOLDING_ID), any(HoldingRequest.class));

            mockMvc
                    .perform(put("/api/holdings/{id}", HOLDING_ID)
                            .with(user("mallory").roles("USER"))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(validBody()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").value("You are not allowed to perform this action."));
        }
    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        void should_Return204_When_OwnerDeletesHolding() throws Exception {
            mockMvc
                    .perform(delete("/api/holdings/{id}", HOLDING_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf()))
                    .andExpect(status().isNoContent());

            verify(holdingService).delete("alice", HOLDING_ID);
        }

        @Test
        void should_Return404Problem_When_HoldingMissing() throws Exception {
            willThrow(new HoldingNotFoundException(HOLDING_ID))
                    .given(holdingService).delete("alice", HOLDING_ID);

            mockMvc
                    .perform(delete("/api/holdings/{id}", HOLDING_ID)
                            .with(user("alice").roles("USER"))
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_Return403Problem_When_CallerIsNotOwner() throws Exception {
            willThrow(new AccessDeniedException("Holding is owned by another user."))
                    .given(holdingService).delete("mallory", HOLDING_ID);

            mockMvc
                    .perform(delete("/api/holdings/{id}", HOLDING_ID)
                            .with(user("mallory").roles("USER"))
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    private static HoldingResponse response() {
        return new HoldingResponse(
                HOLDING_ID,
                ASSET_ID,
                "Apple shares",
                new BigDecimal("100.0000"),
                new BigDecimal("2.00000000"),
                new BigDecimal("50.00000000"),
                LocalDate.of(2026, 2, 1),
                "Bought on the dip.",
                Instant.parse("2026-02-01T10:15:30Z"));
    }

    private static String validBody() {
        return "{\"name\":\"Apple shares\",\"boughtForAmount\":100,"
                + "\"quantity\":2,\"date\":\"2026-02-01\",\"note\":\"Bought on the dip.\"}";
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
