package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InterestTopicRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String NAME = "rust";

    private static final String PROMPT = "Summarise the latest news about Rust.";

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    /**
     * The shared Testcontainers Postgres carries rows committed by earlier, non-rolled-back
     * SpringBootTest contexts (DatabaseLoader's seed catalog, other integration tests' HTTP-created
     * rows) - this DataJpaTest's own per-test rollback only undoes what this test itself writes, not
     * what was already there when it started.
     */
    @BeforeEach
    void clearTopicsAndCategories() {
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void should_lowercase_the_name_before_persisting() {
        Category category = categoryRepository.save(newCategory("programming"));

        InterestTopic saved = interestTopicRepository.save(newTopic("Rust", category));

        assertThat(saved.getName()).isEqualTo(NAME);
    }

    @Test
    void should_lowercase_the_name_on_update() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic saved = interestTopicRepository.saveAndFlush(newTopic(NAME, category));

        saved.setName("Kotlin");
        InterestTopic updated = interestTopicRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("kotlin");
    }

    @Test
    void should_reject_a_duplicate_name() {
        Category category = categoryRepository.save(newCategory("programming"));
        interestTopicRepository.saveAndFlush(newTopic(NAME, category));

        assertThatThrownBy(() -> interestTopicRepository.saveAndFlush(newTopic(NAME, category)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void should_report_a_category_referenced_by_a_topic_as_existing() {
        Category category = categoryRepository.save(newCategory("programming"));
        interestTopicRepository.save(newTopic(NAME, category));

        assertThat(interestTopicRepository.existsByCategory_Id(category.getId())).isTrue();
    }

    @Test
    void should_not_report_an_unreferenced_category_as_existing() {
        Category category = categoryRepository.save(newCategory("programming"));

        assertThat(interestTopicRepository.existsByCategory_Id(category.getId())).isFalse();
    }

    @Test
    void should_return_only_the_ids_that_exist() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic saved = interestTopicRepository.save(newTopic(NAME, category));

        List<UUID> existing = interestTopicRepository.findExistingIds(List.of(saved.getId(), UUID.randomUUID()));

        assertThat(existing).containsExactly(saved.getId());
    }

    @Test
    void should_return_topics_named_after_the_cursor_in_name_order_up_to_the_limit() {
        Category category = categoryRepository.save(newCategory("programming"));
        List.of("rust", "go", "kotlin", "java").forEach(name -> interestTopicRepository.save(newTopic(name, category)));

        List<InterestTopic> page = interestTopicRepository.findPageAfter("go", Limit.of(2));

        assertThat(page)
                .extracting(InterestTopic::getName)
                .containsExactly("java", "kotlin");
    }

    @Test
    void should_return_only_the_given_ids_named_after_the_cursor() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic go = interestTopicRepository.save(newTopic("go", category));
        InterestTopic kotlin = interestTopicRepository.save(newTopic("kotlin", category));
        interestTopicRepository.save(newTopic("java", category));

        List<InterestTopic> page = interestTopicRepository
                .findPageAfterIdIn("", List.of(go.getId(), kotlin.getId()), Limit.of(10));

        assertThat(page)
                .extracting(InterestTopic::getName)
                .containsExactly("go", "kotlin");
    }

    @Test
    void should_leave_out_the_given_ids_named_after_the_cursor() {
        Category category = categoryRepository.save(newCategory("programming"));
        InterestTopic go = interestTopicRepository.save(newTopic("go", category));
        interestTopicRepository.save(newTopic("java", category));
        interestTopicRepository.save(newTopic("kotlin", category));

        List<InterestTopic> page = interestTopicRepository
                .findPageAfterIdNotIn("java", List.of(go.getId()), Limit.of(10));

        assertThat(page)
                .extracting(InterestTopic::getName)
                .containsExactly("kotlin");
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private InterestTopic newTopic(String name, Category category) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setPrompt(PROMPT);
        topic.setCategory(category);
        return topic;
    }
}
