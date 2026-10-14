package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractOpenRouterIntegrationTest;

@SpringBootTest
class TopicNewsGenerationJobIntegrationTest extends AbstractOpenRouterIntegrationTest {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String STUBBED_NEWS = "<p>Stubbed news.</p>";

    private static final LocalDate NEWS_DATE = LocalDate.of(2026, 10, 13);

    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 17);

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Autowired
    private TopicNewsRepository topicNewsRepository;

    @Autowired
    private TopicNewsGenerationJob topicNewsGenerationJob;

    @BeforeEach
    void clearTopicsAndCategoriesAndStubOpenRouter() {
        topicNewsRepository.deleteAll();
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();

        OPENROUTER_STUB.resetMappings();
        OPENROUTER_STUB.resetRequests();
        OPENROUTER_STUB.register(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"choices\": [{\"message\": {\"content\": \"%s\"}}]}".formatted(STUBBED_NEWS))));
    }

    @Test
    void should_create_exactly_one_pending_news_row_per_topic_and_skip_them_on_a_second_run() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic rust = interestTopicRepository.save(newTopic("rust", category));
        InterestTopic kotlin = interestTopicRepository.save(newTopic("kotlin", category));

        topicNewsGenerationJob.generateNewsFor(NEWS_DATE);

        List<TopicNews> allNews = topicNewsRepository.findAll();
        assertThat(allNews).hasSize(2);
        assertThat(allNews)
                .extracting(TopicNews::getInterestTopic)
                .extracting(InterestTopic::getId)
                .containsExactlyInAnyOrder(rust.getId(), kotlin.getId());
        assertThat(allNews).allSatisfy(news -> {
            assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(news.getNewsDate()).isEqualTo(NEWS_DATE);
            assertThat(news.getSchedule()).isEqualTo(NewsSchedule.DAILY);
            assertThat(news.getPeriodStart()).isEqualTo(NEWS_DATE);
            assertThat(news.getData()).isEqualTo(STUBBED_NEWS);
        });

        topicNewsGenerationJob.generateNewsFor(NEWS_DATE);

        assertThat(topicNewsRepository.findAll()).hasSize(2);
    }

    @Test
    void should_skip_the_topic_whose_generation_call_fails_and_still_save_the_rest() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic failingTopic = interestTopicRepository.save(newTopic("rust", category));
        InterestTopic healthyTopic = interestTopicRepository.save(newTopic("kotlin", category));
        OPENROUTER_STUB.register(post(urlEqualTo(CHAT_COMPLETIONS_PATH))
                .withRequestBody(containing(failingTopic.getName()))
                .atPriority(1)
                .willReturn(aResponse().withStatus(500)));

        topicNewsGenerationJob.generateNewsFor(NEWS_DATE);

        List<TopicNews> allNews = topicNewsRepository.findAll();
        assertThat(allNews).hasSize(1);
        assertThat(allNews.get(0).getInterestTopic().getId()).isEqualTo(healthyTopic.getId());
    }

    @Test
    void should_send_only_the_recent_headlines_of_the_topic_being_generated() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic topicA = interestTopicRepository.save(newTopic("alpha-topic", category));
        InterestTopic topicB = interestTopicRepository.save(newTopic("beta-topic", category));
        topicNewsRepository.save(newNews(topicA, NEWS_DATE.minusDays(1), headlineItem("A yesterday story")));
        topicNewsRepository.save(newNews(topicA, NEWS_DATE.minusDays(8), headlineItem("A old story")));
        topicNewsRepository.save(newNews(topicB, NEWS_DATE.minusDays(1), headlineItem("B yesterday story")));
        topicNewsRepository.save(newNews(topicB, NEWS_DATE, headlineItem("B today story")));

        topicNewsGenerationJob.generateNewsFor(NEWS_DATE);

        List<LoggedRequest> requests = OPENROUTER_STUB.find(postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).getBodyAsString())
                .contains("alpha-topic")
                .contains("- A yesterday story")
                .doesNotContain("A old story")
                .doesNotContain("B yesterday story");
    }

    @Test
    void should_create_rows_only_for_the_topics_whose_preset_runs_on_a_saturday() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic daily = interestTopicRepository.save(newTopic("daily-topic", category, NewsSchedule.DAILY));
        interestTopicRepository.save(newTopic("weekdays-topic", category, NewsSchedule.WEEKDAYS));
        InterestTopic weekends = interestTopicRepository.save(newTopic("weekends-topic", category, NewsSchedule.WEEKENDS));
        interestTopicRepository.save(newTopic("weekly-topic", category, NewsSchedule.WEEKLY));

        topicNewsGenerationJob.generateNewsFor(SATURDAY);

        List<TopicNews> allNews = topicNewsRepository.findAll();
        assertThat(allNews)
                .extracting(news -> news.getInterestTopic().getId(), TopicNews::getSchedule)
                .containsExactlyInAnyOrder(
                        tuple(daily.getId(), NewsSchedule.DAILY),
                        tuple(weekends.getId(), NewsSchedule.WEEKENDS));
        assertThat(allNews)
                .filteredOn(news -> news.getSchedule() == NewsSchedule.WEEKENDS)
                .extracting(TopicNews::getPeriodStart)
                .containsExactly(LocalDate.of(2026, 10, 12));
        assertThat(OPENROUTER_STUB.find(postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)))).hasSize(2);
    }

    private String headlineItem(String headline) {
        return "<ul><li><strong>%s</strong> Detail.</li></ul>".formatted(headline);
    }

    private TopicNews newNews(InterestTopic topic, LocalDate newsDate, String data) {
        return TopicNews.builder()
                .interestTopic(topic)
                .newsDate(newsDate)
                .schedule(NewsSchedule.DAILY)
                .periodStart(newsDate)
                .data(data)
                .nextAttemptAt(Instant.now())
                .build();
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private InterestTopic newTopic(String name, Category category) {
        return newTopic(name, category, NewsSchedule.DAILY);
    }

    private InterestTopic newTopic(String name, Category category, NewsSchedule schedule) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setSchedule(schedule);
        topic.setPrompt("What's new with " + name + "?");
        topic.setCategories(Set.of(category));
        return topic;
    }
}
