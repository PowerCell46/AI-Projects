package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CategoryRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String NAME = "programming";

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
        Category saved = categoryRepository.save(newCategory("Programming"));

        assertThat(saved.getName()).isEqualTo(NAME);
    }

    @Test
    void should_lowercase_the_name_on_update() {
        Category saved = categoryRepository.saveAndFlush(newCategory(NAME));

        saved.setName("Science");
        Category updated = categoryRepository.saveAndFlush(saved);

        assertThat(updated.getName()).isEqualTo("science");
    }

    @Test
    void should_reject_a_duplicate_name() {
        categoryRepository.saveAndFlush(newCategory(NAME));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(newCategory(NAME)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }
}
