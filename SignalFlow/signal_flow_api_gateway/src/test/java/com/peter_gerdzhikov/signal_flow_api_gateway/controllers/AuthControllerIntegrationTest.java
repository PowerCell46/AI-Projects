package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.UserResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class AuthControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String COOKIE_NAME = "access_token";
    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void clearUsers() {
        userRepository.deleteAll();
    }

    @Nested
    class Register {

        @Test
        void should_create_a_user_and_return_it_with_a_cookie() {
            UserResponseDTO body = register(EMAIL, PASSWORD)
                    .expectStatus().isCreated()
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getEmail()).isEqualTo(EMAIL);
            assertThat(body.getRole()).isEqualTo(Role.USER);
        }

        @Test
        void should_set_the_cookie_as_http_only_same_site_strict_and_path_root() {
            register(EMAIL, PASSWORD)
                    .expectStatus().isCreated()
                    .expectCookie().httpOnly(COOKIE_NAME, true)
                    .expectCookie().sameSite(COOKIE_NAME, "Strict")
                    .expectCookie().path(COOKIE_NAME, "/");
        }

        @Test
        void should_not_store_the_password_as_plaintext() {
            register(EMAIL, PASSWORD).expectStatus().isCreated();

            User saved = userRepository.findByEmail(EMAIL).orElseThrow();

            assertThat(saved.getPassword()).isNotEqualTo(PASSWORD);
        }

        @Test
        void should_store_the_email_lowercased() {
            register("User@Example.com", PASSWORD).expectStatus().isCreated();

            assertThat(userRepository.findByEmail(EMAIL)).isPresent();
        }

        @Test
        void should_return_409_for_a_duplicate_email() {
            register(EMAIL, PASSWORD).expectStatus().isCreated();

            register(EMAIL, PASSWORD).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_409_for_a_duplicate_email_differing_only_in_case() {
            register(EMAIL, PASSWORD).expectStatus().isCreated();

            register("User@Example.com", PASSWORD).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_naming_the_field_for_an_invalid_email() {
            ErrorResponseDTO body = register("not-an-email", PASSWORD)
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages().getFirst()).contains("email");
        }

        @Test
        void should_return_400_for_a_password_under_8_characters() {
            register(EMAIL, "short12").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_over_72_characters() {
            register(EMAIL, "a".repeat(73)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_without_an_uppercase_letter() {
            register(EMAIL, "password123").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_without_a_lowercase_letter() {
            register(EMAIL, "PASSWORD123").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_without_a_digit() {
            register(EMAIL, "Passwordonly").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_an_email_without_a_top_level_domain() {
            register("user@localhost", PASSWORD).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_blank_fields() {
            register("", "").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_malformed_json() {
            restTestClient.post()
                    .uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ not valid json")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void should_ignore_a_role_sent_in_the_request_body() {
            restTestClient.post()
                    .uri("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"role\":\"ADMIN\"}")
                    .exchange()
                    .expectStatus().isCreated();

            assertThat(userRepository.findByEmail(EMAIL).orElseThrow().getRole()).isEqualTo(Role.USER);
        }

        @Test
        void should_not_leak_exception_or_package_names_in_the_error_body() {
            ErrorResponseDTO body = register("not-an-email", PASSWORD)
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
        }
    }

    @Nested
    class Login {

        @Test
        void should_return_200_and_a_cookie_for_valid_credentials() {
            registerUser(EMAIL, PASSWORD);

            login(EMAIL, PASSWORD)
                    .expectStatus().isOk()
                    .expectCookie().exists(COOKIE_NAME);
        }

        @Test
        void should_return_a_generic_401_for_wrong_password() {
            registerUser(EMAIL, PASSWORD);

            login(EMAIL, "WrongPassword123").expectStatus().isUnauthorized();
        }

        @Test
        void should_return_an_identical_generic_401_for_an_unknown_email() {
            login("nobody@example.com", PASSWORD).expectStatus().isUnauthorized();
        }

        @Test
        void should_return_an_identical_generic_401_for_a_disabled_user() {
            registerUser(EMAIL, PASSWORD);
            User user = userRepository.findByEmail(EMAIL).orElseThrow();
            user.setEnabled(false);
            userRepository.save(user);

            login(EMAIL, PASSWORD).expectStatus().isUnauthorized();
        }

        @Test
        void should_match_the_email_case_insensitively() {
            registerUser(EMAIL, PASSWORD);

            login("User@Example.com", PASSWORD).expectStatus().isOk();
        }

        @Test
        void should_return_400_for_malformed_json() {
            restTestClient.post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ not valid json")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_under_8_characters() {
            login(EMAIL, "short12").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_over_72_characters() {
            login(EMAIL, "a".repeat(73)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_without_an_uppercase_letter() {
            login(EMAIL, "password123").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_without_a_lowercase_letter() {
            login(EMAIL, "PASSWORD123").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_password_without_a_digit() {
            login(EMAIL, "Passwordonly").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_an_email_without_a_top_level_domain() {
            login("user@localhost", PASSWORD).expectStatus().isBadRequest();
        }
    }

    @Nested
    class Logout {

        @Test
        void should_return_204_and_expire_the_cookie() {
            registerUser(EMAIL, PASSWORD);
            String cookie = login(EMAIL, PASSWORD)
                    .expectCookie().exists(COOKIE_NAME)
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseCookies()
                    .getFirst(COOKIE_NAME)
                    .getValue();

            restTestClient.post()
                    .uri("/api/v1/auth/logout")
                    .cookie(COOKIE_NAME, cookie)
                    .exchange()
                    .expectStatus().isNoContent()
                    .expectCookie().maxAge(COOKIE_NAME, Duration.ZERO);
        }

        @Test
        void should_return_204_with_no_cookie_present() {
            restTestClient.post()
                    .uri("/api/v1/auth/logout")
                    .exchange()
                    .expectStatus().isNoContent();
        }

        @Test
        void should_prevent_a_browser_that_dropped_the_cleared_cookie_from_authenticating_me() {
            registerUser(EMAIL, PASSWORD);
            String cookie = login(EMAIL, PASSWORD)
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseCookies()
                    .getFirst(COOKIE_NAME)
                    .getValue();

            restTestClient.post()
                    .uri("/api/v1/auth/logout")
                    .cookie(COOKIE_NAME, cookie)
                    .exchange()
                    .expectStatus().isNoContent();

            restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    class Me {

        @Test
        void should_return_the_callers_id_email_role_and_created_at() {
            registerUser(EMAIL, PASSWORD);
            String cookie = login(EMAIL, PASSWORD)
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseCookies()
                    .getFirst(COOKIE_NAME)
                    .getValue();
            User saved = userRepository.findByEmail(EMAIL).orElseThrow();

            UserResponseDTO body = restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .cookie(COOKIE_NAME, cookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isEqualTo(saved.getId());
            assertThat(body.getEmail()).isEqualTo(EMAIL);
            assertThat(body.getRole()).isEqualTo(Role.USER);
            assertThat(body.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        }

        @Test
        void should_return_401_with_no_cookie() {
            restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void should_return_401_with_a_garbage_cookie() {
            restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .cookie(COOKIE_NAME, "garbage")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void should_return_401_with_a_token_signed_by_a_different_secret() throws JOSEException {
            String token = mintToken("a-completely-different-signing-secret-value", Instant.now(), Instant.now().plusSeconds(3600));

            restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .cookie(COOKIE_NAME, token)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void should_return_401_with_an_expired_token() throws JOSEException {
            String token = mintToken(jwtSecret, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

            restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .cookie(COOKIE_NAME, token)
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void should_never_return_the_password() {
            registerUser(EMAIL, PASSWORD);
            String cookie = login(EMAIL, PASSWORD)
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseCookies()
                    .getFirst(COOKIE_NAME)
                    .getValue();

            String body = restTestClient.get()
                    .uri("/api/v1/auth/me")
                    .cookie(COOKIE_NAME, cookie)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(String.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body).doesNotContain(PASSWORD);
        }
    }

    private void registerUser(String email, String password) {
        register(email, password).expectStatus().isCreated();
    }

    private RestTestClient.ResponseSpec register(String email, String password) {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail(email);
        request.setPassword(password);

        return restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private RestTestClient.ResponseSpec login(String email, String password) {
        LoginRequestDTO request = new LoginRequestDTO();
        request.setEmail(email);
        request.setPassword(password);

        return restTestClient.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private String mintToken(String secret, Instant issuedAt, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", EMAIL)
                .claim("role", Role.USER.name())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(new MACSigner(secret));
        return signedJwt.serialize();
    }
}
