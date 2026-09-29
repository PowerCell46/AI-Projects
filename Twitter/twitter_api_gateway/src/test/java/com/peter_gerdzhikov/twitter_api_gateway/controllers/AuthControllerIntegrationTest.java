package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.ConfirmRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.LoginRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.ResendConfirmationRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.auth.UserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.User;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.ConfirmationTokenService;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.TokenService;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.MutableClock;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestEntities;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestJwts;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUser;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class AuthControllerIntegrationTest extends AbstractMinioIntegrationTest {

    private static final String COOKIE_NAME = "access_token";

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.kafka.user-confirmation-requested.name}")
    private String topicName;

    @Value("${app.confirmation.link-base-url}")
    private String linkBaseUrl;

    @Value("${app.request.max-body-bytes}")
    private int maxRequestBodyBytes;

    @Value("${app.confirmation.ttl}")
    private Duration confirmationTtl;

    @Value("${app.confirmation.resend-cooldown}")
    private Duration resendCooldown;

    @Value("${app.jwt.ttl}")
    private Duration jwtTtl;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private MutableClock mutableClock;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EmailConfirmationTokenRepository tokenRepository;

    @Autowired
    private ConfirmationTokenService confirmationTokenService;

    @Nested
    class Register {

        @Test
        void should_return_201_with_the_user_and_no_cookie_when_the_registration_is_valid() {
            RegisterRequestDTO request = validRequest().build();

            UserResponseDTO body = register(request)
                    .expectStatus()
                    .isCreated()
                    .expectHeader()
                    .doesNotExist(HttpHeaders.SET_COOKIE)
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isNotNull();
            assertThat(body.getUsername()).isEqualTo(request.getUsername());
            assertThat(body.getEmail()).isEqualTo(request.getEmail());
        }

        @Test
        void should_store_a_disabled_user_with_normalized_username_and_lowercased_email_when_registered() {
            TestUser user = TestUsers.unique();
            String typedUsername = "Mx" + user.getUsername();
            RegisterRequestDTO request = validRequest()
                    .username(typedUsername)
                    .email(user.getEmail().toUpperCase())
                    .build();

            register(request)
                    .expectStatus()
                    .isCreated();

            User saved = userRepository.findByEmail(user.getEmail()).orElseThrow();
            assertThat(saved.isEnabled()).isFalse();
            assertThat(saved.getUsername()).isEqualTo(typedUsername);
            assertThat(saved.getUsernameNormalized()).isEqualTo(typedUsername.toLowerCase());
            assertThat(saved.getEmail()).isEqualTo(user.getEmail());
        }

        @Test
        void should_store_one_token_row_whose_hash_differs_from_the_raw_token_when_registered() {
            UUID userId = registerAndReturnId(validRequest().build());

            List<EmailConfirmationToken> tokens = tokensOf(userId);

            assertThat(tokens).hasSize(1);
            assertThat(tokens.getFirst().getTokenHash()).isNotEqualTo(rawTokenOf(userId));
        }

        @Test
        void should_enqueue_one_pending_outbox_row_with_every_contract_field_when_registered() {
            RegisterRequestDTO request = validRequest().build();
            UUID userId = registerAndReturnId(request);

            List<Outbox> rows = outboxRowsOf(userId);

            assertThat(rows).hasSize(1);
            Outbox row = rows.getFirst();
            assertThat(row.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(row.getTopic()).isEqualTo(topicName);
            JsonNode payload = objectMapper.readTree(row.getPayload());
            assertThat(payload.propertyNames()).containsExactlyInAnyOrder(
                    "eventId", "userId", "email", "username", "confirmationUrl", "expiresAt");
            assertThat(UUID.fromString(payload.get("eventId").asString())).isNotNull();
            assertThat(payload.get("userId").asString()).isEqualTo(userId.toString());
            assertThat(payload.get("email").asString()).isEqualTo(request.getEmail());
            assertThat(payload.get("username").asString()).isEqualTo(request.getUsername());
            assertThat(payload.get("confirmationUrl").asString()).startsWith(linkBaseUrl + "?token=");
            assertThat(Instant.parse(payload.get("expiresAt").asString())).isAfter(Instant.now());
        }

        @Test
        void should_end_the_confirmation_url_with_a_token_matching_the_stored_hash_when_registered() {
            UUID userId = registerAndReturnId(validRequest().build());

            String storedHash = tokensOf(userId).getFirst().getTokenHash();

            assertThat(confirmationTokenService.hash(rawTokenOf(userId))).isEqualTo(storedHash);
        }

        @Test
        void should_return_409_when_the_email_is_already_registered() {
            RegisterRequestDTO first = validRequest().build();
            register(first)
                    .expectStatus()
                    .isCreated();

            RegisterRequestDTO second = validRequest().email(first.getEmail()).build();

            assertConflict(register(second), "Email already registered.");
        }

        @Test
        void should_return_409_when_the_email_differs_only_in_case() {
            RegisterRequestDTO first = validRequest().build();
            register(first)
                    .expectStatus()
                    .isCreated();

            RegisterRequestDTO second = validRequest().email(first.getEmail().toUpperCase()).build();

            assertConflict(register(second), "Email already registered.");
        }

        @Test
        void should_return_409_when_the_username_is_already_taken() {
            RegisterRequestDTO first = validRequest().build();
            register(first)
                    .expectStatus()
                    .isCreated();

            RegisterRequestDTO second = validRequest().username(first.getUsername()).build();

            assertConflict(register(second), "Username already taken.");
        }

        @Test
        void should_return_409_when_the_username_differs_only_in_case() {
            RegisterRequestDTO first = validRequest().username("PeterG" + TestUsers.unique().getUsername()).build();
            register(first)
                    .expectStatus()
                    .isCreated();

            RegisterRequestDTO second = validRequest().username(first.getUsername().toLowerCase()).build();

            assertConflict(register(second), "Username already taken.");
        }

        @Test
        void should_return_400_when_the_username_is_shorter_than_3_characters() {
            assertRejectedNaming(validRequest().username("ab").build(), "username");
        }

        @Test
        void should_return_400_when_the_username_is_longer_than_15_characters() {
            assertRejectedNaming(validRequest().username("a".repeat(16)).build(), "username");
        }

        @Test
        void should_return_400_when_the_username_has_characters_outside_letters_digits_and_underscore() {
            assertRejectedNaming(validRequest().username("bad-name!").build(), "username");
        }

        @Test
        void should_return_400_when_the_email_is_malformed() {
            assertRejectedNaming(validRequest().email("not-an-email").build(), "email");
        }

        @Test
        void should_return_400_when_the_password_is_shorter_than_8_characters() {
            assertRejectedNaming(validRequest().password("Pa1").build(), "password");
        }

        @Test
        void should_return_400_when_the_password_is_over_72_bytes_even_with_fewer_characters() {
            assertRejectedNaming(validRequest().password("Aa1" + "é".repeat(60)).build(), "password");
        }

        @Test
        void should_return_400_when_the_password_is_longer_than_72_characters() {
            assertRejectedNaming(validRequest().password("Pa1" + "x".repeat(70)).build(), "password");
        }

        @Test
        void should_return_400_when_the_password_has_no_lowercase_letter() {
            assertRejectedNaming(validRequest().password("PASSWORD123").build(), "password");
        }

        @Test
        void should_return_400_when_the_password_has_no_uppercase_letter() {
            assertRejectedNaming(validRequest().password("password123").build(), "password");
        }

        @Test
        void should_return_400_when_the_password_has_no_digit() {
            assertRejectedNaming(validRequest().password("PasswordOnly").build(), "password");
        }

        @Test
        void should_return_413_when_the_body_is_over_the_size_cap() {
            TestUser user = TestUsers.unique();
            String oversizedBody = "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}"
                    .formatted(user.getUsername(), user.getEmail(), "P1".repeat(maxRequestBodyBytes));

            registerRaw(oversizedBody)
                    .expectStatus()
                    .isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

            assertThat(userRepository.existsByEmail(user.getEmail())).isFalse();
        }

        @Test
        void should_not_leak_exception_or_package_names_in_the_error_body() {
            ErrorResponseDTO body = registerRaw("{not json")
                    .expectStatus()
                    .isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages()).noneMatch(message -> message.contains("Exception"));
            assertThat(body.getMessages()).noneMatch(message -> message.contains("com.peter_gerdzhikov"));
        }

        @Test
        void should_never_include_the_password_in_any_response() {
            RegisterRequestDTO request = validRequest().build();
            RegisterRequestDTO invalid = validRequest().username("ab").password(request.getPassword()).build();

            List<String> bodies = List.of(
                    bodyOf(register(request)),
                    bodyOf(register(request)),
                    bodyOf(register(invalid))
            );

            assertThat(bodies).noneMatch(body -> body.contains(request.getPassword()));
        }
    }

    @Nested
    class Confirm {

        @AfterEach
        void resetClock() {
            mutableClock.reset();
        }

        @Test
        void should_return_204_and_enable_the_user_when_the_token_is_valid() {
            UUID userId = registerAndReturnId(validRequest().build());

            postConfirm(rawTokenOf(userId))
                    .expectStatus()
                    .isNoContent();

            assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isTrue();
        }

        @Test
        void should_delete_the_token_row_when_the_confirmation_succeeds() {
            UUID userId = registerAndReturnId(validRequest().build());

            postConfirm(rawTokenOf(userId))
                    .expectStatus()
                    .isNoContent();

            assertThat(tokensOf(userId)).isEmpty();
        }

        @Test
        void should_return_204_without_a_cookie_when_the_confirmation_succeeds() {
            UUID userId = registerAndReturnId(validRequest().build());

            postConfirm(rawTokenOf(userId))
                    .expectStatus()
                    .isNoContent()
                    .expectHeader()
                    .doesNotExist(HttpHeaders.SET_COOKIE);
        }

        @Test
        void should_return_400_when_the_token_is_reused() {
            String rawToken = rawTokenOf(registerAndReturnId(validRequest().build()));
            postConfirm(rawToken)
                    .expectStatus()
                    .isNoContent();

            assertInvalidToken(postConfirm(rawToken));
        }

        @Test
        void should_return_400_when_the_token_is_unknown() {
            assertInvalidToken(postConfirm(confirmationTokenService.generateRawToken()));
        }

        @Test
        void should_return_400_when_the_token_is_malformed() {
            postConfirm("not-a-token")
                    .expectStatus()
                    .isBadRequest();
        }

        @Test
        void should_return_400_and_keep_the_user_disabled_when_the_token_is_expired() {
            UUID userId = registerAndReturnId(validRequest().build());
            String rawToken = rawTokenOf(userId);
            mutableClock.advance(confirmationTtl.plusSeconds(1));

            assertInvalidToken(postConfirm(rawToken));

            assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isFalse();
        }

        @Test
        void should_return_400_for_the_old_token_and_204_for_the_new_one_when_the_confirmation_was_resent() {
            UUID userId = registerAndReturnId(validRequest().build());
            String oldToken = rawTokenOf(userId);
            mutableClock.advance(resendCooldown.plusSeconds(1));
            postResend(userRepository.findById(userId).orElseThrow().getEmail())
                    .expectStatus()
                    .isAccepted();

            assertInvalidToken(postConfirm(oldToken));
            postConfirm(rawTokenOf(userId))
                    .expectStatus()
                    .isNoContent();
        }
    }

    @Nested
    class Resend {

        @AfterEach
        void resetClock() {
            mutableClock.reset();
        }

        @Test
        void should_return_202_and_enqueue_nothing_when_the_email_is_unknown() {
            String email = TestUsers.unique().getEmail();

            postResend(email)
                    .expectStatus()
                    .isAccepted();

            assertThat(outboxRepository.findAll()).noneMatch(row -> row.getPayload().contains(email));
        }

        @Test
        void should_return_202_and_enqueue_nothing_when_the_user_is_already_confirmed() {
            RegisterRequestDTO request = validRequest().build();
            UUID userId = registerAndReturnId(request);
            postConfirm(rawTokenOf(userId))
                    .expectStatus()
                    .isNoContent();
            mutableClock.advance(resendCooldown.plusSeconds(1));

            postResend(request.getEmail())
                    .expectStatus()
                    .isAccepted();

            assertThat(outboxRowsOf(userId)).hasSize(1);
            assertThat(tokensOf(userId)).isEmpty();
        }

        @Test
        void should_return_202_with_one_new_token_and_one_new_outbox_row_when_the_user_is_pending() {
            RegisterRequestDTO request = validRequest().build();
            UUID userId = registerAndReturnId(request);
            String oldHash = tokensOf(userId).getFirst().getTokenHash();
            mutableClock.advance(resendCooldown.plusSeconds(1));

            postResend(request.getEmail())
                    .expectStatus()
                    .isAccepted();

            assertThat(tokensOf(userId))
                    .hasSize(1)
                    .extracting(EmailConfirmationToken::getTokenHash)
                    .doesNotContain(oldHash);
            assertThat(outboxRowsOf(userId)).hasSize(2);
        }

        @Test
        void should_return_202_and_enqueue_nothing_when_resent_within_the_cooldown() {
            RegisterRequestDTO request = validRequest().build();
            UUID userId = registerAndReturnId(request);
            String hash = tokensOf(userId).getFirst().getTokenHash();

            postResend(request.getEmail())
                    .expectStatus()
                    .isAccepted();

            assertThat(outboxRowsOf(userId)).hasSize(1);
            assertThat(tokensOf(userId))
                    .extracting(EmailConfirmationToken::getTokenHash)
                    .containsExactly(hash);
        }

        @Test
        void should_return_202_and_enqueue_a_new_row_when_resent_after_the_cooldown() {
            RegisterRequestDTO request = validRequest().build();
            UUID userId = registerAndReturnId(request);
            postResend(request.getEmail())
                    .expectStatus()
                    .isAccepted();
            mutableClock.advance(resendCooldown.plusSeconds(1));

            postResend(request.getEmail())
                    .expectStatus()
                    .isAccepted();

            assertThat(outboxRowsOf(userId)).hasSize(2);
        }

        @Test
        void should_return_400_when_the_body_is_malformed() {
            restTestClient
                    .post()
                    .uri("/api/v1/auth/confirm/resend")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"email\":\"not-an-email\"}")
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }
    }

    @Nested
    class Login {

        @Test
        void should_return_200_and_a_cookie_when_logging_in_by_email() {
            RegisterRequestDTO request = validRequest().build();
            UUID userId = registerConfirmed(request);

            RestTestClient.ResponseSpec response = postLogin(request.getEmail(), request.getPassword());

            UserResponseDTO body = response
                    .expectStatus()
                    .isOk()
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseBody();
            assertThat(body.getId()).isEqualTo(userId);
            assertThat(body.getEmail()).isEqualTo(request.getEmail());
            assertThat(subjectOf(setCookieOf(response))).isEqualTo(userId.toString());
        }

        @Test
        void should_return_200_and_a_cookie_when_logging_in_by_username() {
            RegisterRequestDTO request = validRequest().build();
            registerConfirmed(request);

            postLogin(request.getUsername(), request.getPassword())
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .exists(HttpHeaders.SET_COOKIE);
        }

        @Test
        void should_return_200_and_a_cookie_when_the_username_differs_only_in_case() {
            RegisterRequestDTO request = validRequest().build();
            registerConfirmed(request);

            postLogin(request.getUsername().toUpperCase(), request.getPassword())
                    .expectStatus()
                    .isOk()
                    .expectHeader()
                    .exists(HttpHeaders.SET_COOKIE);
        }

        @Test
        void should_set_the_cookie_as_http_only_same_site_strict_with_max_age_equal_to_the_ttl() {
            RegisterRequestDTO request = validRequest().build();
            registerConfirmed(request);

            String cookie = setCookieOf(postLogin(request.getEmail(), request.getPassword()));

            assertThat(cookie)
                    .startsWith(COOKIE_NAME + "=")
                    .contains("HttpOnly")
                    .contains("SameSite=Strict")
                    .contains("Path=/")
                    .contains("Max-Age=" + jwtTtl.toSeconds());
        }

        @Test
        void should_return_401_when_the_password_is_wrong() {
            RegisterRequestDTO request = validRequest().build();
            registerConfirmed(request);

            assertInvalidCredentials(postLogin(request.getEmail(), "Wr0ngPassword"));
        }

        @Test
        void should_return_401_when_the_email_is_unknown() {
            assertInvalidCredentials(postLogin(TestUsers.unique().getEmail(), "Passw0rdOk1"));
        }

        @Test
        void should_return_401_when_the_username_is_unknown() {
            assertInvalidCredentials(postLogin(TestUsers.unique().getUsername(), "Passw0rdOk1"));
        }

        @Test
        void should_return_the_same_401_body_for_a_wrong_password_and_an_unknown_identifier() {
            RegisterRequestDTO request = validRequest().build();
            registerConfirmed(request);

            ErrorResponseDTO wrongPassword = errorOf(postLogin(request.getEmail(), "Wr0ngPassword"));
            ErrorResponseDTO unknownIdentifier = errorOf(postLogin(TestUsers.unique().getEmail(), "Wr0ngPassword"));

            assertThat(wrongPassword.getStatus()).isEqualTo(unknownIdentifier.getStatus());
            assertThat(wrongPassword.getMessages()).isEqualTo(unknownIdentifier.getMessages());
        }

        @ParameterizedTest
        @CsvSource({"'',Passw0rdOk1", "someone,''", "'   ',Passw0rdOk1", "someone,'   '"})
        void should_return_400_when_the_identifier_or_password_is_blank(String identifier, String password) {
            postLogin(identifier, password)
                    .expectStatus()
                    .isBadRequest();
        }

        @Test
        void should_return_400_when_the_password_is_over_72_bytes_even_with_fewer_characters() {
            postLogin(TestUsers.unique().getEmail(), "Aa1" + "é".repeat(60))
                    .expectStatus()
                    .isBadRequest();
        }

        @Test
        void should_return_400_when_the_body_is_malformed_json() {
            restTestClient
                    .post()
                    .uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"identifier\":")
                    .exchange()
                    .expectStatus()
                    .isBadRequest();
        }

        @Test
        void should_return_403_when_the_password_is_correct_but_the_user_is_unconfirmed() {
            RegisterRequestDTO request = validRequest().build();
            registerAndReturnId(request);

            RestTestClient.ResponseSpec response = postLogin(request.getEmail(), request.getPassword());

            assertThat(errorOf(response, HttpStatus.FORBIDDEN).getMessages())
                    .containsExactly("Please confirm your email first.");
            response
                    .expectHeader()
                    .doesNotExist(HttpHeaders.SET_COOKIE);
        }

        @Test
        void should_return_401_when_the_password_is_wrong_and_the_user_is_unconfirmed() {
            RegisterRequestDTO request = validRequest().build();
            registerAndReturnId(request);

            assertInvalidCredentials(postLogin(request.getEmail(), "Wr0ngPassword"));
        }

        private void assertInvalidCredentials(RestTestClient.ResponseSpec response) {
            assertThat(errorOf(response, HttpStatus.UNAUTHORIZED).getMessages())
                    .containsExactly("Invalid credentials.");
            response
                    .expectHeader()
                    .doesNotExist(HttpHeaders.SET_COOKIE);
        }

        private ErrorResponseDTO errorOf(RestTestClient.ResponseSpec response) {
            return errorOf(response, HttpStatus.UNAUTHORIZED);
        }

        private ErrorResponseDTO errorOf(RestTestClient.ResponseSpec response, HttpStatus status) {
            return response
                    .expectStatus()
                    .isEqualTo(status)
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();
        }
    }

    @Nested
    class Logout {

        @Test
        void should_return_204_with_a_clearing_cookie_when_logged_in() {
            postLogout(validCookie())
                    .expectStatus()
                    .isNoContent()
                    .expectCookie()
                    .maxAge(COOKIE_NAME, Duration.ZERO);
        }

        @Test
        void should_return_204_with_a_clearing_cookie_when_not_logged_in() {
            postLogout(null)
                    .expectStatus()
                    .isNoContent()
                    .expectCookie()
                    .maxAge(COOKIE_NAME, Duration.ZERO);
        }

        private RestTestClient.ResponseSpec postLogout(String cookie) {
            RestTestClient.RequestHeadersSpec<?> request = restTestClient
                    .post()
                    .uri("/api/v1/auth/logout");
            if (cookie != null) {
                request.cookie(COOKIE_NAME, cookie);
            }

            return request.exchange();
        }
    }

    @Nested
    class Me {

        @Test
        void should_return_200_with_the_claims_when_the_cookie_is_valid() {
            User user = TestEntities.newUser();
            user.setId(UUID.randomUUID());

            UserResponseDTO body = getMe(tokenService.mint(user))
                    .expectStatus()
                    .isOk()
                    .expectBody(UserResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isEqualTo(user.getId());
            assertThat(body.getUsername()).isEqualTo(user.getUsername());
            assertThat(body.getEmail()).isEqualTo(user.getEmail());
        }

        @Test
        void should_return_200_without_reading_the_database_when_the_cookie_is_valid() {
            User saved = userRepository.saveAndFlush(TestEntities.newUser());
            String token = tokenService.mint(saved);
            userRepository.deleteById(saved.getId());

            getMe(token)
                    .expectStatus()
                    .isOk();
        }

        @Test
        void should_return_401_when_there_is_no_cookie() {
            getMe(null)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_signature_is_tampered() {
            String token = TestJwts.withTamperedSignature(validCookie());

            getMe(token)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_token_is_expired() {
            Instant issuedAt = Instant.now().minusSeconds(7200);
            String token = TestJwts.sign(TestJwts.validClaims()
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(issuedAt.plusSeconds(3600))), jwtSecret);

            getMe(token)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_subject_is_not_a_uuid() {
            String token = TestJwts.sign(TestJwts.validClaims().subject("not-a-uuid"), jwtSecret);

            getMe(token)
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_401_when_the_username_claim_is_missing() {
            String token = TestJwts.sign(TestJwts.validClaims().claim("username", null), jwtSecret);

            getMe(token)
                    .expectStatus()
                    .isUnauthorized();
        }

        private RestTestClient.ResponseSpec getMe(String cookie) {
            RestTestClient.RequestHeadersSpec<?> request = restTestClient
                    .get()
                    .uri("/api/v1/auth/me");
            if (cookie != null) {
                request.cookie(COOKIE_NAME, cookie);
            }

            return request.exchange();
        }
    }

    @Nested
    class Security {

        @ParameterizedTest
        @CsvSource({
                "GET,/api/v1/users/someone",
                "POST,/api/v1/tweets",
                "DELETE,/api/v1/anything"
        })
        void should_return_401_when_an_authenticated_route_is_called_without_a_cookie(String method, String path) {
            restTestClient
                    .method(HttpMethod.valueOf(method))
                    .uri(path)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized();
        }

        @Test
        void should_return_200_when_the_health_endpoint_is_called_without_a_cookie() {
            restTestClient
                    .get()
                    .uri("/actuator/health")
                    .exchange()
                    .expectStatus()
                    .isOk();
        }
    }

    private String validCookie() {
        return TestJwts.sign(TestJwts.validClaims(), jwtSecret);
    }

    private RegisterRequestDTO.RegisterRequestDTOBuilder validRequest() {
        TestUser user = TestUsers.unique();

        return RegisterRequestDTO.builder()
                .username(user.getUsername())
                .email(user.getEmail())
                .password(user.getPassword());
    }

    private RestTestClient.ResponseSpec register(RegisterRequestDTO request) {
        return restTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private RestTestClient.ResponseSpec registerRaw(String json) {
        return restTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json)
                .exchange();
    }

    private UUID registerAndReturnId(RegisterRequestDTO request) {
        return register(request)
                .expectStatus()
                .isCreated()
                .expectBody(UserResponseDTO.class)
                .returnResult()
                .getResponseBody()
                .getId();
    }

    private RestTestClient.ResponseSpec postLogin(String identifier, String password) {
        return restTestClient
                .post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(LoginRequestDTO.builder().identifier(identifier).password(password).build())
                .exchange();
    }

    private UUID registerConfirmed(RegisterRequestDTO request) {
        UUID userId = registerAndReturnId(request);
        postConfirm(rawTokenOf(userId))
                .expectStatus()
                .isNoContent();

        return userId;
    }

    private String setCookieOf(RestTestClient.ResponseSpec response) {
        return response
                .expectHeader()
                .exists(HttpHeaders.SET_COOKIE)
                .returnResult(String.class)
                .getResponseHeaders()
                .getFirst(HttpHeaders.SET_COOKIE);
    }

    private String subjectOf(String setCookie) {
        String token = setCookie.substring(COOKIE_NAME.length() + 1, setCookie.indexOf(';'));

        return jwtDecoder.decode(token).getSubject();
    }

    private RestTestClient.ResponseSpec postConfirm(String token) {
        return restTestClient
                .post()
                .uri("/api/v1/auth/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ConfirmRequestDTO.builder().token(token).build())
                .exchange();
    }

    private RestTestClient.ResponseSpec postResend(String email) {
        return restTestClient
                .post()
                .uri("/api/v1/auth/confirm/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ResendConfirmationRequestDTO.builder().email(email).build())
                .exchange();
    }

    private void assertInvalidToken(RestTestClient.ResponseSpec response) {
        ErrorResponseDTO body = response
                .expectStatus()
                .isBadRequest()
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getMessages()).containsExactly("Invalid or expired confirmation token.");
    }

    private String bodyOf(RestTestClient.ResponseSpec response) {
        return response
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();
    }

    private void assertConflict(RestTestClient.ResponseSpec response, String message) {
        ErrorResponseDTO body = response
                .expectStatus()
                .isEqualTo(HttpStatus.CONFLICT)
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getMessages()).containsExactly(message);
    }

    private void assertRejectedNaming(RegisterRequestDTO request, String field) {
        ErrorResponseDTO body = register(request)
                .expectStatus()
                .isBadRequest()
                .expectBody(ErrorResponseDTO.class)
                .returnResult()
                .getResponseBody();

        assertThat(body.getMessages()).anyMatch(message -> message.startsWith(field + " "));
    }

    private List<Outbox> outboxRowsOf(UUID userId) {
        return outboxRepository
                .findAll()
                .stream()
                .filter(row -> row.getMessageKey().equals(userId.toString()))
                .toList();
    }

    private Outbox latestOutboxRowOf(UUID userId) {
        return outboxRowsOf(userId)
                .stream()
                .max(Comparator.comparing(Outbox::getCreatedAt))
                .orElseThrow();
    }

    private List<EmailConfirmationToken> tokensOf(UUID userId) {
        return tokenRepository
                .findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .toList();
    }

    private String rawTokenOf(UUID userId) {
        String confirmationUrl = objectMapper
                .readTree(latestOutboxRowOf(userId).getPayload())
                .get("confirmationUrl")
                .asString();

        return confirmationUrl.substring(confirmationUrl.indexOf("?token=") + "?token=".length());
    }
}
