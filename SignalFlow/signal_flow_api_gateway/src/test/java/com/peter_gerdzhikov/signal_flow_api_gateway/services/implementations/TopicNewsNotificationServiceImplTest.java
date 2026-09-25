package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.TopicNewsInbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.NotificationOutboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.TopicNewsInboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TopicNewsNotificationServiceImplTest extends AbstractPostgresIntegrationTest {

    private static final int BATCH_SIZE = 2;

    private static final int MAX_DATA_LENGTH = 20;

    private static final String PASSWORD = "hashed-password";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private TopicNewsInboxRepository topicNewsInboxRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Autowired
    private DataSource dataSource;

    private TopicNewsNotificationServiceImpl topicNewsNotificationService;

    @BeforeEach
    void setUp() {
        topicNewsNotificationService = new TopicNewsNotificationServiceImpl(
                BATCH_SIZE, MAX_DATA_LENGTH, subscriptionRepository, topicNewsInboxRepository,
                new JdbcTemplate(dataSource));
    }

    @Nested
    class NotifySubscribers {

        @Test
        void should_queue_one_outbox_row_per_enabled_subscriber_and_mark_the_news_processed() {
            UUID interestTopicId = UUID.randomUUID();
            User bob = userRepository.save(newUser("bob@example.com", true));
            User alice = userRepository.save(newUser("alice@example.com", true));
            User carl = userRepository.save(newUser("carl@example.com", false));
            subscriptionRepository.save(newSubscription(bob, interestTopicId));
            subscriptionRepository.save(newSubscription(alice, interestTopicId));
            subscriptionRepository.save(newSubscription(carl, interestTopicId));
            TopicNewsEventDTO event = newEvent(interestTopicId);

            topicNewsNotificationService.notifySubscribers(event);

            assertThat(topicNewsInboxRepository.existsById(event.getNewsId())).isTrue();
            List<NotificationOutbox> queued = notificationOutboxRepository.findAll();
            assertThat(queued)
                    .extracting(NotificationOutbox::getUserId)
                    .containsExactlyInAnyOrder(bob.getId(), alice.getId());

            NotificationOutbox row = queued.getFirst();
            assertThat(row.getNewsId()).isEqualTo(event.getNewsId());
            assertThat(row.getInterestTopicId()).isEqualTo(event.getInterestTopicId());
            assertThat(row.getTopicName()).isEqualTo(event.getTopicName());
            assertThat(row.getCategoryName()).isEqualTo(event.getCategoryName());
            assertThat(row.getNewsDate()).isEqualTo(event.getNewsDate());
            assertThat(row.getData()).isEqualTo(event.getData());
            assertThat(row.getGeneratedAt()).isEqualTo(event.getGeneratedAt());
            assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
            assertThat(row.getAttemptCount()).isZero();
        }

        @Test
        void should_walk_every_batch_of_subscribers() {
            UUID interestTopicId = UUID.randomUUID();
            List<User> subscribers = Stream
                    .generate(() -> userRepository.save(newUser(UUID.randomUUID() + "@example.com", true)))
                    .limit(5)
                    .toList();
            subscribers.forEach(user -> subscriptionRepository.save(newSubscription(user, interestTopicId)));

            topicNewsNotificationService.notifySubscribers(newEvent(interestTopicId));

            assertThat(notificationOutboxRepository.findAll())
                    .extracting(NotificationOutbox::getUserId)
                    .containsExactlyInAnyOrderElementsOf(subscribers.stream().map(User::getId).toList());
        }

        @Test
        void should_queue_nothing_but_still_mark_the_news_processed_when_there_are_no_subscribers() {
            TopicNewsEventDTO event = newEvent(UUID.randomUUID());

            topicNewsNotificationService.notifySubscribers(event);

            assertThat(topicNewsInboxRepository.existsById(event.getNewsId())).isTrue();
            assertThat(notificationOutboxRepository.findAll()).isEmpty();
        }

        @Test
        void should_skip_an_already_processed_news_id() {
            UUID interestTopicId = UUID.randomUUID();
            User bob = userRepository.save(newUser("bob@example.com", true));
            subscriptionRepository.save(newSubscription(bob, interestTopicId));
            TopicNewsEventDTO event = newEvent(interestTopicId);
            topicNewsInboxRepository.save(newInbox(event.getNewsId()));

            topicNewsNotificationService.notifySubscribers(event);

            assertThat(notificationOutboxRepository.findAll()).isEmpty();
        }

        @Test
        void should_queue_nothing_but_still_mark_the_news_processed_when_data_exceeds_the_length_cap() {
            UUID interestTopicId = UUID.randomUUID();
            User bob = userRepository.save(newUser("bob@example.com", true));
            subscriptionRepository.save(newSubscription(bob, interestTopicId));
            TopicNewsEventDTO event = newEvent(interestTopicId, "x".repeat(MAX_DATA_LENGTH + 1));

            topicNewsNotificationService.notifySubscribers(event);

            assertThat(topicNewsInboxRepository.existsById(event.getNewsId())).isTrue();
            assertThat(notificationOutboxRepository.findAll()).isEmpty();
        }

        @Test
        void should_queue_nothing_but_still_mark_the_news_processed_when_the_topic_name_exceeds_the_column_cap() {
            UUID interestTopicId = UUID.randomUUID();
            User bob = userRepository.save(newUser("bob@example.com", true));
            subscriptionRepository.save(newSubscription(bob, interestTopicId));
            TopicNewsEventDTO event = newEvent(interestTopicId);
            event.setTopicName("x".repeat(256));

            topicNewsNotificationService.notifySubscribers(event);

            assertThat(topicNewsInboxRepository.existsById(event.getNewsId())).isTrue();
            assertThat(notificationOutboxRepository.findAll()).isEmpty();
        }

        @Test
        void should_queue_nothing_but_still_mark_the_news_processed_when_the_category_name_exceeds_the_column_cap() {
            UUID interestTopicId = UUID.randomUUID();
            User bob = userRepository.save(newUser("bob@example.com", true));
            subscriptionRepository.save(newSubscription(bob, interestTopicId));
            TopicNewsEventDTO event = newEvent(interestTopicId);
            event.setCategoryName("x".repeat(256));

            topicNewsNotificationService.notifySubscribers(event);

            assertThat(topicNewsInboxRepository.existsById(event.getNewsId())).isTrue();
            assertThat(notificationOutboxRepository.findAll()).isEmpty();
        }
    }

    private User newUser(String email, boolean enabled) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(PASSWORD);
        user.setRole(Role.USER);
        user.setEnabled(enabled);
        return user;
    }

    private Subscription newSubscription(User user, UUID interestTopicId) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setInterestTopicId(interestTopicId);
        return subscription;
    }

    private TopicNewsInbox newInbox(UUID newsId) {
        TopicNewsInbox inbox = new TopicNewsInbox();
        inbox.setNewsId(newsId);
        return inbox;
    }

    private TopicNewsEventDTO newEvent(UUID interestTopicId) {
        return newEvent(interestTopicId, "today's rust news");
    }

    private TopicNewsEventDTO newEvent(UUID interestTopicId, String data) {
        return new TopicNewsEventDTO(
                UUID.randomUUID(),
                interestTopicId,
                "rust",
                "programming",
                LocalDate.now(),
                data,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
