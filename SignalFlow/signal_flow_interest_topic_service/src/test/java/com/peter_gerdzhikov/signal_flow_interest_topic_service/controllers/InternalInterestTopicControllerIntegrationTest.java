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
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.response.ExistingInterestTopicsResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.Category;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.CategoryRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.repositories.InterestTopicRepository;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestTestClient
class InternalInterestTopicControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EXISTING_PATH = "/internal/v1/interest-topics/existing";

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

    private RestTestClient.ResponseSpec findExisting(List<UUID> ids) {
        ExistingInterestTopicsRequestDTO request = new ExistingInterestTopicsRequestDTO();
        request.setIds(ids);

        return restTestClient.post()
                .uri(EXISTING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange();
    }

    private RestTestClient.ResponseSpec postRawBody(String body) {
        return restTestClient.post()
                .uri(EXISTING_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange();
    }

    private InterestTopic persistedInterestTopic(String name) {
        Category category = new Category();
        category.setName("programming");

        InterestTopic interestTopic = new InterestTopic();
        interestTopic.setName(name);
        interestTopic.setPrompt("What's new with " + name + "?");
        interestTopic.setCategory(categoryRepository.save(category));
        return interestTopicRepository.save(interestTopic);
    }
}
