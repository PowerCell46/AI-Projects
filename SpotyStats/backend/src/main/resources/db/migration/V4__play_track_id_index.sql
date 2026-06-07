-- The history and aggregate queries (e.g. PlayRepository.sumListeningTimeMsInWindow)
-- join/filter play on track_id, which the foreign key alone does not index in Postgres.
create index ix_play_track_id on play (track_id);
