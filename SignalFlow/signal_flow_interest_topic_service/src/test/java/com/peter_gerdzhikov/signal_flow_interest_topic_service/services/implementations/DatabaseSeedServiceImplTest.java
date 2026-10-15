package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsSchedule;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.CategoryService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;

@ExtendWith(MockitoExtension.class)
class DatabaseSeedServiceImplTest {

    private static final int EXPECTED_CATEGORY_COUNT = 10;

    private static final int EXPECTED_TOPIC_COUNT = 10;

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InterestTopicService interestTopicService;

    private DatabaseSeedServiceImpl databaseSeedService;

    @BeforeEach
    void setUp() {
        databaseSeedService = new DatabaseSeedServiceImpl(categoryService, categoryRepository, interestTopicService);
    }

    @Nested
    class SeedInitialCatalog {

        @Test
        void should_skip_seeding_when_categories_already_exist() {
            when(categoryRepository.count()).thenReturn(1L);

            databaseSeedService.seedInitialCatalog();

            verify(categoryService, never()).create(anyString());
            verify(interestTopicService, never()).create(any(), any(), any(), any(), any());
        }

        @Test
        void should_seed_every_category_and_interest_topic_when_none_exist() {
            when(categoryRepository.count()).thenReturn(0L);
            stubCategoryCreation();

            databaseSeedService.seedInitialCatalog();

            verify(categoryService, times(EXPECTED_CATEGORY_COUNT)).create(anyString());
            verify(interestTopicService, times(EXPECTED_TOPIC_COUNT)).create(any(), any(), any(), any(), any());
        }

        @Test
        void should_seed_each_topic_with_its_schedule_preset_when_none_exist() {
            when(categoryRepository.count()).thenReturn(0L);
            stubCategoryCreation();

            databaseSeedService.seedInitialCatalog();

            verify(interestTopicService).create(eq("Stock Markets"), any(), any(), any(), eq(NewsSchedule.WEEKDAYS));
            verify(interestTopicService).create(eq("Nutrition & Fitness"), any(), any(), any(), eq(NewsSchedule.WEEKLY));
            verify(interestTopicService).create(eq("Cryptocurrency"), any(), any(), any(), eq(NewsSchedule.DAILY));
            verify(interestTopicService).create(eq("Film & Television"), any(), any(), any(), eq(NewsSchedule.WEEKENDS));
        }

        @Test
        void should_seed_topic_with_its_own_category_first_and_extra_categories_after_when_none_exist() {
            when(categoryRepository.count()).thenReturn(0L);
            Map<String, UUID> categoryIdsByName = stubCategoryCreation();

            databaseSeedService.seedInitialCatalog();

            verify(interestTopicService).create(
                    eq("Video Games"),
                    any(),
                    any(),
                    eq(List.of(categoryIdsByName.get("Entertainment"), categoryIdsByName.get("Technology"))),
                    any());
            verify(interestTopicService).create(
                    eq("Cryptocurrency"),
                    any(),
                    any(),
                    eq(List.of(categoryIdsByName.get("Business & Finance"), categoryIdsByName.get("Technology"))),
                    any());
        }

        @Test
        void should_seed_topic_with_three_categories_when_it_has_two_extra_categories() {
            when(categoryRepository.count()).thenReturn(0L);
            Map<String, UUID> categoryIdsByName = stubCategoryCreation();

            databaseSeedService.seedInitialCatalog();

            verify(interestTopicService).create(
                    eq("Artificial Intelligence"),
                    any(),
                    any(),
                    eq(List.of(
                            categoryIdsByName.get("Technology"),
                            categoryIdsByName.get("Business & Finance"),
                            categoryIdsByName.get("Science & Research"))),
                    any());
        }

        @Test
        void should_seed_topic_with_a_category_that_has_no_topics_of_its_own_when_it_is_an_extra_category() {
            when(categoryRepository.count()).thenReturn(0L);
            Map<String, UUID> categoryIdsByName = stubCategoryCreation();

            databaseSeedService.seedInitialCatalog();

            verify(interestTopicService).create(
                    eq("Cybersecurity"),
                    any(),
                    any(),
                    eq(List.of(categoryIdsByName.get("Technology"), categoryIdsByName.get("Security & Privacy"))),
                    any());
        }

        private Map<String, UUID> stubCategoryCreation() {
            Map<String, UUID> categoryIdsByName = new HashMap<>();
            when(categoryService.create(anyString())).thenAnswer(invocation -> {
                String name = invocation.getArgument(0);
                Category category = new Category();
                category.setId(categoryIdsByName.computeIfAbsent(name, key -> UUID.randomUUID()));
                category.setName(name);
                return category;
            });

            return categoryIdsByName;
        }
    }
}
