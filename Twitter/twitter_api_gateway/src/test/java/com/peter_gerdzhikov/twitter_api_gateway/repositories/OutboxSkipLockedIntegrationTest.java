package com.peter_gerdzhikov.twitter_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.support.AbstractPostgresIntegrationTest;

/**
 * The rows are committed and two real transactions compete for them, so the test itself must not run in a
 * transaction.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxSkipLockedIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final int PAGE_SIZE = 1000;

    private final String topic = "outbox-skip-locked-test-" + UUID.randomUUID();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    private List<UUID> ownRowIds;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        ownRowIds = transaction.execute(status -> List.of(savedRowId(), savedRowId()));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM outbox WHERE topic = ?", topic);
    }

    @Test
    void should_skip_rows_another_transaction_has_locked() {
        List<UUID> seenByTheSecondTransaction = transaction.execute(first -> {
            lockedPendingIds();

            return CompletableFuture
                    .supplyAsync(() -> transaction.execute(second -> lockedPendingIds()))
                    .join();
        });

        assertThat(seenByTheSecondTransaction).doesNotContainAnyElementsOf(ownRowIds);
    }

    @Test
    void should_return_the_rows_again_once_the_first_transaction_has_ended() {
        transaction.executeWithoutResult(first -> lockedPendingIds());

        List<UUID> seenByTheSecondTransaction = transaction.execute(second -> lockedPendingIds());

        assertThat(seenByTheSecondTransaction).containsAll(ownRowIds);
    }

    private List<UUID> lockedPendingIds() {
        return outboxRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, PAGE_SIZE))
                .stream()
                .filter(row -> topic.equals(row.getTopic()))
                .map(Outbox::getId)
                .toList();
    }

    private UUID savedRowId() {
        Outbox row = Outbox.builder()
                .topic(topic)
                .messageKey(UUID.randomUUID().toString())
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .build();

        return outboxRepository.save(row).getId();
    }
}
