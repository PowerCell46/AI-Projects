package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories.CategoryInUseException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories.CategoryLimitExceededException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories.CategoryNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.categories.DuplicateCategoryNameException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private static final long MAX_CATEGORY_COUNT = 100;

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private static final String CATEGORY_NAME = "programming";

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InterestTopicRepository interestTopicRepository;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(MAX_CATEGORY_COUNT, categoryRepository, interestTopicRepository);
    }

    @Nested
    class Create {

        @Test
        void should_save_and_return_the_category() {
            when(categoryRepository.count()).thenReturn(0L);
            when(categoryRepository.saveAndFlush(any(Category.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Category saved = categoryService.create(CATEGORY_NAME);

            assertThat(saved.getName()).isEqualTo(CATEGORY_NAME);
        }

        @Test
        void should_throw_when_the_name_is_already_in_use() {
            when(categoryRepository.count()).thenReturn(0L);
            when(categoryRepository.saveAndFlush(any(Category.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> categoryService.create(CATEGORY_NAME))
                    .isInstanceOf(DuplicateCategoryNameException.class);
        }

        @Test
        void should_throw_when_the_category_count_is_at_the_limit() {
            when(categoryRepository.count()).thenReturn(MAX_CATEGORY_COUNT);

            assertThatThrownBy(() -> categoryService.create(CATEGORY_NAME))
                    .isInstanceOf(CategoryLimitExceededException.class);

            verify(categoryRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    class FindPage {

        @Test
        void should_return_the_page_the_repository_returns() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<Category> page = new PageImpl<>(List.of(new Category()));
            when(categoryRepository.findAll(pageable)).thenReturn(page);

            assertThat(categoryService.findPage(pageable)).isSameAs(page);
        }
    }

    @Nested
    class Rename {

        @Test
        void should_rename_and_return_the_category() {
            Category category = new Category();
            category.setName(CATEGORY_NAME);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(categoryRepository.saveAndFlush(category)).thenReturn(category);

            Category renamed = categoryService.rename(CATEGORY_ID, "science");

            assertThat(renamed.getName()).isEqualTo("science");
        }

        @Test
        void should_throw_when_the_category_does_not_exist() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.rename(CATEGORY_ID, "science"))
                    .isInstanceOf(CategoryNotFoundException.class);

            verify(categoryRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_throw_when_the_new_name_is_already_in_use() {
            Category category = new Category();
            category.setName(CATEGORY_NAME);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(categoryRepository.saveAndFlush(category))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> categoryService.rename(CATEGORY_ID, "science"))
                    .isInstanceOf(DuplicateCategoryNameException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void should_delete_the_category() {
            when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
            when(interestTopicRepository.existsByCategory_Id(CATEGORY_ID)).thenReturn(false);

            categoryService.delete(CATEGORY_ID);

            verify(categoryRepository).deleteById(CATEGORY_ID);
        }

        @Test
        void should_throw_when_the_category_does_not_exist() {
            when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(false);

            assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID))
                    .isInstanceOf(CategoryNotFoundException.class);

            verify(categoryRepository, never()).deleteById(any());
        }

        @Test
        void should_throw_when_referenced_by_an_interest_topic() {
            when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
            when(interestTopicRepository.existsByCategory_Id(CATEGORY_ID)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.delete(CATEGORY_ID))
                    .isInstanceOf(CategoryInUseException.class);

            verify(categoryRepository, never()).deleteById(any());
        }
    }
}
