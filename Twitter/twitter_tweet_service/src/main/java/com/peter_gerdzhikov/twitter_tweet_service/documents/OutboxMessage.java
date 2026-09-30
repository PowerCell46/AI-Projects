package com.peter_gerdzhikov.twitter_tweet_service.documents;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One Kafka message still owed to a topic, written in the same transaction as the tweet change it
 * announces. The payload is the event as a JSON string.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox")
@CompoundIndex(name = "ix_outbox_status_created_at", def = "{'status': 1, 'createdAt': 1}")
public class OutboxMessage {

    @Id
    private UUID id;

    private int attempts;

    private String topic;

    private String payload;

    private Instant createdAt;

    private String messageKey;

    private OutboxStatus status;
}
