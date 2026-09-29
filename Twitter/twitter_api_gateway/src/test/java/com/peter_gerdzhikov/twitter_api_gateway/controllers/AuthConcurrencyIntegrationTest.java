package com.peter_gerdzhikov.twitter_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.client.RestTestClient;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.ConfirmRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.request.ResendConfirmationRequestDTO;
import com.peter_gerdzhikov.twitter_api_gateway.DTOs.response.auth.UserResponseDTO;
import com.peter_gerdzhikov.twitter_api_gateway.entities.EmailConfirmationToken;
import com.peter_gerdzhikov.twitter_api_gateway.entities.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.EmailConfirmationTokenRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_api_gateway.support.MutableClock;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUser;
import com.peter_gerdzhikov.twitter_api_gateway.support.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class AuthConcurrencyIntegrationTest extends AbstractMinioIntegrationTest {

    private static final int PARALLEL_CALLS = 8;

    private static final long TIMEOUT_SECONDS = 60;

    @Value("${app.confirmation.resend-cooldown}")
    private Duration resendCooldown;

    @Autowired
    private MutableClock mutableClock;

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

    @AfterEach
    void resetClock() {
        mutableClock.reset();
    }

    @Test
    void should_give_exactly_one_201_and_the_rest_409_when_the_same_email_registers_in_parallel() throws Exception {
        TestUser shared = TestUsers.unique();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_CALLS; i++) {
            TestUser other = TestUsers.unique();
            calls.add(() -> register(other.getUsername(), shared.getEmail(), other.getPassword()));
        }

        List<Integer> statuses = runInParallel(calls);

        assertExactlyOneWinner(statuses, 201, 409);
        assertThat(userRepository.findByEmail(shared.getEmail())).isPresent();
    }

    @Test
    void should_give_exactly_one_201_and_the_rest_409_when_usernames_differing_only_in_case_register_in_parallel()
            throws Exception {
        String base = "Cc" + TestUsers.unique().getUsername();
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_CALLS; i++) {
            TestUser other = TestUsers.unique();
            String username = i % 2 == 0 ? base.toLowerCase() : base.toUpperCase();
            calls.add(() -> register(username, other.getEmail(), other.getPassword()));
        }

        List<Integer> statuses = runInParallel(calls);

        assertExactlyOneWinner(statuses, 201, 409);
        assertThat(userRepository.findByUsernameNormalized(base.toLowerCase())).isPresent();
    }

    @Test
    void should_give_exactly_one_204_when_one_token_is_confirmed_in_parallel() throws Exception {
        UUID userId = registerAndReturnId(TestUsers.unique());
        String rawToken = rawTokenOf(userId);
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_CALLS; i++) {
            calls.add(() -> confirm(rawToken));
        }

        List<Integer> statuses = runInParallel(calls);

        assertExactlyOneWinner(statuses, 204, 400);
        assertThat(userRepository.findById(userId).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void should_create_exactly_one_new_token_and_one_outbox_row_when_resent_in_parallel() throws Exception {
        TestUser user = TestUsers.unique();
        UUID userId = registerAndReturnId(user);
        mutableClock.advance(resendCooldown.plusSeconds(1));
        List<Callable<Integer>> calls = new ArrayList<>();
        for (int i = 0; i < PARALLEL_CALLS; i++) {
            calls.add(() -> resend(user.getEmail()));
        }

        List<Integer> statuses = runInParallel(calls);

        assertThat(statuses).containsOnly(202);
        assertThat(tokensOf(userId)).hasSize(1);
        assertThat(outboxRowsOf(userId)).hasSize(2);
    }

    private void assertExactlyOneWinner(List<Integer> statuses, int winner, int loser) {
        assertThat(statuses.stream().filter(status -> status == winner)).hasSize(1);
        assertThat(statuses.stream().filter(status -> status == loser)).hasSize(PARALLEL_CALLS - 1);
    }

    /**
     * Releases every call at once through a latch, so the requests overlap instead of running in submission
     * order.
     */
    private List<Integer> runInParallel(List<Callable<Integer>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> call : calls) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();

            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return statuses;

        } finally {
            executor.shutdownNow();
        }
    }

    private int register(String username, String email, String password) {
        return statusOf(restTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(RegisterRequestDTO.builder().username(username).email(email).password(password).build())
                .exchange());
    }

    private int confirm(String token) {
        return statusOf(restTestClient
                .post()
                .uri("/api/v1/auth/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ConfirmRequestDTO.builder().token(token).build())
                .exchange());
    }

    private int resend(String email) {
        return statusOf(restTestClient
                .post()
                .uri("/api/v1/auth/confirm/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ResendConfirmationRequestDTO.builder().email(email).build())
                .exchange());
    }

    private int statusOf(RestTestClient.ResponseSpec response) {
        return response
                .returnResult(String.class)
                .getStatus()
                .value();
    }

    private UUID registerAndReturnId(TestUser user) {
        return restTestClient
                .post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(RegisterRequestDTO.builder()
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .password(user.getPassword())
                        .build())
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(UserResponseDTO.class)
                .returnResult()
                .getResponseBody()
                .getId();
    }

    private List<Outbox> outboxRowsOf(UUID userId) {
        return outboxRepository
                .findAll()
                .stream()
                .filter(row -> row.getMessageKey().equals(userId.toString()))
                .toList();
    }

    private List<EmailConfirmationToken> tokensOf(UUID userId) {
        return tokenRepository
                .findAll()
                .stream()
                .filter(token -> token.getUser().getId().equals(userId))
                .toList();
    }

    private String rawTokenOf(UUID userId) {
        Outbox newest = outboxRowsOf(userId)
                .stream()
                .max(Comparator.comparing(Outbox::getCreatedAt))
                .orElseThrow();
        String confirmationUrl = objectMapper
                .readTree(newest.getPayload())
                .get("confirmationUrl")
                .asString();

        return confirmationUrl.substring(confirmationUrl.indexOf("?token=") + "?token=".length());
    }
}
