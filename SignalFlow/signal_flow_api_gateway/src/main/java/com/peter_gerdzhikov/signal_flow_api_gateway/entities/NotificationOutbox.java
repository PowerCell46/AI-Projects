package com.peter_gerdzhikov.signal_flow_api_gateway.entities;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.peter_gerdzhikov.signal_flow_api_gateway.entities.enums.NotificationOutboxStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per {@code (newsId, userId)} pair still owed a {@code topic-news.notification-requested} Kafka
 * send - the outbox half of the transactional inbox/outbox pair with {@link TopicNewsInbox}.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "notification_outbox",
        uniqueConstraints = @UniqueConstraint(columnNames = {"news_id", "user_id"}),
        indexes = @Index(name = "idx_notification_outbox_status_created_at", columnList = "status, created_at")
)
public class NotificationOutbox extends CommonEntity {

    @Column(name = "news_id", nullable = false)
    private UUID newsId;

    @Column(name = "interest_topic_id", nullable = false)
    private UUID interestTopicId;

    @Column(name = "topic_name", nullable = false)
    private String topicName;

    @Column(name = "category_name", nullable = false)
    private String categoryName;

    @Column(name = "news_date", nullable = false)
    private LocalDate newsDate;

    @Column(nullable = false, columnDefinition = "text")
    private String data;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "email_address", nullable = false)
    private String emailAddress;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
}
