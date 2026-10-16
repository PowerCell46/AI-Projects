package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TopicNewsRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String PROMPT = "Summarise the latest news about Rust.";

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Autowired
    private TopicNewsRepository topicNewsRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void should_reject_a_duplicate_news_row_for_the_same_topic_and_date() {
        InterestTopic topic = newSavedTopic("rust");
        LocalDate newsDate = LocalDate.now();
        topicNewsRepository.saveAndFlush(newNews(topic, newsDate, NewsStatus.PENDING, Instant.now()));

        assertThatThrownBy(
                        () -> topicNewsRepository.saveAndFlush(
                                newNews(topic, newsDate, NewsStatus.PENDING, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_cascade_delete_news_rows_when_their_topic_is_deleted() {
        InterestTopic topic = newSavedTopic("rust");
        TopicNews news = topicNewsRepository.saveAndFlush(
                newNews(topic, LocalDate.now(), NewsStatus.PENDING, Instant.now()));

        interestTopicRepository.delete(topic);
        interestTopicRepository.flush();
        entityManager.clear();

        assertThat(topicNewsRepository.findById(news.getId())).isEmpty();
    }

    @Test
    void should_return_only_due_pending_rows_ordered_by_next_attempt_at_ascending() {
        InterestTopic topic = newSavedTopic("rust");
        Instant now = Instant.now();
        TopicNews earliestDue = topicNewsRepository.save(
                newNews(topic, LocalDate.now().minusDays(3), NewsStatus.PENDING, now.minus(2, ChronoUnit.MINUTES)));
        TopicNews laterDue = topicNewsRepository.save(
                newNews(topic, LocalDate.now().minusDays(2), NewsStatus.PENDING, now.minus(1, ChronoUnit.MINUTES)));
        topicNewsRepository.save(
                newNews(topic, LocalDate.now().minusDays(1), NewsStatus.PENDING, now.plus(1, ChronoUnit.MINUTES)));
        topicNewsRepository.save(newNews(topic, LocalDate.now(), NewsStatus.SENT, now.minus(1, ChronoUnit.MINUTES)));

        List<TopicNews> due = topicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                NewsStatus.PENDING, now, PageRequest.of(0, 50));

        assertThat(due)
                .extracting(TopicNews::getId)
                .containsExactly(earliestDue.getId(), laterDue.getId());
    }

    @Test
    void should_cap_the_returned_rows_at_the_page_size() {
        InterestTopic topic = newSavedTopic("rust");
        Instant now = Instant.now();
        for (int day = 0; day < 3; day++) {
            topicNewsRepository.save(
                    newNews(topic, LocalDate.now().minusDays(day), NewsStatus.PENDING, now.minus(1, ChronoUnit.MINUTES)));
        }

        List<TopicNews> due = topicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                NewsStatus.PENDING, now, PageRequest.of(0, 1));

        assertThat(due).hasSize(1);
    }

    @Test
    void should_return_each_news_row_once_and_respect_the_page_size_when_topics_have_three_categories() {
        Set<Category> categories = new HashSet<>(categoryRepository.saveAll(List.of(
                newCategory("art"), newCategory("science"), newCategory("sport"))));
        Instant now = Instant.now();
        for (int topicIndex = 0; topicIndex < 3; topicIndex++) {
            InterestTopic topic = newSavedTopic("topic-" + topicIndex, categories);
            topicNewsRepository.save(
                    newNews(topic, LocalDate.now(), NewsStatus.PENDING, now.minus(topicIndex + 1, ChronoUnit.MINUTES)));
        }

        List<TopicNews> due = topicNewsRepository.findByStatusAndNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(
                NewsStatus.PENDING, now, PageRequest.of(0, 2));

        assertThat(due)
                .extracting(news -> news.getInterestTopic().getName())
                .containsExactly("topic-2", "topic-1");
    }

    @Test
    void should_return_the_data_of_rows_in_the_window_newest_first_and_exclude_the_end_date() {
        InterestTopic topic = newSavedTopic("rust");
        saveNewsWithData(topic, LocalDate.of(2026, 10, 5), "too old");
        saveNewsWithData(topic, LocalDate.of(2026, 10, 6), "window start");
        saveNewsWithData(topic, LocalDate.of(2026, 10, 12), "window end");
        saveNewsWithData(topic, LocalDate.of(2026, 10, 13), "the end date itself");

        List<String> data = topicNewsRepository.findDataByTopicInDateRange(
                topic.getId(), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 13));

        assertThat(data).containsExactly("window end", "window start");
    }

    @Test
    void should_return_rows_of_any_status_in_the_window() {
        InterestTopic topic = newSavedTopic("rust");
        saveNewsWithStatus(topic, LocalDate.of(2026, 10, 10), NewsStatus.SENT);
        saveNewsWithStatus(topic, LocalDate.of(2026, 10, 11), NewsStatus.FAILED);
        saveNewsWithStatus(topic, LocalDate.of(2026, 10, 12), NewsStatus.PENDING);

        List<String> data = topicNewsRepository.findDataByTopicInDateRange(
                topic.getId(), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 13));

        assertThat(data).hasSize(3);
    }

    @Test
    void should_not_return_another_topics_rows_in_the_window() {
        Set<Category> categories = Set.of(categoryRepository.save(newCategory("programming")));
        InterestTopic topic = newSavedTopic("rust", categories);
        InterestTopic otherTopic = newSavedTopic("kotlin", categories);
        saveNewsWithData(otherTopic, LocalDate.of(2026, 10, 10), "someone else's news");

        List<String> data = topicNewsRepository.findDataByTopicInDateRange(
                topic.getId(), LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 13));

        assertThat(data).isEmpty();
    }

    @Test
    void should_store_the_schedule_as_its_code_in_the_column() {
        InterestTopic topic = newSavedTopic("rust");
        TopicNews news = newNews(topic, LocalDate.now(), NewsStatus.PENDING, Instant.now());
        news.setSchedule(NewsSchedule.WEEKLY);
        topicNewsRepository.saveAndFlush(news);

        Object storedValue = entityManager
                .createNativeQuery("select schedule from topic_news where id = :id")
                .setParameter("id", news.getId())
                .getSingleResult();

        assertThat(storedValue).isEqualTo(NewsSchedule.WEEKLY.getCode());
    }

    private void saveNewsWithData(InterestTopic topic, LocalDate newsDate, String data) {
        TopicNews news = newNews(topic, newsDate, NewsStatus.PENDING, Instant.now());
        news.setData(data);
        topicNewsRepository.save(news);
    }

    private void saveNewsWithStatus(InterestTopic topic, LocalDate newsDate, NewsStatus status) {
        topicNewsRepository.save(newNews(topic, newsDate, status, Instant.now()));
    }

    private InterestTopic newSavedTopic(String name) {
        Category category = categoryRepository.save(newCategory("programming"));
        return newSavedTopic(name, Set.of(category));
    }

    private InterestTopic newSavedTopic(String name, Set<Category> categories) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setPrompt(PROMPT);
        topic.setCategories(categories);
        return interestTopicRepository.save(topic);
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private TopicNews newNews(InterestTopic topic, LocalDate newsDate, NewsStatus status, Instant nextAttemptAt) {
        TopicNews news = new TopicNews();
        news.setInterestTopic(topic);
        news.setNewsDate(newsDate);
        news.setSchedule(NewsSchedule.DAILY);
        news.setPeriodStart(newsDate);
        news.setData("some generated news");
        news.setStatus(status);
        news.setNextAttemptAt(nextAttemptAt);
        return news;
    }
}
