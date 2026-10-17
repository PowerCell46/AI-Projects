package com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.projections.TopicCategoryNameProjection;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InterestTopicRepositoryIntegrationTest extends AbstractIntegrationTest {

    private static final String NAME = "rust";

    private static final String PROMPT = "Summarise the latest news about Rust.";

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

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
        entityManager.flush();
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

        assertThat(interestTopicRepository.existsByCategories_Id(category.getId())).isTrue();
    }

    @Test
    void should_not_report_an_unreferenced_category_as_existing() {
        Category category = categoryRepository.save(newCategory("programming"));

        assertThat(interestTopicRepository.existsByCategories_Id(category.getId())).isFalse();
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

    @Test
    void should_return_each_topic_once_with_all_its_categories_when_paging_all() {
        persistThreeTopicsWithThreeCategories();

        List<InterestTopic> page = interestTopicRepository.findPageAfter("", Limit.of(2));

        assertEachTopicIsDistinctWithThreeCategories(page, List.of("topic-0", "topic-1"));
    }

    @Test
    void should_return_each_topic_once_with_all_its_categories_when_paging_by_included_ids() {
        List<UUID> ids = persistThreeTopicsWithThreeCategories();

        List<InterestTopic> page = interestTopicRepository.findPageAfterIdIn("", ids, Limit.of(2));

        assertEachTopicIsDistinctWithThreeCategories(page, List.of("topic-0", "topic-1"));
    }

    @Test
    void should_return_each_topic_once_with_all_its_categories_when_paging_by_excluded_ids() {
        List<UUID> ids = persistThreeTopicsWithThreeCategories();

        List<InterestTopic> page = interestTopicRepository.findPageAfterIdNotIn("", List.of(ids.getFirst()), Limit.of(2));

        assertEachTopicIsDistinctWithThreeCategories(page, List.of("topic-1", "topic-2"));
    }

    @Test
    void should_return_only_topics_with_the_given_schedules_when_paging_by_schedule() {
        Category category = categoryRepository.save(newCategory("art"));
        persistTopicWithSchedule("daily", category, NewsSchedule.DAILY);
        persistTopicWithSchedule("weekdays", category, NewsSchedule.WEEKDAYS);
        persistTopicWithSchedule("weekly", category, NewsSchedule.WEEKLY);
        persistTopicWithSchedule("weekends", category, NewsSchedule.WEEKENDS);
        interestTopicRepository.flush();
        entityManager.clear();

        Page<InterestTopic> page = interestTopicRepository.findByScheduleIn(
                Set.of(NewsSchedule.DAILY, NewsSchedule.WEEKDAYS), PageRequest.of(0, 10, Sort.by("name")));

        assertThat(page.getContent()).extracting(InterestTopic::getName).containsExactly("daily", "weekdays");
    }

    @Test
    void should_store_the_schedule_as_its_code_in_the_column() {
        Category category = categoryRepository.save(newCategory("art"));
        persistTopicWithSchedule(NAME, category, NewsSchedule.WEEKDAYS);
        entityManager.flush();

        Object storedValue = entityManager
                .createNativeQuery("select schedule from interest_topics where name = :name")
                .setParameter("name", NAME)
                .getSingleResult();

        assertThat(storedValue).isEqualTo(NewsSchedule.WEEKDAYS.getCode());
    }

    @Test
    void should_return_each_matching_topic_once_and_respect_the_page_size_when_topics_have_several_categories() {
        Set<Category> categories = new HashSet<>(categoryRepository.saveAll(List.of(
                newCategory("art"), newCategory("science"), newCategory("sport"))));
        IntStream.range(0, 3).forEach(i -> persistTopicWithSchedule("topic-" + i, categories, NewsSchedule.DAILY));
        persistTopicWithSchedule("other", categories, NewsSchedule.WEEKLY);
        interestTopicRepository.flush();
        entityManager.clear();

        Page<InterestTopic> firstPage = interestTopicRepository.findByScheduleIn(
                Set.of(NewsSchedule.DAILY), PageRequest.of(0, 2, Sort.by("name")));

        assertThat(firstPage.getContent()).extracting(InterestTopic::getName).containsExactly("topic-0", "topic-1");
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
    }

    @Test
    void should_report_a_category_that_is_one_of_several_as_existing() {
        List<Category> categories = categoryRepository.saveAll(List.of(
                newCategory("art"), newCategory("science"), newCategory("sport")));
        interestTopicRepository.save(newTopic(NAME, new HashSet<>(categories)));

        assertThat(interestTopicRepository.existsByCategories_Id(categories.get(1).getId())).isTrue();
    }

    @Test
    void should_return_the_category_names_of_each_given_topic() {
        List<Category> categories = categoryRepository.saveAll(List.of(newCategory("art"), newCategory("science")));
        InterestTopic both = interestTopicRepository.save(newTopic("both", new HashSet<>(categories)));
        InterestTopic onlyArt = interestTopicRepository.save(newTopic("only-art", Set.of(categories.getFirst())));

        List<TopicCategoryNameProjection> rows = interestTopicRepository
                .findCategoryNamesByTopicIds(List.of(both.getId(), onlyArt.getId()));

        assertThat(rows)
                .extracting(TopicCategoryNameProjection::getTopicId, TopicCategoryNameProjection::getCategoryName)
                .containsExactlyInAnyOrder(
                        tuple(both.getId(), "art"),
                        tuple(both.getId(), "science"),
                        tuple(onlyArt.getId(), "art"));
    }

    private List<UUID> persistThreeTopicsWithThreeCategories() {
        Set<Category> categories = new HashSet<>(categoryRepository.saveAll(List.of(
                newCategory("art"), newCategory("science"), newCategory("sport"))));
        List<UUID> ids = IntStream.range(0, 3)
                .mapToObj(i -> interestTopicRepository.save(newTopic("topic-" + i, categories)).getId())
                .toList();
        interestTopicRepository.flush();
        entityManager.clear();
        return ids;
    }

    private void assertEachTopicIsDistinctWithThreeCategories(List<InterestTopic> page, List<String> expectedNames) {
        assertThat(page).extracting(InterestTopic::getName).containsExactlyElementsOf(expectedNames);
        page.forEach(topic -> assertThat(topic.getCategories())
                .extracting(Category::getName)
                .containsExactlyInAnyOrder("art", "science", "sport"));
    }

    private Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private void persistTopicWithSchedule(String name, Category category, NewsSchedule schedule) {
        persistTopicWithSchedule(name, Set.of(category), schedule);
    }

    private void persistTopicWithSchedule(String name, Set<Category> categories, NewsSchedule schedule) {
        InterestTopic topic = newTopic(name, categories);
        topic.setSchedule(schedule);
        interestTopicRepository.save(topic);
    }

    private InterestTopic newTopic(String name, Category category) {
        return newTopic(name, Set.of(category));
    }

    private InterestTopic newTopic(String name, Set<Category> categories) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setPrompt(PROMPT);
        topic.setCategories(categories);
        return topic;
    }
}
