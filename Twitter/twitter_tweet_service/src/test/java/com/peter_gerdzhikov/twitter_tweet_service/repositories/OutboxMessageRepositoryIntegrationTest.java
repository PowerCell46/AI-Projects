package com.peter_gerdzhikov.twitter_tweet_service.repositories;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexInfo;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.support.AbstractMongoIntegrationTest;
import com.peter_gerdzhikov.twitter_tweet_service.support.TestDocuments;

@DataMongoTest
class OutboxMessageRepositoryIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Nested
    class FindByStatusOrderByCreatedAtAsc {

        @Test
        void should_return_pending_messages_oldest_first() {
            OutboxMessage middle = saved(OutboxStatus.PENDING, 10);
            OutboxMessage newest = saved(OutboxStatus.PENDING, 20);
            OutboxMessage oldest = saved(OutboxStatus.PENDING, 0);

            List<OutboxMessage> page = pendingPage(1000);

            assertThat(page)
                    .extracting(OutboxMessage::getId)
                    .containsSubsequence(oldest.getId(), middle.getId(), newest.getId());
        }

        @Test
        void should_return_at_most_the_requested_page_size() {
            saved(OutboxStatus.PENDING, 0);
            saved(OutboxStatus.PENDING, 1);
            saved(OutboxStatus.PENDING, 2);

            assertThat(pendingPage(2)).hasSize(2);
        }

        @Test
        void should_never_return_failed_messages() {
            OutboxMessage failed = saved(OutboxStatus.FAILED, 0);

            assertThat(pendingPage(1000))
                    .extracting(OutboxMessage::getId)
                    .doesNotContain(failed.getId());
        }
    }

    @Nested
    class Indexes {

        @Test
        void should_have_the_status_and_created_at_compound_index() {
            List<IndexInfo> indexes = mongoTemplate
                    .indexOps(OutboxMessage.class)
                    .getIndexInfo();

            assertThat(indexes)
                    .filteredOn(index -> index.getName().equals("ix_outbox_status_created_at"))
                    .singleElement()
                    .satisfies(index -> assertThat(index.getIndexFields())
                            .extracting(field -> field.getKey())
                            .containsExactly("status", "createdAt"));
        }
    }

    private List<OutboxMessage> pendingPage(int size) {
        return outboxMessageRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING, PageRequest.of(0, size));
    }

    private OutboxMessage saved(OutboxStatus status, int offsetSeconds) {
        return outboxMessageRepository.save(
                TestDocuments.outboxMessage(status, TestDocuments.CREATED_AT.plusSeconds(offsetSeconds)));
    }
}
