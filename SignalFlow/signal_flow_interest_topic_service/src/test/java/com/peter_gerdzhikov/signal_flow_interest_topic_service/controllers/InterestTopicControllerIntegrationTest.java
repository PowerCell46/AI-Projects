package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.CreateInterestTopicRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.UpdateInterestTopicRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.NewsStatus;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.TopicNews;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.TopicNewsRepository;
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

            InterestTopicResponseDTO body = createInterestTopic(TOPIC_NAME, "A systems language", TOPIC_PROMPT, categoryId)
                    .expectStatus().isCreated()
                    .expectBody(InterestTopicResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isNotNull();
            assertThat(body.getName()).isEqualTo(TOPIC_NAME);
            assertThat(body.getCategoryId()).isEqualTo(categoryId);
            assertThat(body.getCategoryName()).isEqualTo("programming");
        }

        @Test
        void should_store_the_name_lowercased() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic("Rust", null, TOPIC_PROMPT, categoryId).expectStatus().isCreated();

            assertThat(interestTopicRepository.findAll())
                    .extracting(InterestTopic::getName)
                    .containsExactly(TOPIC_NAME);
        }

        @Test
        void should_return_404_for_an_unknown_category_id() {
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, UUID.randomUUID()).expectStatus().isNotFound();
        }

        @Test
        void should_return_409_for_a_duplicate_name() {
            UUID categoryId = persistedCategory("programming").getId();
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, categoryId).expectStatus().isCreated();

            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, categoryId).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_409_for_a_duplicate_name_differing_only_in_case() {
            UUID categoryId = persistedCategory("programming").getId();
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, categoryId).expectStatus().isCreated();

            createInterestTopic("Rust", null, TOPIC_PROMPT, categoryId).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_for_a_blank_name() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(" ", null, TOPIC_PROMPT, categoryId).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_blank_prompt() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, null, " ", categoryId).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_missing_category_id() {
            createInterestTopic(TOPIC_NAME, null, TOPIC_PROMPT, null).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_name_over_100_characters() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic("a".repeat(101), null, TOPIC_PROMPT, categoryId).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_description_over_1000_characters() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, "a".repeat(1001), TOPIC_PROMPT, categoryId).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_prompt_over_4000_characters() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, null, "a".repeat(4001), categoryId).expectStatus().isBadRequest();
        }

        @Test
        void should_accept_a_4000_character_prompt_in_a_multi_byte_script() {
            UUID categoryId = persistedCategory("programming").getId();

            createInterestTopic(TOPIC_NAME, "新".repeat(1000), "新".repeat(4000), categoryId).expectStatus().isCreated();
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

            assertThat(body.getName()).isEqualTo("kotlin");
            assertThat(body.getPrompt()).isEqualTo(TOPIC_PROMPT);
        }

        @Test
        void should_return_404_when_the_topic_does_not_exist() {
            updateInterestTopic(UUID.randomUUID(), "kotlin", null, null, null).expectStatus().isNotFound();
        }

        @Test
        void should_return_404_for_an_unknown_category_id() {
            Category category = persistedCategory("programming");
            InterestTopic interestTopic = persistedInterestTopic(TOPIC_NAME, category);

            updateInterestTopic(interestTopic.getId(), null, null, null, UUID.randomUUID()).expectStatus().isNotFound();
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

    private RestTestClient.ResponseSpec createInterestTopic(String name, String description, String prompt, UUID categoryId) {
        CreateInterestTopicRequestDTO request = new CreateInterestTopicRequestDTO();
        request.setName(name);
        request.setDescription(description);
        request.setPrompt(prompt);
        request.setCategoryId(categoryId);

        return restTestClient.post()
                .uri("/api/v1/interest-topics")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private RestTestClient.ResponseSpec updateInterestTopic(UUID topicId, String name, String description, String prompt, UUID categoryId) {
        return restTestClient.patch()
                .uri("/api/v1/interest-topics/" + topicId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(updateRequest(name, description, prompt, categoryId))
                .exchange();
    }

    private RestTestClient.ResponseSpec deleteInterestTopic(UUID topicId) {
        return restTestClient.delete()
                .uri("/api/v1/interest-topics/" + topicId)
                .exchange();
    }

    private UpdateInterestTopicRequestDTO updateRequest(String name, String description, String prompt, UUID categoryId) {
        UpdateInterestTopicRequestDTO request = new UpdateInterestTopicRequestDTO();
        request.setName(name);
        request.setDescription(description);
        request.setPrompt(prompt);
        request.setCategoryId(categoryId);
        return request;
    }

    private Category persistedCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private InterestTopic persistedInterestTopic(String name, Category category) {
        InterestTopic interestTopic = new InterestTopic();
        interestTopic.setName(name);
        interestTopic.setPrompt(TOPIC_PROMPT);
        interestTopic.setCategory(category);
        return interestTopicRepository.save(interestTopic);
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
