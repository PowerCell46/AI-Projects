package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.CategoryRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.CategoryResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
class CategoryControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String CATEGORY_NAME = "programming";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Value("${app.request.max-topic-body-bytes}")
    private int maxTopicRequestBodyBytes;

    @Value("${app.category.max-count}")
    private int maxCategoryCount;

    @BeforeEach
    void clearTopicsAndCategories() {
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Nested
    class CreateCategory {

        @Test
        void should_create_a_category_and_return_it() {
            CategoryResponseDTO body = createCategory(CATEGORY_NAME)
                    .expectStatus().isCreated()
                    .expectBody(CategoryResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getId()).isNotNull();
            assertThat(body.getName()).isEqualTo(CATEGORY_NAME);
            assertThat(body.getCreatedAt()).isNotNull();
        }

        @Test
        void should_store_the_name_lowercased() {
            createCategory("Programming").expectStatus().isCreated();

            assertThat(categoryRepository.findAll())
                    .extracting(Category::getName)
                    .containsExactly(CATEGORY_NAME);
        }

        @Test
        void should_return_409_for_a_duplicate_name() {
            createCategory(CATEGORY_NAME).expectStatus().isCreated();

            createCategory(CATEGORY_NAME).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_409_for_a_duplicate_name_differing_only_in_case() {
            createCategory(CATEGORY_NAME).expectStatus().isCreated();

            createCategory("Programming").expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_for_a_blank_name() {
            createCategory(" ").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_name_over_100_characters() {
            createCategory("a".repeat(101)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_413_for_a_body_over_the_size_cap() {
            createCategory("a".repeat(maxTopicRequestBodyBytes)).expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);

            assertThat(categoryRepository.count()).isZero();
        }

        @Test
        void should_return_400_for_malformed_json() {
            restTestClient.post()
                    .uri("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{ not valid json")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        void should_not_leak_exception_or_package_names_in_the_error_body() {
            ErrorResponseDTO body = createCategory(" ")
                    .expectStatus().isBadRequest()
                    .expectBody(ErrorResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getMessages().getFirst()).doesNotContain("Exception", "com.peter_gerdzhikov");
        }

        @Test
        void should_return_409_when_the_category_count_is_at_the_limit() {
            categoryRepository.saveAll(namedCategories(maxCategoryCount));

            createCategory(CATEGORY_NAME).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class ListCategories {

        @Test
        void should_return_the_first_page_sorted_by_name_by_default() {
            createCategory("science").expectStatus().isCreated();
            createCategory("art").expectStatus().isCreated();

            restTestClient.get()
                    .uri("/api/v1/categories")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content[0].name").isEqualTo("art")
                    .jsonPath("$.content[1].name").isEqualTo("science")
                    .jsonPath("$.page.size").isEqualTo(20);
        }

        @Test
        void should_return_an_empty_page_when_no_categories_exist() {
            restTestClient.get()
                    .uri("/api/v1/categories")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.content.length()").isEqualTo(0);
        }
    }

    @Nested
    class RenameCategory {

        @Test
        void should_rename_the_category_and_return_it() {
            UUID categoryId = persistedCategory(CATEGORY_NAME).getId();

            CategoryResponseDTO body = renameCategory(categoryId, "science")
                    .expectStatus().isOk()
                    .expectBody(CategoryResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getName()).isEqualTo("science");
        }

        @Test
        void should_store_the_renamed_name_lowercased() {
            UUID categoryId = persistedCategory(CATEGORY_NAME).getId();

            renameCategory(categoryId, "Science").expectStatus().isOk();

            assertThat(categoryRepository.findById(categoryId).orElseThrow().getName()).isEqualTo("science");
        }

        @Test
        void should_return_404_when_the_category_does_not_exist() {
            renameCategory(UUID.randomUUID(), "science").expectStatus().isNotFound();
        }

        @Test
        void should_return_409_when_renaming_to_a_name_already_in_use() {
            persistedCategory(CATEGORY_NAME);
            UUID categoryId = persistedCategory("science").getId();

            renameCategory(categoryId, CATEGORY_NAME).expectStatus().isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void should_return_400_for_a_blank_name() {
            UUID categoryId = persistedCategory(CATEGORY_NAME).getId();

            renameCategory(categoryId, " ").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_a_malformed_id() {
            restTestClient.patch()
                    .uri("/api/v1/categories/not-a-uuid")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(categoryRequest("science"))
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    @Nested
    class DeleteCategory {

        @Test
        void should_return_204_and_remove_the_category() {
            UUID categoryId = persistedCategory(CATEGORY_NAME).getId();

            deleteCategory(categoryId).expectStatus().isNoContent();

            assertThat(categoryRepository.existsById(categoryId)).isFalse();
        }

        @Test
        void should_return_404_when_the_category_does_not_exist() {
            deleteCategory(UUID.randomUUID()).expectStatus().isNotFound();
        }

        @Test
        void should_return_409_when_referenced_by_an_interest_topic() {
            Category category = persistedCategory(CATEGORY_NAME);
            persistedInterestTopic(category);

            deleteCategory(category.getId()).expectStatus().isEqualTo(HttpStatus.CONFLICT);

            assertThat(categoryRepository.existsById(category.getId())).isTrue();
        }

        @Test
        void should_return_400_for_a_malformed_id() {
            restTestClient.delete()
                    .uri("/api/v1/categories/not-a-uuid")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    private RestTestClient.ResponseSpec createCategory(String name) {
        return restTestClient.post()
                .uri("/api/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .body(categoryRequest(name))
                .exchange();
    }

    private RestTestClient.ResponseSpec renameCategory(UUID categoryId, String name) {
        return restTestClient.patch()
                .uri("/api/v1/categories/" + categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(categoryRequest(name))
                .exchange();
    }

    private RestTestClient.ResponseSpec deleteCategory(UUID categoryId) {
        return restTestClient.delete()
                .uri("/api/v1/categories/" + categoryId)
                .exchange();
    }

    private CategoryRequestDTO categoryRequest(String name) {
        CategoryRequestDTO request = new CategoryRequestDTO();
        request.setName(name);
        return request;
    }

    private Category persistedCategory(String name) {
        Category category = new Category();
        category.setName(name);
        return categoryRepository.save(category);
    }

    private List<Category> namedCategories(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    Category category = new Category();
                    category.setName("category-" + i);
                    return category;
                })
                .toList();
    }

    private InterestTopic persistedInterestTopic(Category category) {
        InterestTopic interestTopic = new InterestTopic();
        interestTopic.setName("rust");
        interestTopic.setPrompt("What's new with Rust?");
        interestTopic.setCategory(category);
        return interestTopicRepository.save(interestTopic);
    }
}
