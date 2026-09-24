package com.peter_gerdzhikov.signal_flow_api_gateway.listeners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.Subscription;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.User;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.Role;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.NotificationOutboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.TopicNewsInboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.UserRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractKafkaIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class TopicNewsListenerIntegrationTest extends AbstractKafkaIntegrationTest {

    private static final String PASSWORD = "hashed-password";

    /**
     * Same literal as {@code app.kafka.topic-news.name}'s default - a static {@code @BeforeAll} runs
     * before the Spring context, and its {@code @Value}-injected fields, exist.
     */
    private static final String TOPIC_NEWS_TOPIC_NAME = "topic-news.generated";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private TopicNewsInboxRepository topicNewsInboxRepository;

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Value("${app.kafka.topic-news.name}")
    private String topicNewsTopicName;

    private Producer<String, String> producer;

    private Consumer<String, String> deadLetterConsumer;

    /**
     * Pre-creates {@code topic-news.generated} with a known, single partition before the listener container
     * ever subscribes to it. In production the topic service owns this topic's creation; left to Kafka's own
     * auto-create, the partition count is whatever the broker defaults to and the first send races the
     * listener's first rebalance - both make the fan-out assertions below non-deterministic.
     */
    @BeforeAll
    static void createTopicNewsTopic() throws ExecutionException, InterruptedException, TimeoutException {
        try (AdminClient adminClient = AdminClient.create(
                Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers()))) {
            adminClient
                    .createTopics(List.of(new NewTopic(TOPIC_NEWS_TOPIC_NAME, 1, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);

        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw e;
            }
        }
    }

    @BeforeEach
    void setUp() {
        notificationOutboxRepository.deleteAll();
        topicNewsInboxRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();

        producer = new DefaultKafkaProducerFactory<>(
                KafkaTestUtils.producerProps(kafkaBootstrapServers()), new StringSerializer(), new StringSerializer())
                .createProducer();

        deadLetterConsumer = newRawConsumer("topic-news-listener-test-dlt", topicNewsTopicName + "-dlt");
    }

    @AfterEach
    void tearDown() {
        producer.close();
        deadLetterConsumer.close();

        notificationOutboxRepository.deleteAll();
        topicNewsInboxRepository.deleteAll();
        subscriptionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void should_queue_one_outbox_row_per_enabled_subscriber_and_mark_the_news_processed() throws Exception {
        UUID interestTopicId = UUID.randomUUID();
        User bob = userRepository.save(newUser("bob@example.com", true));
        User alice = userRepository.save(newUser("alice@example.com", true));
        User carl = userRepository.save(newUser("carl@example.com", false));
        subscriptionRepository.save(newSubscription(bob, interestTopicId));
        subscriptionRepository.save(newSubscription(alice, interestTopicId));
        subscriptionRepository.save(newSubscription(carl, interestTopicId));

        UUID newsId = UUID.randomUUID();
        LocalDate newsDate = LocalDate.now();
        Instant generatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        producer
                .send(new ProducerRecord<>(topicNewsTopicName, interestTopicId.toString(),
                        topicNewsEventJson(newsId, interestTopicId, newsDate, generatedAt)))
                .get(10, TimeUnit.SECONDS);

        await().atMost(Duration.ofSeconds(30))
                .untilAsserted(() -> assertThat(topicNewsInboxRepository.existsById(newsId)).isTrue());

        List<NotificationOutbox> queued = notificationOutboxRepository.findAll();
        assertThat(queued)
                .extracting(NotificationOutbox::getUserId)
                .containsExactlyInAnyOrder(bob.getId(), alice.getId());

        for (NotificationOutbox row : queued) {
            assertThat(row.getNewsId()).isEqualTo(newsId);
            assertThat(row.getInterestTopicId()).isEqualTo(interestTopicId);
            assertThat(row.getTopicName()).isEqualTo("rust");
            assertThat(row.getCategoryName()).isEqualTo("programming");
            assertThat(row.getNewsDate()).isEqualTo(newsDate);
            assertThat(row.getData()).isEqualTo("today's rust news");
            assertThat(row.getGeneratedAt()).isEqualTo(generatedAt);
            assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);

            User expectedSubscriber = row.getUserId().equals(bob.getId()) ? bob : alice;
            assertThat(row.getEmailAddress()).isEqualTo(expectedSubscriber.getEmail());
        }
    }

    @Test
    void should_send_a_malformed_record_to_the_dead_letter_topic() throws Exception {
        producer
                .send(new ProducerRecord<>(topicNewsTopicName, UUID.randomUUID().toString(), "not valid json"))
                .get(10, TimeUnit.SECONDS);

        ConsumerRecord<String, String> deadLetterRecord =
                KafkaTestUtils.getSingleRecord(deadLetterConsumer, topicNewsTopicName + "-dlt", Duration.ofSeconds(30));

        assertThat(deadLetterRecord.value()).isEqualTo("not valid json");
    }

    private Consumer<String, String> newRawConsumer(String groupId, String topic) {
        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps(kafkaBootstrapServers(), groupId, false),
                new StringDeserializer(),
                new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(topic));
        return consumer;
    }

    private String topicNewsEventJson(UUID newsId, UUID interestTopicId, LocalDate newsDate, Instant generatedAt) {
        return """
                {
                  "newsId": "%s",
                  "interestTopicId": "%s",
                  "topicName": "rust",
                  "categoryName": "programming",
                  "newsDate": "%s",
                  "data": "today's rust news",
                  "generatedAt": "%s"
                }
                """.formatted(newsId, interestTopicId, newsDate, generatedAt);
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
}
