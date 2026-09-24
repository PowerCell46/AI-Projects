package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationOutboxRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private NotificationOutboxRepository notificationOutboxRepository;

    @Test
    void should_save_and_find_a_pending_row_with_every_field_mapped() {
        UUID newsId = UUID.randomUUID();
        UUID interestTopicId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        LocalDate newsDate = LocalDate.now();
        Instant generatedAt = Instant.now();

        NotificationOutbox saved = notificationOutboxRepository.saveAndFlush(
                newOutboxRow(newsId, interestTopicId, userId, newsDate, generatedAt));

        NotificationOutbox found = notificationOutboxRepository.findById(saved.getId()).get();
        assertThat(found.getNewsId()).isEqualTo(newsId);
        assertThat(found.getInterestTopicId()).isEqualTo(interestTopicId);
        assertThat(found.getTopicName()).isEqualTo("rust");
        assertThat(found.getCategoryName()).isEqualTo("programming");
        assertThat(found.getNewsDate()).isEqualTo(newsDate);
        assertThat(found.getData()).isEqualTo("today's rust news");
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getEmailAddress()).isEqualTo("bob@example.com");
        assertThat(found.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(found.getAttemptCount()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void should_return_only_pending_rows_up_to_the_page_size() {
        notificationOutboxRepository.save(pendingRow());
        notificationOutboxRepository.save(pendingRow());
        NotificationOutbox failed = pendingRow();
        failed.setStatus(NotificationOutboxStatus.FAILED);
        notificationOutboxRepository.save(failed);

        List<NotificationOutbox> page = notificationOutboxRepository
                .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, 1));

        assertThat(page).hasSize(1);
        assertThat(page.getFirst().getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
    }

    @Test
    void should_return_pending_rows_ordered_by_created_at_ascending() throws InterruptedException {
        NotificationOutbox first = notificationOutboxRepository.saveAndFlush(pendingRow());
        Thread.sleep(5);
        NotificationOutbox second = notificationOutboxRepository.saveAndFlush(pendingRow());

        List<NotificationOutbox> page = notificationOutboxRepository
                .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, 10));

        assertThat(page)
                .extracting(NotificationOutbox::getId)
                .containsExactly(first.getId(), second.getId());
    }

    @Test
    void should_return_no_rows_when_none_are_pending() {
        List<NotificationOutbox> page = notificationOutboxRepository
                .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, 10));

        assertThat(page).isEmpty();
    }

    private NotificationOutbox pendingRow() {
        return newOutboxRow(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now(), Instant.now());
    }

    private NotificationOutbox newOutboxRow(
            UUID newsId, UUID interestTopicId, UUID userId, LocalDate newsDate, Instant generatedAt
    ) {
        return NotificationOutbox
                .builder()
                .newsId(newsId)
                .interestTopicId(interestTopicId)
                .topicName("rust")
                .categoryName("programming")
                .newsDate(newsDate)
                .data("today's rust news")
                .generatedAt(generatedAt)
                .userId(userId)
                .emailAddress("bob@example.com")
                .status(NotificationOutboxStatus.PENDING)
                .attemptCount(0)
                .build();
    }
}
