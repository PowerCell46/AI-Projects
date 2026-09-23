package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
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
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.InterestTopicFeedMode;
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

    @Nested
    class FindExistingIds {

        @Test
        void should_return_the_ids_the_repository_reports_as_existing() {
            UUID missingId = UUID.randomUUID();
            List<UUID> requested = List.of(TOPIC_ID, missingId);
            when(interestTopicRepository.findExistingIds(requested)).thenReturn(List.of(TOPIC_ID));

            List<UUID> existing = interestTopicService.findExistingIds(requested);

            assertThat(existing).containsExactly(TOPIC_ID);
        }

        @Test
        void should_return_an_empty_list_without_querying_when_no_ids_are_given() {
            List<UUID> existing = interestTopicService.findExistingIds(List.of());

            assertThat(existing).isEmpty();
            verifyNoInteractions(interestTopicRepository);
        }
    }

    @Nested
    class FindFeedSlice {

        private static final Limit ONE_PAST_A_PAGE_OF_TWO = Limit.of(3);

        @Test
        void should_start_from_the_beginning_when_no_cursor_is_given() {
            when(interestTopicRepository.findPageAfter("", ONE_PAST_A_PAGE_OF_TWO)).thenReturn(topicsNamed("go"));

            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(List.of(), InterestTopicFeedMode.ALL, null, 2);

            assertThat(slice.getContent()).extracting(InterestTopic::getName).containsExactly("go");
        }

        @Test
        void should_report_a_next_page_and_trim_the_extra_row_when_more_topics_follow() {
            when(interestTopicRepository.findPageAfter("a", ONE_PAST_A_PAGE_OF_TWO))
                    .thenReturn(topicsNamed("go", "java", "kotlin"));

            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(List.of(), InterestTopicFeedMode.ALL, "a", 2);

            assertThat(slice.hasNext()).isTrue();
            assertThat(slice.getContent()).extracting(InterestTopic::getName).containsExactly("go", "java");
        }

        @Test
        void should_report_no_next_page_when_the_last_topics_fit() {
            when(interestTopicRepository.findPageAfter("a", ONE_PAST_A_PAGE_OF_TWO)).thenReturn(topicsNamed("go", "java"));

            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(List.of(), InterestTopicFeedMode.ALL, "a", 2);

            assertThat(slice.hasNext()).isFalse();
            assertThat(slice.getContent()).hasSize(2);
        }

        @Test
        void should_query_only_the_given_ids_when_including() {
            List<UUID> ids = List.of(TOPIC_ID);
            when(interestTopicRepository.findPageAfterIdIn("", ids, ONE_PAST_A_PAGE_OF_TWO)).thenReturn(topicsNamed("go"));

            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(ids, InterestTopicFeedMode.INCLUDE, null, 2);

            assertThat(slice.getContent()).extracting(InterestTopic::getName).containsExactly("go");
        }

        @Test
        void should_return_nothing_without_querying_when_including_no_ids() {
            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(List.of(), InterestTopicFeedMode.INCLUDE, null, 2);

            assertThat(slice.getContent()).isEmpty();
            assertThat(slice.hasNext()).isFalse();
            verifyNoInteractions(interestTopicRepository);
        }

        @Test
        void should_query_around_the_given_ids_when_excluding() {
            List<UUID> ids = List.of(TOPIC_ID);
            when(interestTopicRepository.findPageAfterIdNotIn("", ids, ONE_PAST_A_PAGE_OF_TWO)).thenReturn(topicsNamed("go"));

            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(ids, InterestTopicFeedMode.EXCLUDE, null, 2);

            assertThat(slice.getContent()).extracting(InterestTopic::getName).containsExactly("go");
        }

        @Test
        void should_return_every_topic_when_excluding_no_ids() {
            when(interestTopicRepository.findPageAfter("", ONE_PAST_A_PAGE_OF_TWO)).thenReturn(topicsNamed("go"));

            Slice<InterestTopic> slice = interestTopicService.findFeedSlice(List.of(), InterestTopicFeedMode.EXCLUDE, null, 2);

            assertThat(slice.getContent()).extracting(InterestTopic::getName).containsExactly("go");
            verify(interestTopicRepository, never()).findPageAfterIdNotIn(any(), any(), any());
        }

        private List<InterestTopic> topicsNamed(String... names) {
            return Arrays.stream(names)
                    .map(this::topicNamed)
                    .toList();
        }

        private InterestTopic topicNamed(String name) {
            InterestTopic topic = new InterestTopic();
            topic.setName(name);
            return topic;
        }
    }

    private Category categoryWithId(UUID categoryId) {
        Category category = new Category();
        category.setId(categoryId);
        category.setName("programming");
        return category;
    }
}
