package com.peter_gerdzhikov.signal_flow_api_gateway.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One row per {@code topic-news.generated} record already fanned out. {@code newsId} is the record's own
 * id, not a surrogate, so a redelivered event's insert collides on the primary key instead of re-fanning-out.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "topic_news_inbox")
public class TopicNewsInbox {

    @Id
    @Column(name = "news_id", updatable = false, nullable = false)
    private UUID newsId;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant processedAt;
}
