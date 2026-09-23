package com.peter_gerdzhikov.signal_flow_interest_topic_service.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.ExistingInterestTopicsRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.request.InterestTopicFeedRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ExistingInterestTopicsResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicFeedResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.InterestTopicResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopicFeedMode;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
class InternalInterestTopicControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EXISTING_PATH = "/internal/v1/interest-topics/existing";

    private static final String FEED_PATH = "/internal/v1/interest-topics/feed";

    @Autowired
    private RestTestClient restTestClient;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private InterestTopicRepository interestTopicRepository;

    @Value("${app.request.max-body-bytes}")
    private int maxRequestBodyBytes;

    @BeforeEach
    void clearTopicsAndCategories() {
        interestTopicRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Nested
    class FindExistingInterestTopics {

        @Test
        void should_return_only_the_ids_that_exist() {
            UUID existingId = persistedInterestTopic("rust").getId();

            ExistingInterestTopicsResponseDTO body = findExisting(List.of(existingId, UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(ExistingInterestTopicsResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getExistingIds()).containsExactly(existingId);
        }

        @Test
        void should_return_an_empty_list_when_none_exist() {
            ExistingInterestTopicsResponseDTO body = findExisting(List.of(UUID.randomUUID()))
                    .expectStatus().isOk()
                    .expectBody(ExistingInterestTopicsResponseDTO.class)
                    .returnResult()
                    .getResponseBody();

            assertThat(body.getExistingIds()).isEmpty();
        }

        @Test
        void should_return_400_when_ids_is_missing() {
            postRawBody("{}").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_when_an_id_is_null() {
            postRawBody("{\"ids\": [null]}").expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_when_an_id_is_malformed() {
            postRawBody("{\"ids\": [\"not-a-uuid\"]}").expectStatus().isBadRequest();
        }

        @Test
        void should_return_413_for_a_body_over_the_size_cap() {
            int idCount = maxRequestBodyBytes / UUID.randomUUID().toString().length();
            List<UUID> ids = Collections.nCopies(idCount, UUID.randomUUID());

            findExisting(ids).expectStatus().isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        }

    }

    @Nested
    class FindInterestTopicFeed {

        private Category category;

        @BeforeEach
        void persistCategory() {
            category = persistedCategory();
        }

        @Test
        void should_return_the_first_page_in_name_order_with_a_cursor_to_the_next() {
            List.of("rust", "go", "kotlin").forEach(name -> persistedInterestTopic(name, category));

            InterestTopicFeedResponseDTO body = feedBody(feedRequest(List.of(), InterestTopicFeedMode.ALL, null, 2));

            assertThat(body.getItems()).extracting(InterestTopicResponseDTO::getName).containsExactly("go", "kotlin");
            assertThat(body.getNextCursor()).isEqualTo("kotlin");
        }

        @Test
        void should_continue_after_the_cursor_and_return_no_cursor_on_the_last_page() {
            List.of("rust", "go", "kotlin").forEach(name -> persistedInterestTopic(name, category));

            InterestTopicFeedResponseDTO body = feedBody(feedRequest(List.of(), InterestTopicFeedMode.ALL, "kotlin", 2));

            assertThat(body.getItems()).extracting(InterestTopicResponseDTO::getName).containsExactly("rust");
            assertThat(body.getNextCursor()).isNull();
        }

        @Test
        void should_return_only_the_given_ids_when_including() {
            UUID go = persistedInterestTopic("go", category).getId();
            persistedInterestTopic("rust", category);

            InterestTopicFeedResponseDTO body = feedBody(feedRequest(List.of(go), InterestTopicFeedMode.INCLUDE, null, 20));

            assertThat(body.getItems()).extracting(InterestTopicResponseDTO::getName).containsExactly("go");
        }

        @Test
        void should_leave_out_the_given_ids_when_excluding() {
            UUID go = persistedInterestTopic("go", category).getId();
            persistedInterestTopic("rust", category);

            InterestTopicFeedResponseDTO body = feedBody(feedRequest(List.of(go), InterestTopicFeedMode.EXCLUDE, null, 20));

            assertThat(body.getItems()).extracting(InterestTopicResponseDTO::getName).containsExactly("rust");
        }

        @Test
        void should_count_every_topic_and_only_the_given_ids_that_still_exist() {
            UUID go = persistedInterestTopic("go", category).getId();
            persistedInterestTopic("rust", category);
            List<UUID> ids = List.of(go, UUID.randomUUID());

            InterestTopicFeedResponseDTO body = feedBody(feedRequest(ids, InterestTopicFeedMode.ALL, null, 20));

            assertThat(body.getTotal()).isEqualTo(2);
            assertThat(body.getMatching()).isEqualTo(1);
        }

        @Test
        void should_embed_the_category_and_not_expose_the_prompt() {
            persistedInterestTopic("go", category);

            feed(feedRequest(List.of(), InterestTopicFeedMode.ALL, null, 20))
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.items[0].categoryName").isEqualTo("programming")
                    .jsonPath("$.items[0].prompt").doesNotExist();
        }

        @Test
        void should_return_400_when_the_mode_is_missing() {
            feed(feedRequest(List.of(), null, null, 20)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_when_ids_is_missing() {
            feed(feedRequest(null, InterestTopicFeedMode.ALL, null, 20)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_when_the_size_is_out_of_range() {
            feed(feedRequest(List.of(), InterestTopicFeedMode.ALL, null, 101)).expectStatus().isBadRequest();
        }

        @Test
        void should_return_400_for_an_unknown_mode() {
            restTestClient.post()
                    .uri(FEED_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"ids\": [], \"mode\": \"SOME\", \"size\": 20}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }
    }

    private RestTestClient.ResponseSpec findExisting(List<UUID> ids) {
        ExistingInterestTopicsRequestDTO request = new ExistingInterestTopicsRequestDTO();
        request.setIds(ids);

        return restTestClient.post()
                .uri(EXISTING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private InterestTopicFeedRequestDTO feedRequest(
            List<UUID> ids,
            InterestTopicFeedMode mode,
            String after,
            int size
    ) {
        InterestTopicFeedRequestDTO request = new InterestTopicFeedRequestDTO();
        request.setIds(ids);
        request.setMode(mode);
        request.setAfter(after);
        request.setSize(size);
        return request;
    }

    private RestTestClient.ResponseSpec feed(InterestTopicFeedRequestDTO request) {
        return restTestClient.post()
                .uri(FEED_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private InterestTopicFeedResponseDTO feedBody(InterestTopicFeedRequestDTO request) {
        return feed(request)
                .expectStatus().isOk()
                .expectBody(InterestTopicFeedResponseDTO.class)
                .returnResult()
                .getResponseBody();
    }

    private RestTestClient.ResponseSpec postRawBody(String body) {
        return restTestClient.post()
                .uri(EXISTING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private InterestTopic persistedInterestTopic(String name) {
        return persistedInterestTopic(name, persistedCategory());
    }

    private InterestTopic persistedInterestTopic(String name, Category category) {
        InterestTopic interestTopic = new InterestTopic();
        interestTopic.setName(name);
        interestTopic.setPrompt("What's new with " + name + "?");
        interestTopic.setCategory(category);
        return interestTopicRepository.save(interestTopic);
    }

    private Category persistedCategory() {
        Category category = new Category();
        category.setName("programming");
        return categoryRepository.save(category);
    }
}
