package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.twitter_shared.outbox.OutboxPublisher;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxPublisherService;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxRecord;
import com.peter_gerdzhikov.twitter_shared.outbox.OutboxStatus;
import com.peter_gerdzhikov.twitter_shared.outbox.PendingOutboxStore;
import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;

@Service
public class OutboxPublisherServiceImpl implements OutboxPublisherService {

    private final OutboxPublisher publisher;

    private final PendingOutboxStore store;

    public OutboxPublisherServiceImpl(
            @Value("${app.outbox.batch-size}") int batchSize,
            @Value("${app.outbox.max-attempts}") int maxAttempts,
            @Value("${app.outbox.send-timeout}") Duration sendTimeout,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxMessageRepository outboxMessageRepository
    ) {
        this.publisher = new OutboxPublisher(batchSize, maxAttempts, sendTimeout, kafkaTemplate);
        this.store = new RepositoryPendingOutboxStore(outboxMessageRepository);
    }

    @Override
    public int publishPending() {
        return publisher.publishPending(store);
    }

    private static class RepositoryPendingOutboxStore implements PendingOutboxStore {

        private final OutboxMessageRepository outboxMessageRepository;

        private RepositoryPendingOutboxStore(OutboxMessageRepository outboxMessageRepository) {
            this.outboxMessageRepository = outboxMessageRepository;
        }

        @Override
        public List<OutboxRecord> loadPending(int batchSize) {
            List<OutboxMessage> messages = outboxMessageRepository.findByStatusOrderByCreatedAtAsc(
                    OutboxStatus.PENDING,
                    PageRequest.of(0, batchSize)
            );

            return messages.stream().<OutboxRecord>map(MessageOutboxRecord::new).toList();
        }

        @Override
        public void delete(OutboxRecord record) {
            outboxMessageRepository.delete(((MessageOutboxRecord) record).message());
        }

        @Override
        public void save(OutboxRecord record) {
            outboxMessageRepository.save(((MessageOutboxRecord) record).message());
        }
    }

    private static class MessageOutboxRecord implements OutboxRecord {

        private final OutboxMessage message;

        private MessageOutboxRecord(OutboxMessage message) {
            this.message = message;
        }

        private OutboxMessage message() {
            return message;
        }

        @Override
        public Object id() {
            return message.getId();
        }

        @Override
        public String topic() {
            return message.getTopic();
        }

        @Override
        public String messageKey() {
            return message.getMessageKey();
        }

        @Override
        public String payload() {
            return message.getPayload();
        }

        @Override
        public int attempts() {
            return message.getAttempts();
        }

        @Override
        public void attempts(int attempts) {
            message.setAttempts(attempts);
        }

        @Override
        public OutboxStatus status() {
            return message.getStatus();
        }

        @Override
        public void status(OutboxStatus status) {
            message.setStatus(status);
        }
    }
}
