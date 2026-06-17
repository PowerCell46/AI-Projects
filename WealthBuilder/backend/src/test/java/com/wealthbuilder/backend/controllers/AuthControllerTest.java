package com.wealthbuilder.backend.controllers;

import com.wealthbuilder.backend.dtos.auth.AuthResponse;
import com.wealthbuilder.backend.dtos.auth.LoginRequest;
import com.wealthbuilder.backend.dtos.auth.RegisterRequest;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Web-slice test for the auth endpoints. Security filters are disabled
 * ({@code addFilters = false}) so the slice exercises request mapping, bean validation and
 * the global exception handler without needing the JWT security chain or a database.
 */
@WebMvcTest(value = AuthController.class, excludeAutoConfiguration = {
        SecurityAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    private static final String TOKEN = "signed.jwt.token";

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

    @Nested
    @DisplayName("Register")
    class Register {

        @Test
        void should_Return201WithToken_When_RequestValid() throws Exception {
            given(authService.register(any(RegisterRequest.class))).willReturn(AuthResponse.of(TOKEN));

            mockMvc
                    .perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("valid-user", "valid-password")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.token").value(TOKEN));
        }

        @Test
        void should_DelegateToServiceWithBody_When_Registering() throws Exception {
            given(authService.register(any(RegisterRequest.class))).willReturn(AuthResponse.of(TOKEN));

            mockMvc
                    .perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("valid-user", "valid-password")))
                    .andExpect(status().isCreated());

            final ArgumentCaptor<RegisterRequest> captor = ArgumentCaptor.forClass(RegisterRequest.class);
            verify(authService).register(captor.capture());
            assertThat(captor.getValue().getUsername()).isEqualTo("valid-user");
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
                            .content(json("valid-user", "short")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        void should_Return200WithToken_When_RequestValid() throws Exception {
            given(authService.login(any(LoginRequest.class))).willReturn(AuthResponse.of(TOKEN));

            mockMvc
                    .perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("valid-user", "any-password")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value(TOKEN));
        }

        @Test
        void should_Return400_When_PasswordBlank() throws Exception {
            mockMvc
                    .perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("valid-user", "")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.password").exists());
        }
    }

    private static String json(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }
}
