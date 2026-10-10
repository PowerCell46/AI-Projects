package com.peter_gerdzhikov.twitter_tweet_service.configurations.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMinioIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxHealthIntegrationTest extends AbstractMinioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Test
    void should_report_degraded_with_http_200_when_an_outbox_message_is_failed() throws Exception {
        OutboxMessage failed = outboxMessageRepository.save(
                TestDocuments.outboxMessage(OutboxStatus.FAILED, TestDocuments.CREATED_AT));

        try {
            mockMvc
                    .perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DEGRADED"));

        } finally {
            outboxMessageRepository.delete(failed);
        }
    }
}
