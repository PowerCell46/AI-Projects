package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        outboxService = new OutboxServiceImpl(JsonMapper.builder().build(), outboxRepository);
    }

    @Test
    void should_save_a_pending_row_with_the_topic_key_and_json_payload() {
        outboxService.enqueue("some.topic", "some-key", Map.of("answer", 42));

        Outbox saved = savedRow();

        assertThat(saved.getTopic()).isEqualTo("some.topic");
        assertThat(saved.getMessageKey()).isEqualTo("some-key");
        assertThat(saved.getPayload()).isEqualTo("{\"answer\":42}");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void should_start_the_row_with_zero_attempts() {
        outboxService.enqueue("some.topic", "some-key", Map.of());

        assertThat(savedRow().getAttempts()).isZero();
    }

    private Outbox savedRow() {
        ArgumentCaptor<Outbox> captor = ArgumentCaptor.forClass(Outbox.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
