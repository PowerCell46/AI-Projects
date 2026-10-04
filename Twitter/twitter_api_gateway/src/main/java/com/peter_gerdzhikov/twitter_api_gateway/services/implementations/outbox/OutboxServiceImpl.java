package com.peter_gerdzhikov.twitter_api_gateway.services.implementations.outbox;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import com.peter_gerdzhikov.twitter_api_gateway.entities.outbox.Outbox;
import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;
import com.peter_gerdzhikov.twitter_api_gateway.repositories.OutboxRepository;
import com.peter_gerdzhikov.twitter_api_gateway.services.interfaces.outbox.OutboxService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxServiceImpl implements OutboxService {

    private final ObjectMapper objectMapper;

    private final OutboxRepository outboxRepository;

    @Override
    public void enqueue(String topic, String messageKey, Object payload) {
        Outbox row = Outbox.builder()
                .topic(topic)
                .messageKey(messageKey)
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxStatus.PENDING)
                .build();

        outboxRepository.save(row);
    }
}
