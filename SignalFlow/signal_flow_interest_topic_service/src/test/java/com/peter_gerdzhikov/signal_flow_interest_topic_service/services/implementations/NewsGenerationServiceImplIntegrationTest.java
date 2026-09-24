package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.news.NewsGenerationFailedException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.NewsGenerationService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.support.AbstractOpenRouterIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
class NewsGenerationServiceImplIntegrationTest extends AbstractOpenRouterIntegrationTest {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final int DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS = 2000;

    private static final LocalDate NEWS_DATE = LocalDate.of(2026, 9, 24);

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Autowired
    private NewsGenerationService newsGenerationService;

    @BeforeEach
    void resetTheOpenRouterStandIn() {
        OPENROUTER_STUB.resetMappings();
        OPENROUTER_STUB.resetRequests();
    }

    @Test
    void should_return_the_message_content_from_a_successful_response() {
        stubChatCompletions(successResponse("<p>Rust 1.90 shipped.</p>"));

        String news = newsGenerationService.generate(newTopic("rust", "What's new with Rust?"), NEWS_DATE);

        assertThat(news).isEqualTo("<p>Rust 1.90 shipped.</p>");
    }

    @Test
    void should_send_the_model_max_tokens_and_bearer_key() {
        stubChatCompletions(successResponse("<p>news</p>"));

        newsGenerationService.generate(newTopic("rust", "What's new with Rust?"), NEWS_DATE);

        JsonNode body = requestBody();
        assertThat(body.path("model").asString()).isEqualTo("deepseek/deepseek-v4.1-flash:online");
        assertThat(body.path("max_tokens").asInt()).isEqualTo(2000);
        assertThat(onlyLoggedRequest().getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-key");
    }

    @Test
    void should_carry_the_format_rules_the_date_and_the_topic_in_the_messages() {
        stubChatCompletions(successResponse("<p>news</p>"));

        newsGenerationService.generate(newTopic("rust", "What's new with Rust?"), NEWS_DATE);

        JsonNode messages = requestBody().path("messages");
        String systemMessage = messages.get(0).path("content").asString();
        String userMessage = messages.get(1).path("content").asString();

        assertThat(systemMessage)
                .contains(NEWS_DATE.toString())
                .contains("5 items")
                .contains("<a href>");
        assertThat(userMessage)
                .contains("rust")
                .contains("What's new with Rust?");
    }

    @Test
    void should_send_a_prompt_with_special_characters_as_valid_unchanged_json() {
        stubChatCompletions(successResponse("<p>news</p>"));
        String trickyPrompt = "Cover \"Rust\", the \\ operator, and\nnew lines.";

        newsGenerationService.generate(newTopic("rust", trickyPrompt), NEWS_DATE);

        JsonNode userMessage = requestBody().path("messages").get(1);
        assertThat(userMessage.path("content").asString()).contains(trickyPrompt);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 402, 429, 500, 503})
    void should_fail_carrying_the_status_and_the_provider_message_on_a_non_2xx_response(int status) {
        stubChatCompletions(aResponse()
                .withStatus(status)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"error\": {\"message\": \"insufficient credits\"}}"));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class)
                .satisfies(e -> {
                    NewsGenerationFailedException failure = (NewsGenerationFailedException) e;
                    assertThat(failure.getStatus().value()).isEqualTo(status);
                    assertThat(failure.getMessage()).contains("insufficient credits");
                });
    }

    @Test
    void should_strip_cr_and_lf_from_the_provider_message_before_it_reaches_the_exception() {
        stubChatCompletions(aResponse()
                .withStatus(401)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("{\"error\": {\"message\": \"bad key\\n2026-09-24 ERROR fabricated log line\"}}"));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\n").doesNotContain("\r"));
    }

    @Test
    void should_fail_when_the_choices_field_is_missing() {
        stubChatCompletions(jsonResponse("{}"));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class);
    }

    @Test
    void should_fail_when_choices_is_empty() {
        stubChatCompletions(jsonResponse("{\"choices\": []}"));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n"})
    void should_fail_when_the_content_is_null_or_blank(String content) {
        stubChatCompletions(successResponse(content));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class);
    }

    @Test
    void should_fail_when_the_body_is_not_json() {
        stubChatCompletions(aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody("not json"));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class);
    }

    @Test
    void should_fail_when_the_call_times_out() {
        stubChatCompletions(successResponse("<p>news</p>")
                .withFixedDelay(DELAY_PAST_THE_TEST_READ_TIMEOUT_MILLIS));

        assertThatThrownBy(() -> newsGenerationService.generate(newTopic("rust", "prompt"), NEWS_DATE))
                .isInstanceOf(NewsGenerationFailedException.class);
    }

    private void stubChatCompletions(ResponseDefinitionBuilder response) {
        OPENROUTER_STUB.register(post(urlEqualTo(CHAT_COMPLETIONS_PATH)).willReturn(response));
    }

    private ResponseDefinitionBuilder successResponse(String content) {
        ObjectNode message = JSON_MAPPER.createObjectNode();
        if (content != null) {
            message.put("content", content);
        }

        ObjectNode choice = JSON_MAPPER.createObjectNode();
        choice.set("message", message);

        ArrayNode choices = JSON_MAPPER.createArrayNode();
        choices.add(choice);

        ObjectNode root = JSON_MAPPER.createObjectNode();
        root.set("choices", choices);

        return jsonResponse(JSON_MAPPER.writeValueAsString(root));
    }

    private ResponseDefinitionBuilder jsonResponse(String body) {
        return aResponse()
                .withStatus(200)
                .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .withBody(body);
    }

    private JsonNode requestBody() {
        return JSON_MAPPER.readTree(onlyLoggedRequest().getBodyAsString());
    }

    private LoggedRequest onlyLoggedRequest() {
        List<LoggedRequest> requests = OPENROUTER_STUB.find(postRequestedFor(urlEqualTo(CHAT_COMPLETIONS_PATH)));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    private InterestTopic newTopic(String name, String prompt) {
        InterestTopic topic = new InterestTopic();
        topic.setName(name);
        topic.setPrompt(prompt);
        return topic;
    }
}
