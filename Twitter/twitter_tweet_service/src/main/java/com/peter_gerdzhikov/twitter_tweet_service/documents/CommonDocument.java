package com.peter_gerdzhikov.twitter_tweet_service.documents;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * The id is assigned by the service with {@code UUID.randomUUID()}, and both timestamps are set from the
 * {@code Clock} - Spring Data auditing can't tell a new document from an existing one when the id is assigned.
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
public abstract class CommonDocument {

    @Id
    private UUID id;

    private Instant createdAt;

    private Instant updatedAt;
}
