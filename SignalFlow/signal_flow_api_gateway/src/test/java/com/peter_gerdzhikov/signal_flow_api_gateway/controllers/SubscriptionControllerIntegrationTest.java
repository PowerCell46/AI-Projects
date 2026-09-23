package com.peter_gerdzhikov.signal_flow_api_gateway.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.RegisterRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.request.SubscribeRequestDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.auth.UserResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.response.subscriptions.SubscriptionResponseDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
@ActiveProfiles("test")
class SubscriptionControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String COOKIE_NAME = "access_token";
    private static final String EMAIL = "user@example.com";
    private static final String OTHER_EMAIL = "other@example.com";
    private static final String PASSWORD = "Password123";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Value("${app.subscriptions.max-per-user}")
    private int maxSubscriptionsPerUser;

    @BeforeEach
    void clearSubscriptionsAndUsers() {
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    class ListSubscriptions {

        @Test
        void should_return_the_callers_subscriptions_newest_first() {
            String cookie = registerAndGetCookie(EMAIL);
            UUID firstTopicId = UUID.randomUUID();
            UUID secondTopicId = UUID.randomUUID();
            subscribe(cookie, firstTopicId).expectStatus().isCreated();
            subscribe(cookie, secondTopicId).expectStatus().isCreated();

            List<SubscriptionResponseDTO> body = listSubscriptions(cookie);

            assertThat(body).extracting(SubscriptionResponseDTO::getInterestTopicId)
                    .containsExactly(secondTopicId, firstTopicId);
        }

        @Test
        void should_return_an_empty_list_when_the_caller_has_no_subscriptions() {
            String cookie = registerAndGetCookie(EMAIL);

            assertThat(listSubscriptions(cookie)).isEmpty();
        }

        @Test
        void should_not_return_another_users_subscriptions() {
            String cookie = registerAndGetCookie(EMAIL);
            String otherCookie = registerAndGetCookie(OTHER_EMAIL);
            UUID interestTopicId = UUID.randomUUID();
            subscribe(otherCookie, interestTopicId).expectStatus().isCreated();

            assertThat(listSubscriptions(cookie)).isEmpty();
            assertThat(listSubscriptions(otherCookie)).extracting(SubscriptionResponseDTO::getInterestTopicId)
                    .containsExactly(interestTopicId);
        }

        @Test
        void should_return_401_with_no_cookie() {
            restTestClient.get()
                    .uri("/api/v1/subscriptions")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    class Subscribe {

        @Test
        void should_create_a_subscription_and_return_it() {
            String cookie = registerAndGetCookie(EMAIL);
            UUID interestTopicId = UUID.randomUUID();

            SubscriptionResponseDTO body = subscribe(cookie, interestTopicId)
                    .expectStatus().isCreated()
                    .expectBody(SubscriptionResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isNotNull();
            assertThat(body.getInterestTopicId()).isEqualTo(interestTopicId);
            assertThat(body.getCreatedAt()).isNotNull();
        }

        @Test
        void should_attach_the_subscription_to_the_authenticated_caller() {
            String cookie = registerAndGetCookie(EMAIL);
            UUID interestTopicId = UUID.randomUUID();

            subscribe(cookie, interestTopicId).expectStatus().isCreated();

            UUID callerId = userRepository.findByEmail(EMAIL).orElseThrow().getId();
            Subscription saved = subscriptionRepository
                    .findByUser_IdAndInterestTopicId(callerId, interestTopicId)
                    .orElseThrow();

            assertThat(saved.getUser().getId()).isEqualTo(callerId);
        }

        @Test
        void should_allow_two_users_to_subscribe_to_the_same_topic() {
            String cookie = registerAndGetCookie(EMAIL);
            String otherCookie = registerAndGetCookie(OTHER_EMAIL);
            UUID interestTopicId = UUID.randomUUID();

            subscribe(cookie, interestTopicId).expectStatus().isCreated();
            subscribe(otherCookie, interestTopicId).expectStatus().isCreated();

            assertThat(subscriptionRepository.count()).isEqualTo(2);
        }

        @Test
        void should_return_409_when_already_subscribed() {
            String cookie = registerAndGetCookie(EMAIL);
            UUID interestTopicId = UUID.randomUUID();
            subscribe(cookie, interestTopicId).expectStatus().isCreated();

            subscribe(cookie, interestTopicId).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_409_when_the_subscription_limit_is_reached() {
            String cookie = registerAndGetCookie(EMAIL);
            for (int i = 0; i < maxSubscriptionsPerUser; i++) {
                subscribe(cookie, UUID.randomUUID()).expectStatus().isCreated();
            }

            subscribe(cookie, UUID.randomUUID()).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_for_a_missing_interest_topic_id() {
            String cookie = registerAndGetCookie(EMAIL);

            subscribe(cookie, null).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_malformed_json() {
            String cookie = registerAndGetCookie(EMAIL);

            restTestClient.post()
                    .uri("/api/v1/subscriptions")
                    .cookie(COOKIE_NAME, cookie)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ not valid json")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void should_return_401_with_no_cookie() {
            restTestClient.post()
                    .uri("/api/v1/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(subscribeRequest(UUID.randomUUID()))
                    .exchange()
                    .expectStatus().isUnauthorized();
        }

        @Test
        void should_not_leak_exception_or_package_names_in_the_error_body() {
            String cookie = registerAndGetCookie(EMAIL);

            ErrorResponseDTO body = subscribe(cookie, null)
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
        }
    }

    @Nested
    class Unsubscribe {

        @Test
        void should_return_204_and_remove_the_subscription() {
            String cookie = registerAndGetCookie(EMAIL);
            UUID interestTopicId = UUID.randomUUID();
            subscribe(cookie, interestTopicId).expectStatus().isCreated();

            unsubscribe(cookie, interestTopicId.toString()).expectStatus().isNoContent();

            assertThat(subscriptionRepository.count()).isZero();
        }

        @Test
        void should_return_404_when_not_subscribed() {
            String cookie = registerAndGetCookie(EMAIL);

            unsubscribe(cookie, UUID.randomUUID().toString()).expectStatus().isNotFound();
        }

        @Test
        void should_return_404_when_the_subscription_belongs_to_another_user() {
            String cookie = registerAndGetCookie(EMAIL);
            String otherCookie = registerAndGetCookie(OTHER_EMAIL);
            UUID interestTopicId = UUID.randomUUID();
            subscribe(cookie, interestTopicId).expectStatus().isCreated();

            unsubscribe(otherCookie, interestTopicId.toString()).expectStatus().isNotFound();

            assertThat(subscriptionRepository.count()).isEqualTo(1);
        }

        @Test
        void should_return_400_for_a_malformed_interest_topic_id() {
            String cookie = registerAndGetCookie(EMAIL);

            ErrorResponseDTO body = unsubscribe(cookie, "not-a-uuid")
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "UUID", "com.peter_gerdzhikov");
        }

        @Test
        void should_return_401_with_no_cookie() {
            restTestClient.delete()
                    .uri("/api/v1/subscriptions/" + UUID.randomUUID())
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    private List<SubscriptionResponseDTO> listSubscriptions(String cookie) {
        SubscriptionResponseDTO[] body = restTestClient.get()
                .uri("/api/v1/subscriptions")
                .cookie(COOKIE_NAME, cookie)
                .exchange()
                .expectStatus().isOk()
                .expectBody(SubscriptionResponseDTO[].class)
                .returnResult()
                .getResponseBody();

        return List.of(body);
    }

    private RestTestClient.ResponseSpec subscribe(String cookie, UUID interestTopicId) {
        return restTestClient.post()
                .uri("/api/v1/subscriptions")
                .cookie(COOKIE_NAME, cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .body(subscribeRequest(interestTopicId))
                .exchange();
    }

    private RestTestClient.ResponseSpec unsubscribe(String cookie, String interestTopicId) {
        return restTestClient.delete()
                .uri("/api/v1/subscriptions/" + interestTopicId)
                .cookie(COOKIE_NAME, cookie)
                .exchange();
    }

    private SubscribeRequestDTO subscribeRequest(UUID interestTopicId) {
        SubscribeRequestDTO request = new SubscribeRequestDTO();
        request.setInterestTopicId(interestTopicId);
        return request;
    }

    // Duplicated from AuthControllerIntegrationTest rather than refactoring a green suite - extract to
    // support/ at the third caller (see PLAN.md's subscriptions step 4).
    private String registerAndGetCookie(String email) {
        RegisterRequestDTO request = new RegisterRequestDTO();
        request.setEmail(email);
        request.setPassword(PASSWORD);

        return restTestClient.post()
                .uri("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponseDTO.class)
                .returnResult()
                .getResponseCookies()
                .getFirst(COOKIE_NAME)
                .getValue();
    }
}
