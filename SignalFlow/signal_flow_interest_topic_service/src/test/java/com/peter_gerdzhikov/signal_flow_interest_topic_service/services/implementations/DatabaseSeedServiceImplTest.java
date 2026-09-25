package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.CategoryService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.InterestTopicService;

@ExtendWith(MockitoExtension.class)
class DatabaseSeedServiceImplTest {

    private static final int EXPECTED_CATEGORY_COUNT = 10;

    private static final int EXPECTED_TOPIC_COUNT = 30;

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
            verify(interestTopicService, never()).create(any(), any(), any(), any());
        }

        @Test
        void should_seed_every_category_and_interest_topic_when_none_exist() {
            when(categoryRepository.count()).thenReturn(0L);
            when(categoryService.create(anyString())).thenAnswer(invocation -> {
                Category category = new Category();
                category.setId(UUID.randomUUID());
                category.setName(invocation.getArgument(0));
                return category;
            });

            databaseSeedService.seedInitialCatalog();

            verify(categoryService, times(EXPECTED_CATEGORY_COUNT)).create(anyString());
            verify(interestTopicService, times(EXPECTED_TOPIC_COUNT)).create(any(), any(), any(), any());
        }
    }
}
