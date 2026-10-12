package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.outbox;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxPublisher;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxPublisherService;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxRecord;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxStatus;
import com.peter_gerdzhikov.twitter_shared.outbox.PendingOutboxStore;

@Service
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private final OutboxPublisher publisher;

    private final PendingOutboxStore store;

    public OutboxPublisherServiceImpl(
            @Value("${app.outbox.batch-size}") int batchSize,
            @Value("${app.outbox.max-attempts}") int maxAttempts,
            @Value("${app.outbox.send-timeout}") Duration sendTimeout,
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.publisher = new OutboxPublisher(batchSize, maxAttempts, sendTimeout, kafkaTemplate);
        this.store = new RepositoryPendingOutboxStore(outboxRepository);
    }

    @Override
    @Transactional
    public int publishPending() {
        return publisher.publishPending(store);
    }

    private static class RepositoryPendingOutboxStore implements PendingOutboxStore {

        private final OutboxRepository outboxRepository;

        private RepositoryPendingOutboxStore(OutboxRepository outboxRepository) {
            this.outboxRepository = outboxRepository;
        }

        @Override
        public List<OutboxRecord> loadPending(int batchSize) {
            List<Outbox> rows = outboxRepository.findByStatusOrderByCreatedAtAsc(
                    OutboxStatus.PENDING,
                    PageRequest.of(0, batchSize)
            );

            return rows.stream().<OutboxRecord>map(RowOutboxRecord::new).toList();
        }

        @Override
        public void delete(OutboxRecord record) {
            outboxRepository.delete(((RowOutboxRecord) record).row());
        }

        @Override
        public void save(OutboxRecord record) {
            outboxRepository.save(((RowOutboxRecord) record).row());
        }
    }

    private static class RowOutboxRecord implements OutboxRecord {

        private final Outbox row;

        private RowOutboxRecord(Outbox row) {
            this.row = row;
        }

        private Outbox row() {
            return row;
        }

        @Override
        public Object id() {
            return row.getId();
        }

        @Override
        public String topic() {
            return row.getTopic();
        }

        @Override
        public String messageKey() {
            return row.getMessageKey();
        }

        @Override
        public String payload() {
            return row.getPayload();
        }

        @Override
        public int attempts() {
            return row.getAttempts();
        }

        @Override
        public void attempts(int attempts) {
            row.setAttempts(attempts);
        }

        @Override
        public OutboxStatus status() {
            return row.getStatus();
        }

        @Override
        public void status(OutboxStatus status) {
            row.setStatus(status);
        }
    }
}
