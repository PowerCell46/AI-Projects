package com.peter_gerdzhikov.signal_flow_interest_topic_service.entities;

import java.time.Instant;
import java.time.LocalDate;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

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

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
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

    @Column(name = "news_date", nullable = false)
    private LocalDate newsDate; // ? What does this do? Don't we already have createdAt and updatedAt

    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private NewsStatus status = NewsStatus.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt; // ? What is the point of this?

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError; // ? What is the point of this?

    @Column(name = "sent_at")
    private Instant sentAt;
}
