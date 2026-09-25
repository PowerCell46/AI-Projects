package com.peter_gerdzhikov.signal_flow_api_gateway.services.implementations;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.peter_gerdzhikov.signal_flow_api_gateway.DTOs.event.TopicNewsEventDTO;
import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.EnabledSubscriberProjection;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.SubscriptionRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.repositories.TopicNewsInboxRepository;
import com.peter_gerdzhikov.signal_flow_api_gateway.services.interfaces.TopicNewsNotificationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TopicNewsNotificationServiceImpl implements TopicNewsNotificationService {

    /** Sorts before every other {@code uuid} in Postgres, so the first keyset page starts at the very beginning. */
    private static final UUID BEFORE_EVERY_USER_ID = new UUID(0L, 0L);

    /** Matches {@link com.peter_gerdzhikov.signal_flow_api_gateway.entities.NotificationOutbox}'s default {@code varchar(255)}. */
    private static final int MAX_COLUMN_LENGTH = 255;

    private static final String INSERT_INBOX_ROW_SQL = """
            INSERT INTO topic_news_inbox (news_id, processed_at) VALUES (?, ?)
            """;

    private static final String INSERT_OUTBOX_ROW_SQL = """
            INSERT INTO notification_outbox
                (id, created_at, updated_at, news_id, interest_topic_id, topic_name, category_name,
                 news_date, data, generated_at, user_id, email_address, status, attempt_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final int batchSize;

    private final int maxDataLength;

    private final SubscriptionRepository subscriptionRepository;

    private final TopicNewsInboxRepository topicNewsInboxRepository;

    private final JdbcTemplate jdbcTemplate;

    public TopicNewsNotificationServiceImpl(
            @Value("${app.subscriptions.notification-fanout.batch-size}") int batchSize,
            @Value("${app.subscriptions.notification-fanout.max-data-length}") int maxDataLength,
            SubscriptionRepository subscriptionRepository,
            TopicNewsInboxRepository topicNewsInboxRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this.batchSize = batchSize;
        this.maxDataLength = maxDataLength;
        this.subscriptionRepository = subscriptionRepository;
        this.topicNewsInboxRepository = topicNewsInboxRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void notifySubscribers(TopicNewsEventDTO event) {
        if (topicNewsInboxRepository.existsById(event.getNewsId())) {
            log.info("Skipped already-processed topic-news record '{}'.", event.getNewsId());
            return;
        }

        if (event.getData().length() > maxDataLength) {
            skipOversizedRecord(event.getNewsId(), "data is %d characters, over the %d cap"
                    .formatted(event.getData().length(), maxDataLength));
            return;
        }

        if (event.getTopicName().length() > MAX_COLUMN_LENGTH || event.getCategoryName().length() > MAX_COLUMN_LENGTH) {
            skipOversizedRecord(event.getNewsId(), "topicName/categoryName exceeds the %d-character column cap"
                    .formatted(MAX_COLUMN_LENGTH));
            return;
        }

        UUID after = BEFORE_EVERY_USER_ID;
        long queued = 0;

        List<EnabledSubscriberProjection> batch = nextBatch(event.getInterestTopicId(), after);
        while (!batch.isEmpty()) {
            insertOutboxRows(event, batch);
            queued += batch.size();
            after = batch.getLast().getUserId();
            batch = batch.size() < batchSize ? List.of() : nextBatch(event.getInterestTopicId(), after);
        }

        insertInboxRow(event.getNewsId());
        log.info("Queued {} topic-news notifications for news '{}'.", queued, event.getNewsId());
    }

    private List<EnabledSubscriberProjection> nextBatch(UUID interestTopicId, UUID after) {
        return subscriptionRepository.findEnabledSubscribersAfter(interestTopicId, after, PageRequest.of(0, batchSize));
    }

    private void insertInboxRow(UUID newsId) {
        jdbcTemplate.update(INSERT_INBOX_ROW_SQL, newsId, OffsetDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Marks the record processed rather than throwing: retrying changes nothing, since the payload is the
     * same every time, so treating this like a transient failure would just be retry noise. Fans out to
     * no one.
     */
    private void skipOversizedRecord(UUID newsId, String reason) {
        log.warn("Skipped topic-news record '{}': {}.", newsId, reason);
        insertInboxRow(newsId);
    }

    private void insertOutboxRows(TopicNewsEventDTO event, List<EnabledSubscriberProjection> subscribers) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime generatedAt = event.getGeneratedAt().atOffset(ZoneOffset.UTC);

        jdbcTemplate.batchUpdate(INSERT_OUTBOX_ROW_SQL, subscribers, subscribers.size(), (ps, subscriber) -> {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, now);
            ps.setObject(3, now);
            ps.setObject(4, event.getNewsId());
            ps.setObject(5, event.getInterestTopicId());
            ps.setString(6, event.getTopicName());
            ps.setString(7, event.getCategoryName());
            ps.setObject(8, event.getNewsDate());
            ps.setString(9, event.getData());
            ps.setObject(10, generatedAt);
            ps.setObject(11, subscriber.getUserId());
            ps.setString(12, subscriber.getEmail());
            ps.setString(13, NotificationOutboxStatus.PENDING.name());
            ps.setInt(14, 0);
        });
    }
}
