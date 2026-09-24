package com.peter_gerdzhikov.signal_flow_api_gateway.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.TopicNewsInbox;
import com.peter_gerdzhikov.signal_flow_api_gateway.support.AbstractPostgresIntegrationTest;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TopicNewsInboxRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TopicNewsInboxRepository topicNewsInboxRepository;

    @Test
    void should_save_and_find_a_processed_news_id() {
        UUID newsId = UUID.randomUUID();

        topicNewsInboxRepository.save(newInbox(newsId));

        assertThat(topicNewsInboxRepository.existsById(newsId)).isTrue();
        assertThat(topicNewsInboxRepository.findById(newsId).get().getProcessedAt()).isNotNull();
    }

    @Test
    void should_report_an_unprocessed_news_id_as_not_existing() {
        assertThat(topicNewsInboxRepository.existsById(UUID.randomUUID())).isFalse();
    }

    @Test
    void should_delete_only_rows_older_than_the_cutoff() {
        Instant now = Instant.now();
        UUID oldNewsId = UUID.randomUUID();
        UUID recentNewsId = UUID.randomUUID();
        topicNewsInboxRepository.saveAndFlush(newInbox(oldNewsId));
        topicNewsInboxRepository.saveAndFlush(newInbox(recentNewsId));

        int deleted = topicNewsInboxRepository.deleteAllByProcessedAtBefore(now.plus(1, ChronoUnit.DAYS));

        assertThat(deleted).isEqualTo(2);
        assertThat(topicNewsInboxRepository.findAll()).isEmpty();
    }

    @Test
    void should_keep_rows_at_or_after_the_cutoff() {
        UUID newsId = UUID.randomUUID();
        topicNewsInboxRepository.saveAndFlush(newInbox(newsId));

        int deleted = topicNewsInboxRepository.deleteAllByProcessedAtBefore(Instant.now().minus(1, ChronoUnit.DAYS));

        assertThat(deleted).isZero();
        assertThat(topicNewsInboxRepository.existsById(newsId)).isTrue();
    }

    private TopicNewsInbox newInbox(UUID newsId) {
        TopicNewsInbox inbox = new TopicNewsInbox();
        inbox.setNewsId(newsId);
        return inbox;
    }
}
