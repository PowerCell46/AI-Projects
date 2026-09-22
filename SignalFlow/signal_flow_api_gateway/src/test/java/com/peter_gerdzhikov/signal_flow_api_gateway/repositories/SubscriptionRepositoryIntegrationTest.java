package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SubscriptionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String PASSWORD = "hashed-password";

    private static final UUID INTEREST_TOPIC_ID = UUID.randomUUID();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void should_save_and_find_a_subscription_by_user_and_topic() {
        User user = userRepository.save(newUser("bob@example.com"));

        subscriptionRepository.save(newSubscription(user, INTEREST_TOPIC_ID));

        Optional<Subscription> found =
                subscriptionRepository.findByUser_IdAndInterestTopicId(user.getId(), INTEREST_TOPIC_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getInterestTopicId()).isEqualTo(INTEREST_TOPIC_ID);
        assertThat(found.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    void should_reject_a_second_subscription_to_the_same_topic_by_the_same_user() {
        User user = userRepository.save(newUser("bob@example.com"));
        subscriptionRepository.saveAndFlush(newSubscription(user, INTEREST_TOPIC_ID));

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(newSubscription(user, INTEREST_TOPIC_ID)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_allow_two_users_to_subscribe_to_the_same_topic() {
        User bob = userRepository.save(newUser("bob@example.com"));
        User alice = userRepository.save(newUser("alice@example.com"));

        subscriptionRepository.saveAndFlush(newSubscription(bob, INTEREST_TOPIC_ID));
        subscriptionRepository.saveAndFlush(newSubscription(alice, INTEREST_TOPIC_ID));

        assertThat(subscriptionRepository.countByUser_Id(bob.getId())).isEqualTo(1);
        assertThat(subscriptionRepository.countByUser_Id(alice.getId())).isEqualTo(1);
    }

    @Test
    void should_count_only_the_given_users_subscriptions() {
        User bob = userRepository.save(newUser("bob@example.com"));
        User alice = userRepository.save(newUser("alice@example.com"));

        subscriptionRepository.save(newSubscription(bob, INTEREST_TOPIC_ID));
        subscriptionRepository.save(newSubscription(bob, UUID.randomUUID()));
        subscriptionRepository.save(newSubscription(alice, INTEREST_TOPIC_ID));

        assertThat(subscriptionRepository.countByUser_Id(bob.getId())).isEqualTo(2);
        assertThat(subscriptionRepository.countByUser_Id(alice.getId())).isEqualTo(1);
    }

    @Test
    void should_report_an_existing_subscription() {
        User user = userRepository.save(newUser("bob@example.com"));
        subscriptionRepository.save(newSubscription(user, INTEREST_TOPIC_ID));

        assertThat(subscriptionRepository.existsByUser_IdAndInterestTopicId(user.getId(), INTEREST_TOPIC_ID))
                .isTrue();
    }

    @Test
    void should_not_report_another_users_subscription_as_existing() {
        User bob = userRepository.save(newUser("bob@example.com"));
        User alice = userRepository.save(newUser("alice@example.com"));
        subscriptionRepository.save(newSubscription(bob, INTEREST_TOPIC_ID));

        assertThat(subscriptionRepository.existsByUser_IdAndInterestTopicId(alice.getId(), INTEREST_TOPIC_ID))
                .isFalse();
    }

    private User newUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(PASSWORD);
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
