package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter.OpenRouterChatMessageDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter.OpenRouterChatRequestDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter.OpenRouterChatResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter.OpenRouterChoiceDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.DTOs.openrouter.OpenRouterErrorResponseDTO;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.exceptions.news.NewsGenerationFailedException;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.NewsGenerationService;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.utilities.LogSanitizer;

/**
 * Calls OpenRouter's chat-completions endpoint once per topic, grounded on the web through the
 * {@code :online} suffix already baked into {@code app.openrouter.model}.
 */
@Service
public class NewsGenerationServiceImpl implements NewsGenerationService {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String NO_PROVIDER_MESSAGE = "(no message)";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            You are a news curator. Report notable news published on or just before %s about the user's \
            topic below, using only what the web search returned. Cite each item with an <a href> link to \
            its source. Output only an HTML fragment built from <p>, <ul>, <li>, <strong>, <em> and <a> \
            tags - no preamble, no Markdown. Report at most 5 items.""";

    private final String model;

    private final Integer maxTokens;

    private final RestClient openRouterRestClient;

    public NewsGenerationServiceImpl(
            @Value("${app.openrouter.model}") String model,
            @Value("${app.openrouter.max-tokens}") Integer maxTokens,
            RestClient openRouterRestClient
    ) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.openRouterRestClient = openRouterRestClient;
    }

    @Override
    public String generate(InterestTopic interestTopic, LocalDate newsDate) {
        OpenRouterChatResponseDTO response = call(buildRequest(interestTopic, newsDate));
        return extractContent(response);
    }

    private OpenRouterChatResponseDTO call(OpenRouterChatRequestDTO request) {
        try {
            return openRouterRestClient
                    .post()
                    .uri(CHAT_COMPLETIONS_PATH)
                    .body(request)
                    .retrieve()
                    .body(OpenRouterChatResponseDTO.class);

        } catch (RestClientResponseException e) {
            throw new NewsGenerationFailedException(e.getStatusCode(), providerMessage(e));

        } catch (RestClientException e) {
            throw new NewsGenerationFailedException("Call to OpenRouter failed.", e);
        }
    }

    private String extractContent(OpenRouterChatResponseDTO response) {
        List<OpenRouterChoiceDTO> choices = response != null ? response.getChoices() : null;
        if (CollectionUtils.isEmpty(choices)) {
            throw new NewsGenerationFailedException("OpenRouter response had no choices.");
        }

        OpenRouterChatMessageDTO message = choices.get(0).getMessage();
        String content = message != null ? message.getContent() : null;
        if (!StringUtils.hasText(content)) {
            throw new NewsGenerationFailedException("OpenRouter response content was blank.");
        }

        return content;
    }

    private OpenRouterChatRequestDTO buildRequest(InterestTopic interestTopic, LocalDate newsDate) {
        OpenRouterChatMessageDTO systemMessage = new OpenRouterChatMessageDTO(
                "system", SYSTEM_PROMPT_TEMPLATE.formatted(newsDate));
        OpenRouterChatMessageDTO userMessage = new OpenRouterChatMessageDTO(
                "user", "%s%n%n%s".formatted(interestTopic.getName(), interestTopic.getPrompt()));

        return new OpenRouterChatRequestDTO(model, List.of(systemMessage, userMessage), maxTokens);
    }

    private String providerMessage(RestClientResponseException e) {
        try {
            OpenRouterErrorResponseDTO errorBody = e.getResponseBodyAs(OpenRouterErrorResponseDTO.class);
            Object message = errorBody != null && errorBody.getError() != null
                    ? errorBody.getError().get("message")
                    : null;
            return message != null ? LogSanitizer.sanitize(message.toString()) : NO_PROVIDER_MESSAGE;

        } catch (RestClientException unparseable) {
            return NO_PROVIDER_MESSAGE;
        }
    }
}
