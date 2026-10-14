package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@TestPropertySource(properties = {"app.outbox.poll-interval=PT1S", "app.outbox.base-backoff=PT1S"})
class TopicNewsOutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Autowired
    private TopicNewsRepository topicNewsRepository;

    @Value("${app.kafka.topic-news.name}")
    private String topicName;

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        topicNewsRepository.deleteAll();
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();

        consumer = new DefaultKafkaConsumerFactory<>(
                KafkaTestUtils.consumerProps(kafkaBootstrapServers(), "outbox-publisher-test", false),
                new StringDeserializer(),
                new StringDeserializer())
                .createConsumer();
        consumer.subscribe(List.of(topicName));
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void should_publish_a_pending_news_row_and_mark_it_sent() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic topic = interestTopicRepository.save(newTopic("rust", category));
        TopicNews news = topicNewsRepository.save(newPendingNews(topic));

        ConsumerRecord<String, String> record = awaitRecordFor(topic);

        assertThat(record.key()).isEqualTo(topic.getId().toString());
        JsonNode payload = JSON_MAPPER.readTree(record.value());
        assertThat(payload.get("newsId").asString()).isEqualTo(news.getId().toString());
        assertThat(payload.get("interestTopicId").asString()).isEqualTo(topic.getId().toString());
        assertThat(payload.get("topicName").asString()).isEqualTo("rust");
        assertThat(categoryNames(payload)).containsExactly("programming");
        assertThat(payload.get("newsDate").asString()).isEqualTo(news.getNewsDate().toString());
        assertThat(payload.get("data").asString()).isEqualTo(news.getData());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            TopicNews reloaded = topicNewsRepository.findById(news.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(NewsStatus.SENT);
            assertThat(reloaded.getSentAt()).isNotNull();
        });
    }

    @Test
    void should_publish_all_three_category_names_in_sorted_order() {
        List<Category> categories = categoryRepository.saveAll(List.of(
                newCategory("technology"), newCategory("politics"), newCategory("business")));
        InterestTopic topic = interestTopicRepository.save(newTopic("ai regulation", categories));
        topicNewsRepository.save(newPendingNews(topic));

        ConsumerRecord<String, String> record = awaitRecordFor(topic);

        assertThat(categoryNames(JSON_MAPPER.readTree(record.value())))
                .containsExactly("business", "politics", "technology");
    }

    @Test
    void should_publish_each_topic_with_its_own_category_names_when_a_batch_is_due() {
        List<Category> categories = categoryRepository.saveAll(List.of(
                newCategory("art"), newCategory("science"), newCategory("sport")));
        InterestTopic oneCategory = interestTopicRepository.save(newTopic("single", categories.subList(1, 2)));
        InterestTopic twoCategories = interestTopicRepository.save(newTopic("double", categories.subList(0, 2)));
        InterestTopic threeCategories = interestTopicRepository.save(newTopic("triple", categories));
        topicNewsRepository.saveAll(List.of(
                newPendingNews(oneCategory), newPendingNews(twoCategories), newPendingNews(threeCategories)));

        Map<String, List<String>> namesByTopicName = new HashMap<>();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2)).forEach(record -> {
                JsonNode payload = JSON_MAPPER.readTree(record.value());
                namesByTopicName.put(payload.get("topicName").asString(), categoryNames(payload));
            });
            assertThat(namesByTopicName).hasSize(3);
        });

        assertThat(namesByTopicName.get("single")).containsExactly("science");
        assertThat(namesByTopicName.get("double")).containsExactly("art", "science");
        assertThat(namesByTopicName.get("triple")).containsExactly("art", "science", "sport");
    }

    /**
     * The Kafka topic is shared across tests and each consumer starts from the earliest offset, so
     * records left by earlier tests are skipped by matching on the topic's own key.
     */
    private ConsumerRecord<String, String> awaitRecordFor(InterestTopic topic) {
        List<ConsumerRecord<String, String>> matching = new ArrayList<>();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(2))
                    .forEach(record -> {
                        if (record.key().equals(topic.getId().toString())) {
                            matching.add(record);
                        }
                    });
            assertThat(matching).isNotEmpty();
        });
        return matching.getFirst();
    }

    private List<String> categoryNames(JsonNode payload) {
        return payload
                .get("categoryNames")
                .values()
                .stream()
                .map(JsonNode::asString)
                .toList();
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private InterestTopic newTopic(String name, Category category) {
        return newTopic(name, List.of(category));
    }

    private InterestTopic newTopic(String name, List<Category> categories) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setPrompt("What's new with " + name + "?");
        topic.setCategories(new HashSet<>(categories));
        return topic;
    }

    private TopicNews newPendingNews(InterestTopic topic) {
        TopicNews news = new TopicNews();
        news.setInterestTopic(topic);
        news.setNewsDate(LocalDate.now());
        news.setData("today's rust news");
        news.setNextAttemptAt(Instant.now());
        return news;
    }
}
