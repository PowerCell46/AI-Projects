package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.CreateInterestTopicRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.UpdateInterestTopicRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.categories.CategorySummaryResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.interesttopics.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.projections.TopicCategoryNameProjection;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
class InterestTopicControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String TOPIC_NAME = "rust";

    private static final String TOPIC_PROMPT = "What's new with Rust?";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Autowired
    private TopicNewsRepository topicNewsRepository;

    @Value("${app.interest-topic.max-count}")
    private int maxInterestTopicCount;

    @BeforeEach
    void clearTopicsAndCategories() {
        topicNewsRepository.deleteAll();
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Nested
    class CreateInterestTopic {

        @Test
        void should_create_an_interest_topic_and_return_it() {
            UUID categoryId = persistedCategory("programming").getId();

            InterestTopicResponseDTO body = createInterestTopic(TOPIC_NAME, "A systems language", TOPIC_PROMPT, List.of(categoryId))
                    .expectStatus().isCreated()
                    .expectBody(InterestTopicResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isNotNull();
            assertThat(body.getName()).isEqualTo(TOPIC_NAME);
            assertThat(body.getCategories())
                    .extracting(CategorySummaryResponseDTO::getId, CategorySummaryResponseDTO::getName)
                    .containsExactly(tuple(categoryId, "programming"));
        }

        @Test
        void should_not_expose_the_prompt_in_the_response() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, "A systems language", TOPIC_PROMPT, List.of(categoryId))
                    .expectStatus().isCreated()
                    .expectBody()
                    .jsonPath("$.prompt").doesNotExist();
        }

        @Test
        void should_store_the_name_lowercased() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic("Rust", null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isCreated();

            assertThat(interestTopicRepository.findAll())
                    .extracting(InterestTopic::getName)
                    .containsExactly(TOPIC_NAME);
        }

        @Test
        void should_return_404_for_an_unknown_category_id() {
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(UUID.randomUUID())).expectStatus().isNotFound();
        }

        @Test
        void should_return_409_for_a_duplicate_name() {
            UUID categoryId = persistedCategory("programming").getId();
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isCreated();

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_409_for_a_duplicate_name_differing_only_in_case() {
            UUID categoryId = persistedCategory("programming").getId();
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isCreated();

            createInterestTopic("Rust", null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_for_a_blank_name() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(" ", null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_blank_prompt() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, null, " ", List.of(categoryId)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_missing_category_ids() {
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, null).expectStatus().isBadRequest();
        }

        @Test
        void should_create_with_three_categories_sorted_by_name() {
            Category technology = persistedCategory("technology");
            Category politics = persistedCategory("politics");
            Category business = persistedCategory("business");

            InterestTopicResponseDTO body = createInterestTopic(
                    TOPIC_NAME, null, TOPIC_PROMPT, List.of(technology.getId(), politics.getId(), business.getId()))
                    .expectStatus().isCreated()
                    .expectBody(InterestTopicResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getCategories())
                    .extracting(CategorySummaryResponseDTO::getName)
                    .containsExactly("business", "politics", "technology");
        }

        @Test
        void should_return_400_for_an_empty_category_id_list() {
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of()).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_more_than_three_category_ids() {
            List<UUID> categoryIds = List.of(
                    persistedCategory("one").getId(),
                    persistedCategory("two").getId(),
                    persistedCategory("three").getId(),
                    persistedCategory("four").getId());

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, categoryIds).expectStatus().isBadRequest();

            assertThat(interestTopicRepository.count()).isZero();
        }

        @Test
        void should_return_400_for_a_null_category_id_element() {
            List<UUID> categoryIds = Arrays.asList(persistedCategory("programming").getId(), null);

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, categoryIds).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_duplicate_category_ids() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(categoryId, categoryId))
                    .expectStatus().isBadRequest();

            assertThat(interestTopicRepository.count()).isZero();
        }

        @Test
        void should_return_404_and_create_no_topic_when_one_category_id_is_unknown() {
            UUID knownCategoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(knownCategoryId, UUID.randomUUID()))
                    .expectStatus().isNotFound();

            assertThat(interestTopicRepository.count()).isZero();
        }

        @Test
        void should_return_400_for_a_name_over_100_characters() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic("a".repeat(101), null, TOPIC_PROMPT, List.of(categoryId)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_description_over_1000_characters() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, "a".repeat(1001), TOPIC_PROMPT, List.of(categoryId)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_prompt_over_4000_characters() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, null, "a".repeat(4001), List.of(categoryId)).expectStatus().isBadRequest();
        }

        @Test
        void should_accept_a_4000_character_prompt_in_a_multi_byte_script() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, "新".repeat(1000), "新".repeat(4000), List.of(categoryId)).expectStatus().isCreated();
        }

        @Test
        void should_return_400_for_malformed_json() {
            restTestClient.post()
                    .uri("/api/v1/interest-topics")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ not valid json")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void should_not_leak_exception_or_package_names_in_the_error_body() {
            ErrorResponseDTO body = createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, null)
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
        }

        @Test
        void should_return_409_when_the_topic_count_is_at_the_limit() {
            Category category = persistedCategory("programming");
            interestTopicRepository.saveAll(namedInterestTopics(maxInterestTopicCount, category));

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, List.of(category.getId())).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class ListInterestTopics {

        @Test
        void should_return_the_first_page_sorted_by_name_by_default() {
            Category category = persistedCategory("programming");
            persistedInterestTopic("rust", category);
            persistedInterestTopic("kotlin", category);

            restTestClient.get()
                    .uri("/api/v1/interest-topics")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].name").isEqualTo("kotlin")
                    .jsonPath("$.content[1].name").isEqualTo("rust")
                    .jsonPath("$.page.size").isEqualTo(20);
        }

        @Test
        void should_respect_page_and_size_params() {
            Category category = persistedCategory("programming");
            persistedInterestTopic("rust", category);
            persistedInterestTopic("kotlin", category);

            restTestClient.get()
                    .uri("/api/v1/interest-topics?page=1&size=1")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].name").isEqualTo("rust");
        }

        @Test
        void should_cap_the_page_size_at_100() {
            restTestClient.get()
                    .uri("/api/v1/interest-topics?size=1000")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.page.size").isEqualTo(100);
        }

        @Test
        void should_filter_by_category_id_when_provided() {
            Category programming = persistedCategory("programming");
            Category science = persistedCategory("science");
            persistedInterestTopic("rust", programming);
            persistedInterestTopic("physics", science);

            restTestClient.get()
                    .uri("/api/v1/interest-topics?categoryId=" + programming.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].name").isEqualTo("rust");
        }

        @Test
        void should_return_a_topic_whose_second_category_is_the_filter_and_count_it_once() {
            Category art = persistedCategory("art");
            Category science = persistedCategory("science");
            Category sport = persistedCategory("sport");
            persistedInterestTopic("physics", art, science, sport);
            persistedInterestTopic("painting", art);

            restTestClient.get()
                    .uri("/api/v1/interest-topics?categoryId=" + science.getId())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(1)
                    .jsonPath("$.content[0].name").isEqualTo("physics")
                    .jsonPath("$.content[0].categories.length()").isEqualTo(3)
                    .jsonPath("$.page.totalElements").isEqualTo(1);
        }

        @Test
        void should_not_expose_the_prompt_of_any_listed_topic() {
            persistedInterestTopic(TOPIC_NAME, persistedCategory("programming"));

            restTestClient.get()
                    .uri("/api/v1/interest-topics")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].name").isEqualTo(TOPIC_NAME)
                    .jsonPath("$.content[0].prompt").doesNotExist();
        }

        @Test
        void should_return_an_empty_page_when_no_topics_exist() {
            restTestClient.get()
                    .uri("/api/v1/interest-topics")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(0);
        }
    }

    @Nested
    class UpdateInterestTopic {

        @Test
        void should_update_only_the_provided_fields() {
            Category category = persistedCategory("programming");
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, category);

            InterestTopicResponseDTO body = updateInterestTopic(interestTopic.getId(), "kotlin", null, null, null)
                    .expectStatus().isOk()
                    .expectBody(InterestTopicResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            InterestTopic stored = interestTopicRepository.findById(interestTopic.getId()).orElseThrow();
            assertThat(body.getName()).isEqualTo("kotlin");
            assertThat(stored.getPrompt()).isEqualTo(TOPIC_PROMPT);
        }

        @Test
        void should_replace_the_whole_category_set_when_category_ids_are_provided() {
            Category art = persistedCategory("art");
            Category science = persistedCategory("science");
            Category sport = persistedCategory("sport");
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, art, science);

            InterestTopicResponseDTO body = updateInterestTopic(
                    interestTopic.getId(), null, null, null, List.of(sport.getId()))
                    .expectStatus().isOk()
                    .expectBody(InterestTopicResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getCategories())
                    .extracting(CategorySummaryResponseDTO::getName)
                    .containsExactly("sport");
            assertThat(storedCategoryNames(interestTopic.getId())).containsExactly("sport");
        }

        @Test
        void should_leave_the_categories_unchanged_when_category_ids_are_absent() {
            Category art = persistedCategory("art");
            Category science = persistedCategory("science");
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, art, science);

            updateInterestTopic(interestTopic.getId(), "kotlin", null, null, null).expectStatus().isOk();

            assertThat(storedCategoryNames(interestTopic.getId())).containsExactlyInAnyOrder("art", "science");
        }

        @Test
        void should_return_400_for_an_empty_category_id_list_and_keep_the_categories() {
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, persistedCategory("programming"));

            updateInterestTopic(interestTopic.getId(), null, null, null, List.of()).expectStatus().isBadRequest();

            assertThat(storedCategoryNames(interestTopic.getId())).containsExactly("programming");
        }

        @Test
        void should_return_404_when_the_topic_does_not_exist() {
            updateInterestTopic(UUID.randomUUID(), "kotlin", null, null, null).expectStatus().isNotFound();
        }

        @Test
        void should_return_404_for_an_unknown_category_id() {
            Category category = persistedCategory("programming");
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, category);

            updateInterestTopic(interestTopic.getId(), null, null, null, List.of(UUID.randomUUID())).expectStatus().isNotFound();
        }

        @Test
        void should_return_409_when_renaming_to_a_name_already_in_use() {
            Category category = persistedCategory("programming");
            persistedInterestTopic(TOPIC_NAME, category);
            InterestTopic other = persistedInterestTopic("kotlin", category);

            updateInterestTopic(other.getId(), TOPIC_NAME, null, null, null).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_for_a_malformed_id() {
            restTestClient.patch()
                    .uri("/api/v1/interest-topics/not-a-uuid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(updateRequest("kotlin", null, null, null))
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    class DeleteInterestTopic {

        @Test
        void should_return_204_and_cascade_delete_its_news() {
            Category category = persistedCategory("programming");
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, category);
            persistedTopicNews(interestTopic);

            deleteInterestTopic(interestTopic.getId()).expectStatus().isNoContent();

            assertThat(interestTopicRepository.existsById(interestTopic.getId())).isFalse();
            assertThat(topicNewsRepository.count()).isZero();
        }

        @Test
        void should_return_404_when_the_topic_does_not_exist() {
            deleteInterestTopic(UUID.randomUUID()).expectStatus().isNotFound();
        }

        @Test
        void should_return_400_for_a_malformed_id() {
            restTestClient.delete()
                    .uri("/api/v1/interest-topics/not-a-uuid")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    private RestTestClient.ResponseSpec createInterestTopic(String name, String description, String prompt, List<UUID> categoryIds) {
        CreateInterestTopicRequestDTO request = new CreateInterestTopicRequestDTO();
        request.setName(name);
        request.setDescription(description);
        request.setPrompt(prompt);
        request.setCategoryIds(categoryIds);

        return restTestClient.post()
                .uri("/api/v1/interest-topics")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private RestTestClient.ResponseSpec updateInterestTopic(UUID topicId, String name, String description, String prompt, List<UUID> categoryIds) {
        return restTestClient.patch()
                .uri("/api/v1/interest-topics/" + topicId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateRequest(name, description, prompt, categoryIds))
                .exchange();
    }

    private RestTestClient.ResponseSpec deleteInterestTopic(UUID topicId) {
        return restTestClient.delete()
                .uri("/api/v1/interest-topics/" + topicId)
                .exchange();
    }

    private UpdateInterestTopicRequestDTO updateRequest(String name, String description, String prompt, List<UUID> categoryIds) {
        UpdateInterestTopicRequestDTO request = new UpdateInterestTopicRequestDTO();
        request.setName(name);
        request.setDescription(description);
        request.setPrompt(prompt);
        request.setCategoryIds(categoryIds);
        return request;
    }

    private Category persistedCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private InterestTopic persistedInterestTopic(String name, Category... categories) {
        InterestTopic interestTopic = new InterestTopic();
        interestTopic.setName(name);
        interestTopic.setPrompt(TOPIC_PROMPT);
        interestTopic.setCategories(Set.of(categories));
        return interestTopicRepository.save(interestTopic);
    }

    private List<String> storedCategoryNames(UUID topicId) {
        return interestTopicRepository
                .findCategoryNamesByTopicIds(List.of(topicId))
                .stream()
                .map(TopicCategoryNameProjection::getCategoryName)
                .sorted()
                .toList();
    }

    private List<InterestTopic> namedInterestTopics(int count, Category category) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    InterestTopic interestTopic = new InterestTopic();
                    interestTopic.setName("topic-" + i);
                    interestTopic.setPrompt(TOPIC_PROMPT);
                    interestTopic.setCategories(Set.of(category));
                    return interestTopic;
                })
                .toList();
    }

    private TopicNews persistedTopicNews(InterestTopic interestTopic) {
        TopicNews topicNews = new TopicNews();
        topicNews.setInterestTopic(interestTopic);
        topicNews.setNewsDate(LocalDate.now());
        topicNews.setData("Canned news.");
        topicNews.setStatus(NewsStatus.PENDING);
        topicNews.setNextAttemptAt(Instant.now());
        return topicNewsRepository.save(topicNews);
    }
}
