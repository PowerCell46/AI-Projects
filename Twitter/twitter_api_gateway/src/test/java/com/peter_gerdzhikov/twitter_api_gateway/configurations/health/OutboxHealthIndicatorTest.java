package com.peter_gerdzhikov.twitter_api_gateway.configurations.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;

@ExtendWith(MockitoExtension.class)
class OutboxHealthIndicatorTest {

    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private OutboxHealthIndicator indicator;

    @Test
    void should_report_up_when_no_row_is_failed() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(0L);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_report_degraded_with_the_failed_count_when_rows_are_failed() {
        when(outboxRepository.countByStatus(OutboxStatus.FAILED)).thenReturn(3L);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED");
        assertThat(health.getDetails()).containsEntry("failedRows", 3L);
    }
}
