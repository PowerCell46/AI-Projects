package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractOpenRouterIntegrationTest;

@SpringBootTest
class TopicNewsGenerationJobIntegrationTest extends AbstractOpenRouterIntegrationTest {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String STUBBED_NEWS = "<p>Stubbed news.</p>";

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

        topicNewsGenerationJob.generateDailyNews();

        List<TopicNews> allNews = topicNewsRepository.findAll();
        assertThat(allNews).hasSize(2);
        assertThat(allNews)
                .extracting(TopicNews::getInterestTopic)
                .extracting(InterestTopic::getId)
                .containsExactlyInAnyOrder(rust.getId(), kotlin.getId());
        assertThat(allNews).allSatisfy(news -> {
            assertThat(news.getStatus()).isEqualTo(NewsStatus.PENDING);
            assertThat(news.getNewsDate()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
            assertThat(news.getData()).isEqualTo(STUBBED_NEWS);
        });

        topicNewsGenerationJob.generateDailyNews();

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

        topicNewsGenerationJob.generateDailyNews();

        List<TopicNews> allNews = topicNewsRepository.findAll();
        assertThat(allNews).hasSize(1);
        assertThat(allNews.get(0).getInterestTopic().getId()).isEqualTo(healthyTopic.getId());
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
}
