package com.peter_gerdzhikov.signal_flow_interest_topic_service.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.InterestTopic;

class NewsGenerationServiceImplTest {

    private final NewsGenerationServiceImpl newsGenerationService = new NewsGenerationServiceImpl();

    @Test
    void should_return_canned_text_mentioning_the_topic_name_and_prompt() {
        InterestTopic topic = new InterestTopic();
        topic.setName("rust");
        topic.setPrompt("What's new with Rust?");

        String news = newsGenerationService.generate(topic);

        assertThat(news)
                .contains("rust")
                .contains("What's new with Rust?");
    }
}
