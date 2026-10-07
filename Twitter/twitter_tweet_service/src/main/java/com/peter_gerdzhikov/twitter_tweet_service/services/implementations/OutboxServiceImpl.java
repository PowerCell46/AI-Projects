package com.peter_gerdzhikov.twitter_tweet_service.services.implementations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_tweet_service.documents.OutboxMessage;
import com.peter_gerdzhikov.twitter_tweet_service.documents.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_tweet_service.repositories.outbox.OutboxMessageRepository;
import com.peter_gerdzhikov.twitter_tweet_service.services.interfaces.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final Clock clock;

    private final ObjectMapper objectMapper;

    private final OutboxMessageRepository outboxMessageRepository;

    @Override
    public void enqueue(String topic, String messageKey, Object payload) {
        OutboxMessage message = OutboxMessage
                .builder()
                .id(UUID.randomUUID())
                .topic(topic)
                .messageKey(messageKey)
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now(clock).truncatedTo(ChronoUnit.MILLIS))
                .build();

        outboxMessageRepository.insert(message);
    }
}
