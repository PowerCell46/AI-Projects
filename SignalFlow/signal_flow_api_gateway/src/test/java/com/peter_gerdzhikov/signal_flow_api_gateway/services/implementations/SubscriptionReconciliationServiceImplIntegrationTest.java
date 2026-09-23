package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.SubscriptionReconciliationService;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractInterestTopicServiceIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class SubscriptionReconciliationServiceImplIntegrationTest extends AbstractInterestTopicServiceIntegrationTest {

    private static final String EXISTING_PATH = "/internal/v1/interest-topics/existing";

    private static final UUID EXISTING_TOPIC_ID = UUID.randomUUID();

    private static final UUID MISSING_TOPIC_ID = UUID.randomUUID();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private SubscriptionReconciliationService subscriptionReconciliationService;

    @BeforeEach
    void subscribeToOneExistingAndOneMissingTopic() {
        INTEREST_TOPIC_SERVICE_STUB.resetMappings();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();

        User user = userRepository.save(newUser());
        subscriptionRepository.save(newSubscription(user, EXISTING_TOPIC_ID));
        subscriptionRepository.save(newSubscription(user, MISSING_TOPIC_ID));
    }

    @Test
    void should_delete_the_subscription_to_the_missing_topic_and_keep_the_other() {
        stubExistenceLookup(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"existingIds\": [\"%s\"]}".formatted(EXISTING_TOPIC_ID)));

        subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

        assertThat(subscriptionRepository.findAll())
                .extracting(Subscription::getInterestTopicId)
                .containsExactly(EXISTING_TOPIC_ID);
    }

    @Test
    void should_delete_nothing_when_the_interest_topic_service_fails() {
        stubExistenceLookup(aResponse().withStatus(503));

        subscriptionReconciliationService.deleteSubscriptionsToMissingTopics();

        assertThat(subscriptionRepository.count()).isEqualTo(2);
    }

    private void stubExistenceLookup(ResponseDefinitionBuilder response) {
        INTEREST_TOPIC_SERVICE_STUB.register(post(urlEqualTo(EXISTING_PATH)).willReturn(response));
    }

    private User newUser() {
        User user = new User();
        user.setEmail("bob@example.com");
        user.setPassword("hashed-password");
        user.setRole(Role.USER);
        return user;
    }

    private Subscription newSubscription(User user, UUID interestTopicId) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setInterestTopicId(interestTopicId);
        return subscription;
    }
}
