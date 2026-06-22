package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.DTOs.auth.AuthenticatedUser;
import com.wealthbuilder.backend.DTOs.auth.CurrentUserResponse;
import com.wealthbuilder.backend.DTOs.auth.LoginRequest;
import com.wealthbuilder.backend.DTOs.auth.RegisterRequest;
import com.wealthbuilder.backend.config.AuthTokenCookie;
import com.wealthbuilder.backend.entities.enumerations.Role;
import com.wealthbuilder.backend.exceptions.GlobalExceptionHandler;
import com.wealthbuilder.backend.services.interfaces.AuthService;
import com.wealthbuilder.backend.services.interfaces.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Web-slice test for the auth endpoints. Security filters are disabled
 * ({@code addFilters = false}) so the slice exercises request mapping, bean validation and
 * the global exception handler without needing the JWT security chain or a database. The token
 * now rides in an httpOnly cookie, so success is asserted via the {@code Set-Cookie} header and
 * the current-user body — never a token in the JSON.
 */
@WebMvcTest(value = AuthController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    private static final String TOKEN = "signed.jwt.token";

    private static final String USERNAME = "valid-user";

    private static final CurrentUserResponse USER =
            CurrentUserResponse.of(USERNAME, Role.USER, BigDecimal.ZERO);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    // The web slice eagerly instantiates Filter beans, so JwtAuthenticationFilter's
    // collaborators must be present even though security autoconfig is excluded here.
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    // Collaborator of both AuthController (issue/clear) and JwtAuthenticationFilter (getName).
    @MockitoBean
    private AuthTokenCookie authTokenCookie;

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        void should_Return201WithUserAndSetCookie_When_RequestValid() throws Exception {
            given(authService.register(any(RegisterRequest.class))).willReturn(AuthenticatedUser.of(TOKEN, USER));
            given(authTokenCookie.issue(anyString())).willReturn(sampleCookie());

            mockMvc
                    .perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(USERNAME, "valid-password")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(USERNAME))
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(header().exists("Set-Cookie"));
        }

        @Test
        void should_DelegateToServiceWithBody_When_Registering() throws Exception {
            given(authService.register(any(RegisterRequest.class))).willReturn(AuthenticatedUser.of(TOKEN, USER));
            given(authTokenCookie.issue(anyString())).willReturn(sampleCookie());

            mockMvc
                    .perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(USERNAME, "valid-password")))
                    .andExpect(status().isCreated());

            final ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
            verify(authService).register(captor.capture());
            assertThat(captor.getValue().getUsername()).isEqualTo(USERNAME);
            assertThat(captor.getValue().getPassword()).isEqualTo("valid-password");
        }

        @Test
        void should_Return400WithErrors_When_UsernameBlank() throws Exception {
            mockMvc
                    .perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("", "valid-password")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.username").exists());
        }

        @Test
        void should_Return400WithErrors_When_PasswordTooShort() throws Exception {
            mockMvc
                    .perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(USERNAME, "short")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void should_Return200WithUserAndSetCookie_When_RequestValid() throws Exception {
            given(authService.login(any(LoginRequest.class))).willReturn(AuthenticatedUser.of(TOKEN, USER));
            given(authTokenCookie.issue(anyString())).willReturn(sampleCookie());

            mockMvc
                    .perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(USERNAME, "any-password")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(USERNAME))
                    .andExpect(jsonPath("$.token").doesNotExist())
                    .andExpect(header().exists("Set-Cookie"));
        }

        @Test
        void should_Return400_When_PasswordBlank() throws Exception {
            mockMvc
                    .perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(USERNAME, "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    @Nested
    @DisplayName("Logout")
    class Logout {

        @Test
        void should_Return204AndClearCookie_When_LoggingOut() throws Exception {
            given(authTokenCookie.clear()).willReturn(clearedCookie());

            mockMvc
                    .perform(post("/api/auth/logout"))
                    .andExpect(status().isNoContent())
                    .andExpect(header().exists("Set-Cookie"));

            verify(authTokenCookie).clear();
        }
    }

    private static ResponseCookie sampleCookie() {
        return ResponseCookie.from("wb_token", TOKEN).httpOnly(true).path("/").build();
    }

    private static ResponseCookie clearedCookie() {
        return ResponseCookie.from("wb_token", "").httpOnly(true).path("/").maxAge(0).build();
    }

    private static String json(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
