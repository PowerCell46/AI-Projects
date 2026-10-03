package com.peter_gerdzhikov.twitter_timeline_service.services.interfaces;

public interface FeedRetentionService {

    /**
     * Deletes every entry whose tweet is older than the retention, in batches that each commit on their own,
     * until a batch removes fewer than the batch size. Idempotent, and safe to run while events are consumed.
     * Returns the total number of entries removed.
     */
    int deleteExpiredEntries();
}
