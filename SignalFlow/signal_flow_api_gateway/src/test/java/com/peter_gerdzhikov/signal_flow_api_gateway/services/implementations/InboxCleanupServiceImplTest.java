package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.TopicNewsInboxRepository;

@ExtendWith(MockitoExtension.class)
class InboxCleanupServiceImplTest {

    private static final int RETENTION_DAYS = 7;

    @Mock
    private TopicNewsInboxRepository topicNewsInboxRepository;

    private InboxCleanupServiceImpl inboxCleanupService;

    @BeforeEach
    void setUp() {
        inboxCleanupService = new InboxCleanupServiceImpl(RETENTION_DAYS, topicNewsInboxRepository);
    }

    @Nested
    class DeleteProcessedNewsOlderThanRetention {

        @Test
        void should_delete_rows_older_than_the_configured_retention_window() {
            when(topicNewsInboxRepository.deleteAllByProcessedAtBefore(any())).thenReturn(3);

            inboxCleanupService.deleteProcessedNewsOlderThanRetention();

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(topicNewsInboxRepository).deleteAllByProcessedAtBefore(cutoffCaptor.capture());
            Instant expectedCutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
            assertThat(cutoffCaptor.getValue()).isCloseTo(expectedCutoff, within(1, ChronoUnit.SECONDS));
        }

        @Test
        void should_do_nothing_notable_when_no_rows_are_old_enough() {
            when(topicNewsInboxRepository.deleteAllByProcessedAtBefore(any())).thenReturn(0);

            inboxCleanupService.deleteProcessedNewsOlderThanRetention();

            verify(topicNewsInboxRepository).deleteAllByProcessedAtBefore(any());
        }
    }
}
