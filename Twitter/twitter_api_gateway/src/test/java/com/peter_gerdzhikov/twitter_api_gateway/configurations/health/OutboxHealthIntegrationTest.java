package com.peter_gerdzhikov.twitter_api_gateway.configurations.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractMinioIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OutboxHealthIntegrationTest extends AbstractMinioIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void should_report_degraded_with_http_200_when_an_outbox_row_is_failed() throws Exception {
        Outbox failed = outboxRepository.save(Outbox.builder()
                .topic("some.topic")
                .messageKey("some-key")
                .payload("{}")
                .status(OutboxStatus.FAILED)
                .build());

        try {
            mockMvc
                    .perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DEGRADED"));

        } finally {
            outboxRepository.delete(failed);
        }
    }
}
