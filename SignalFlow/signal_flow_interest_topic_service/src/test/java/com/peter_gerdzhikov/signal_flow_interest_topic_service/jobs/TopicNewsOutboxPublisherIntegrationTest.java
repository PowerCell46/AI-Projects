package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, topicName, Duration.ofSeconds(15));

        assertThat(record.key()).isEqualTo(topic.getId().toString());
        JsonNode payload = JSON_MAPPER.readTree(record.value());
        assertThat(payload.get("newsId").asString()).isEqualTo(news.getId().toString());
        assertThat(payload.get("interestTopicId").asString()).isEqualTo(topic.getId().toString());
        assertThat(payload.get("topicName").asString()).isEqualTo("rust");
        assertThat(payload.get("categoryName").asString()).isEqualTo("programming");
        assertThat(payload.get("newsDate").asString()).isEqualTo(news.getNewsDate().toString());
        assertThat(payload.get("data").asString()).isEqualTo(news.getData());

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            TopicNews reloaded = topicNewsRepository.findById(news.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(NewsStatus.SENT);
            assertThat(reloaded.getSentAt()).isNotNull();
        });
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private InterestTopic newTopic(String name, Category category) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setPrompt("What's new with " + name + "?");
        topic.setCategory(category);
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
