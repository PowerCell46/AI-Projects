package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.TopicNewsInboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.InboxCleanupService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class InboxCleanupServiceImpl implements InboxCleanupService {

    private final int retentionDays;

    private final TopicNewsInboxRepository topicNewsInboxRepository;

    public InboxCleanupServiceImpl(
            @Value("${app.notification-inbox.retention-days}") int retentionDays,
            TopicNewsInboxRepository topicNewsInboxRepository
    ) {
        this.retentionDays = retentionDays;
        this.topicNewsInboxRepository = topicNewsInboxRepository;
    }

    @Override
    public void deleteProcessedNewsOlderThanRetention() {
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        int deleted = topicNewsInboxRepository.deleteAllByProcessedAtBefore(cutoff);
        log.info("Notification inbox cleanup deleted {} rows older than {} days.", deleted, retentionDays);
    }
}
