package com.peter_gerdzhikov.twitter_shared.outbox;

import java.util.List;

/**
 * The pending slice of a service's outbox table, behind whatever store keeps it.
 */
public interface PendingOutboxStore {

    List<OutboxRecord> loadPending(int batchSize);

    void delete(OutboxRecord record);

    void save(OutboxRecord record);
}
