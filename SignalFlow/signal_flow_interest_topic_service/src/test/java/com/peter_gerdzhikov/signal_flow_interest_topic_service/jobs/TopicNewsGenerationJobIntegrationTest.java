package com.peter_gerdzhikov.signal_flow_interest_topic_service.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@SpringBootTest
class TopicNewsGenerationJobIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Autowired
    private TopicNewsRepository topicNewsRepository;

    @Autowired
    private TopicNewsGenerationJob topicNewsGenerationJob;

    @BeforeEach
    void clearTopicsAndCategories() {
        topicNewsRepository.deleteAll();
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();
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
        });

        topicNewsGenerationJob.generateDailyNews();

        assertThat(topicNewsRepository.findAll()).hasSize(2);
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
