package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;
import com.peter_gerdzhikov.signal_flow_interest_topic_service.services.interfaces.NewsGenerationService;

/**
 * Canned stand-in for a real AI provider - not in this phase's scope. Returns deterministic text built
 * from the topic's own fields so callers have something plausible to store and publish.
 */
@Service
public class NewsGenerationServiceImpl implements NewsGenerationService {

    private static final String NEWS_TEMPLATE = "Mock news for '%s': %s";

    @Override
    public String generate(InterestTopic interestTopic) {
        return NEWS_TEMPLATE.formatted(interestTopic.getName(), interestTopic.getPrompt());
    }
}
