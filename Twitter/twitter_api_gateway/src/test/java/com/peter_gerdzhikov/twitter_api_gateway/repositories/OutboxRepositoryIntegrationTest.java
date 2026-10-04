package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;

import jakarta.persistence.EntityManager;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String TOPIC = "outbox-repository-test-topic";

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void should_return_pending_rows_oldest_first() {
        Outbox middle = savedRow(OutboxStatus.PENDING, CREATED_AT.plusSeconds(10));
        Outbox newest = savedRow(OutboxStatus.PENDING, CREATED_AT.plusSeconds(20));
        Outbox oldest = savedRow(OutboxStatus.PENDING, CREATED_AT);

        List<Outbox> page = pendingPage(10);

        assertThat(page)
                .extracting(Outbox::getId)
                .containsSubsequence(oldest.getId(), middle.getId(), newest.getId());
    }

    @Test
    void should_return_at_most_the_requested_page_size() {
        savedRow(OutboxStatus.PENDING, CREATED_AT);
        savedRow(OutboxStatus.PENDING, CREATED_AT.plusSeconds(1));
        savedRow(OutboxStatus.PENDING, CREATED_AT.plusSeconds(2));

        assertThat(pendingPage(2)).hasSize(2);
    }

    @Test
    void should_never_return_failed_rows() {
        Outbox failed = savedRow(OutboxStatus.FAILED, CREATED_AT);

        assertThat(pendingPage(10))
                .extracting(Outbox::getId)
                .doesNotContain(failed.getId());
    }

    private List<Outbox> pendingPage(int size) {
        return outboxRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, size));
    }

    private Outbox savedRow(OutboxStatus status, Instant createdAt) {
        Outbox row = Outbox.builder()
                .topic(TOPIC)
                .messageKey(UUID.randomUUID().toString())
                .payload("{}")
                .status(status)
                .build();
        Outbox saved = outboxRepository.saveAndFlush(row);
        jdbcTemplate.update(
                "UPDATE outbox SET created_at = ? WHERE id = ?",
                Timestamp.from(createdAt),
                saved.getId()
        );
        entityManager.clear();
        return outboxRepository.findById(saved.getId()).orElseThrow();
    }
}
