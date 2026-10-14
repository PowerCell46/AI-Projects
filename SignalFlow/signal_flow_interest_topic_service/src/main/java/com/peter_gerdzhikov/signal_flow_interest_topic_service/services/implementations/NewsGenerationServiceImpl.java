package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

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
            You are a news curator. Report %s about the user's \
            topic below, using only what the web search returned. Report at most 5 items.
            Output only an HTML fragment - no preamble, no Markdown. Put the items in one <ul>, and write \
            every item exactly as:
            <li><strong>Headline</strong> One or two sentences of detail. <a href="source URL">Source name</a></li>
            <em> is allowed inside the detail. If you find nothing new worth reporting, output only a short \
            <p> note saying so instead of the list.""";

    private static final String ONE_DAY_SCOPE_TEMPLATE = "notable news published on or just before %s";

    private static final String PERIOD_SCOPE_TEMPLATE = "the most notable news published between %s and %s";

    private static final String COVERED_STORIES_INTRO =
            "For context, these stories were already covered for this topic recently:";

    private static final String COVERED_STORIES_ADVICE = "Prefer stories that are not on this list. If you do "
            + "include one of them, it should be because of a meaningful new development - say what's new.";

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
    public String generate(
            InterestTopic interestTopic,
            LocalDate newsDate,
            LocalDate periodStart,
            List<String> coveredHeadlines
    ) {
        OpenRouterChatResponseDTO response = call(buildRequest(interestTopic, newsDate, periodStart, coveredHeadlines));
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

    private OpenRouterChatRequestDTO buildRequest(
            InterestTopic interestTopic, LocalDate newsDate, LocalDate periodStart, List<String> coveredHeadlines) {
        OpenRouterChatMessageDTO systemMessage = new OpenRouterChatMessageDTO(
                "system",
                SYSTEM_PROMPT_TEMPLATE.formatted(newsScope(newsDate, periodStart)) + coveredStoriesBlock(coveredHeadlines));
        OpenRouterChatMessageDTO userMessage = new OpenRouterChatMessageDTO(
                "user", "%s%n%n%s".formatted(interestTopic.getName(), interestTopic.getPrompt()));

        return new OpenRouterChatRequestDTO(model, List.of(systemMessage, userMessage), maxTokens);
    }

    private String newsScope(LocalDate newsDate, LocalDate periodStart) {
        if (periodStart.equals(newsDate)) {
            return ONE_DAY_SCOPE_TEMPLATE.formatted(newsDate);
        }

        return PERIOD_SCOPE_TEMPLATE.formatted(periodStart, newsDate);
    }

    private String coveredStoriesBlock(List<String> coveredHeadlines) {
        if (CollectionUtils.isEmpty(coveredHeadlines)) {
            return "";
        }

        String headlineLines = coveredHeadlines
                .stream()
                .map(headline -> "- " + headline)
                .collect(Collectors.joining("\n"));

        return "\n\n" + COVERED_STORIES_INTRO + "\n" + headlineLines + "\n" + COVERED_STORIES_ADVICE;
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
