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
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.CategoryNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.DuplicateInterestTopicNameException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.InterestTopicNotFoundException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;

@ExtendWith(MockitoExtension.class)
class InterestTopicServiceImplTest {

    private static final UUID CATEGORY_ID = UUID.randomUUID();

    private static final UUID TOPIC_ID = UUID.randomUUID();

    private static final String TOPIC_NAME = "rust";

    private static final String TOPIC_PROMPT = "What's new with Rust?";

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private InterestTopicRepository interestTopicRepository;

    private InterestTopicServiceImpl interestTopicService;

    @BeforeEach
    void setUp() {
        interestTopicService = new InterestTopicServiceImpl(categoryRepository, interestTopicRepository);
    }

    @Nested
    class Create {

        @Test
        void should_save_and_return_the_interest_topic() {
            Category category = categoryWithId(CATEGORY_ID);
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
            when(interestTopicRepository.saveAndFlush(any(InterestTopic.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            InterestTopic saved = interestTopicService.create(TOPIC_NAME, "A systems language", TOPIC_PROMPT, CATEGORY_ID);

            assertThat(saved.getName()).isEqualTo(TOPIC_NAME);
            assertThat(saved.getPrompt()).isEqualTo(TOPIC_PROMPT);
            assertThat(saved.getCategory()).isEqualTo(category);
        }

        @Test
        void should_throw_when_the_category_does_not_exist() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interestTopicService.create(TOPIC_NAME, null, TOPIC_PROMPT, CATEGORY_ID))
                    .isInstanceOf(CategoryNotFoundException.class);

            verify(interestTopicRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_throw_when_the_name_is_already_in_use() {
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(categoryWithId(CATEGORY_ID)));
            when(interestTopicRepository.saveAndFlush(any(InterestTopic.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> interestTopicService.create(TOPIC_NAME, null, TOPIC_PROMPT, CATEGORY_ID))
                    .isInstanceOf(DuplicateInterestTopicNameException.class);
        }
    }

    @Nested
    class FindPage {

        @Test
        void should_return_all_topics_when_no_category_filter_is_given() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<InterestTopic> page = new PageImpl<>(List.of(new InterestTopic()));
            when(interestTopicRepository.findAll(pageable)).thenReturn(page);

            assertThat(interestTopicService.findPage(null, pageable)).isSameAs(page);

            verify(interestTopicRepository, never()).findByCategory_Id(any(), any());
        }

        @Test
        void should_filter_by_category_id_when_provided() {
            Pageable pageable = PageRequest.of(0, 20);
            Page<InterestTopic> page = new PageImpl<>(List.of(new InterestTopic()));
            when(interestTopicRepository.findByCategory_Id(CATEGORY_ID, pageable)).thenReturn(page);

            assertThat(interestTopicService.findPage(CATEGORY_ID, pageable)).isSameAs(page);

            verify(interestTopicRepository, never()).findAll(pageable);
        }
    }

    @Nested
    class Update {

        @Test
        void should_update_only_the_provided_fields() {
            InterestTopic interestTopic = new InterestTopic();
            interestTopic.setName(TOPIC_NAME);
            interestTopic.setPrompt(TOPIC_PROMPT);
            when(interestTopicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(interestTopic));
            when(interestTopicRepository.saveAndFlush(interestTopic)).thenReturn(interestTopic);

            InterestTopic updated = interestTopicService.update(TOPIC_ID, "kotlin", null, null, null);

            assertThat(updated.getName()).isEqualTo("kotlin");
            assertThat(updated.getPrompt()).isEqualTo(TOPIC_PROMPT);
            verify(categoryRepository, never()).findById(any());
        }

        @Test
        void should_throw_when_the_topic_does_not_exist() {
            when(interestTopicRepository.findById(TOPIC_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interestTopicService.update(TOPIC_ID, "kotlin", null, null, null))
                    .isInstanceOf(InterestTopicNotFoundException.class);

            verify(interestTopicRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_throw_when_the_category_does_not_exist() {
            InterestTopic interestTopic = new InterestTopic();
            interestTopic.setName(TOPIC_NAME);
            when(interestTopicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(interestTopic));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> interestTopicService.update(TOPIC_ID, null, null, null, CATEGORY_ID))
                    .isInstanceOf(CategoryNotFoundException.class);

            verify(interestTopicRepository, never()).saveAndFlush(any());
        }

        @Test
        void should_throw_when_the_new_name_is_already_in_use() {
            InterestTopic interestTopic = new InterestTopic();
            interestTopic.setName(TOPIC_NAME);
            when(interestTopicRepository.findById(TOPIC_ID)).thenReturn(Optional.of(interestTopic));
            when(interestTopicRepository.saveAndFlush(interestTopic))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> interestTopicService.update(TOPIC_ID, "kotlin", null, null, null))
                    .isInstanceOf(DuplicateInterestTopicNameException.class);
        }
    }

    @Nested
    class Delete {

        @Test
        void should_delete_the_topic() {
            when(interestTopicRepository.existsById(TOPIC_ID)).thenReturn(true);

            interestTopicService.delete(TOPIC_ID);

            verify(interestTopicRepository).deleteById(TOPIC_ID);
        }

        @Test
        void should_throw_when_the_topic_does_not_exist() {
            when(interestTopicRepository.existsById(TOPIC_ID)).thenReturn(false);

            assertThatThrownBy(() -> interestTopicService.delete(TOPIC_ID))
                    .isInstanceOf(InterestTopicNotFoundException.class);

            verify(interestTopicRepository, never()).deleteById(any());
        }
    }

    private Category categoryWithId(UUID categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("programming");
        return category;
    }
}
