package com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.peter_gerdzhikov.twitter_shared.outbox.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;

public interface OutboxMessageRepository extends MongoRepository<OutboxMessage, UUID> {

    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);

    long countByStatus(OutboxStatus status);
}
