package com.peter_gerdzhikov.twitter_api_gateway.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.peter_gerdzhikov.twitter_api_gateway.entities.enums.OutboxStatus;

/**
 * One Kafka message still owed to a topic. Deliberately has no foreign key to {@code users}, so queued
 * events survive the cleanup of an unconfirmed user.
 */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
        name = "outbox",
        indexes = @Index(name = "ix_outbox_status_created_at", columnList = "status, created_at")
)
public class Outbox extends CommonEntity {

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "message_key", nullable = false)
    private String messageKey;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;
}
