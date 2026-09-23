package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.peter_gerdzhikov.signal_flow_interest_topic_service.entities.enums.NewsStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "topic_news",
        indexes = @Index(columnList = "status, next_attempt_at"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"interest_topic_id", "news_date"})
)
public class TopicNews extends CommonEntity {

    @OnDelete(action = OnDeleteAction.CASCADE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interest_topic_id", nullable = false)
    private InterestTopic interestTopic;

    /**
     * The business day this news is for, distinct from {@code createdAt}/{@code updatedAt}: paired with
     * {@code interestTopic} in a unique constraint so a retry (e.g. after midnight) can't produce a
     * second row for the same topic on the same day.
     */
    @Column(name = "news_date", nullable = false)
    private LocalDate newsDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;

    @Builder.Default
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NewsStatus status = NewsStatus.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    /**
     * The outbox publisher's retry gate: a {@code PENDING} row is only picked up once this is
     * {@code <= now}. Set to "now" on creation, then pushed forward with exponential backoff on each
     * failed publish attempt.
     */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /**
     * The most recent publish failure's message, overwritten on every failed attempt. Write-only for
     * diagnostics — lets an operator see why a row is retrying or ended up {@code FAILED} without
     * correlating log lines.
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;
}
