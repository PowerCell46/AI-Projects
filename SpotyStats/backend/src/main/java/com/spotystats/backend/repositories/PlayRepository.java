package com.spotystats.backend.repositories;

import com.spotystats.backend.entities.Play;
import com.spotystats.backend.repositories.projections.ArtistRankView;
import com.spotystats.backend.repositories.projections.ArtistShareView;
import com.spotystats.backend.repositories.projections.HourlyActivityView;
import com.spotystats.backend.repositories.projections.TopTrackView;
import com.spotystats.backend.repositories.projections.WeekdayActivityView;
import com.spotystats.backend.repositories.projections.WeeklyTrendView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;


public interface PlayRepository extends JpaRepository<Play, Long> {

    boolean existsByUserIdAndPlayedAt(String userId, Instant playedAt);

    /**
     * Records a play, silently skipping it when one already exists for
     * (userId, playedAt) — concurrent syncs of the same batch must not blow up
     * the transaction with a unique-constraint violation.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into play (user_id, track_id, played_at)
            values (:userId, :trackId, :playedAt)
            on conflict (user_id, played_at) do nothing
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("userId") String userId,
            @Param("trackId") String trackId,
            @Param("playedAt") Instant playedAt);

    /**
     * Plays in [from, to), newest first, with everything the history cards need
     * (track, album art, full artist credits) fetched in one round trip.
     */
    @Query("""
            select p from Play p
            join fetch p.track t
            left join fetch t.album
            left join fetch t.credits c
            left join fetch c.artist
            where p.userId = :userId
              and p.playedAt >= :from
              and p.playedAt < :to
            order by p.playedAt desc
            """)
    List<Play> findHistoryWindow(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select count(p) from Play p
            where p.userId = :userId
              and p.playedAt >= :from
              and p.playedAt < :to
            """)
    long countPlaysInWindow(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select coalesce(sum(t.durationMs), 0) from Play p
            join p.track t
            where p.userId = :userId
              and p.playedAt >= :from
              and p.playedAt < :to
            """)
    long sumListeningTimeMsInWindow(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select count(distinct t.primaryArtist.spotifyId) from Play p
            join p.track t
            where p.userId = :userId
              and p.playedAt >= :from
              and p.playedAt < :to
            """)
    long countUniqueArtistsInWindow(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select count(distinct t.spotifyId) from Play p
            join p.track t
            where p.userId = :userId
              and p.playedAt >= :from
              and p.playedAt < :to
            """)
    long countUniqueTracksInWindow(
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Artists the user played for the first time ever on or after {@code from} —
     * i.e. their earliest play of that artist falls inside the current window.
     */
    @Query(value = """
            select count(*) from (
                select t.primary_artist_id
                from play p
                join track t on t.spotify_id = p.track_id
                where p.user_id = :userId
                  and t.primary_artist_id is not null
                group by t.primary_artist_id
                having min(p.played_at) >= :from
            ) first_listens
            """, nativeQuery = true)
    long countNewArtistsSince(
            @Param("userId") String userId,
            @Param("from") Instant from);

    @Query("""
            select a.name as artistName,
                   count(p) as trackCount,
                   coalesce(sum(t.durationMs), 0) as listeningTimeMs
            from Play p
            join p.track t
            join t.primaryArtist a
            where p.userId = :userId
              and p.playedAt >= :from
            group by a.spotifyId, a.name
            order by count(p) desc
            """)
    List<ArtistShareView> aggregateArtistSharesSince(
            @Param("userId") String userId,
            @Param("from") Instant from);

    @Query("select min(p.playedAt) from Play p where p.userId = :userId")
    Instant findEarliestPlayedAt(@Param("userId") String userId);

    /**
     * Newest play strictly before the given instant — the history pager uses it
     * to jump over play-free gaps instead of serving empty weeks.
     */
    @Query("""
            select max(p.playedAt) from Play p
            where p.userId = :userId
              and p.playedAt < :before
            """)
    Instant findLatestPlayedAtBefore(
            @Param("userId") String userId,
            @Param("before") Instant before);

    @Query("""
            select a.spotifyId as artistId,
                   a.name as artistName,
                   count(p) as playCount,
                   coalesce(sum(t.durationMs), 0) as listeningTimeMs,
                   count(distinct t.spotifyId) as uniqueTracks
            from Play p
            join p.track t
            join t.primaryArtist a
            where p.userId = :userId
              and p.playedAt >= :from
            group by a.spotifyId, a.name
            order by count(p) desc, sum(t.durationMs) desc
            """)
    List<ArtistRankView> rankArtistsSince(
            @Param("userId") String userId,
            @Param("from") Instant from,
            Pageable pageable);

    @Query(value = """
            select cast(extract(hour from p.played_at at time zone :zone) as integer) as hour,
                   count(*) as plays
            from play p
            where p.user_id = :userId
            group by 1
            order by 1
            """, nativeQuery = true)
    List<HourlyActivityView> aggregateHourlyActivity(
            @Param("userId") String userId,
            @Param("zone") String zone);

    @Query(value = """
            select cast(extract(isodow from p.played_at at time zone :zone) as integer) as isoWeekday,
                   count(*) as plays,
                   coalesce(sum(t.duration_ms), 0) as listeningTimeMs
            from play p
            join track t on t.spotify_id = p.track_id
            where p.user_id = :userId
            group by 1
            order by 1
            """, nativeQuery = true)
    List<WeekdayActivityView> aggregateWeekdayActivity(
            @Param("userId") String userId,
            @Param("zone") String zone);

    @Query(value = """
            select cast(date_trunc('week', p.played_at at time zone :zone) as date) as weekStart,
                   count(*) as plays,
                   coalesce(sum(t.duration_ms), 0) as listeningTimeMs
            from play p
            join track t on t.spotify_id = p.track_id
            where p.user_id = :userId
              and p.played_at >= :from
            group by 1
            order by 1
            """, nativeQuery = true)
    List<WeeklyTrendView> aggregateWeeklyTrend(
            @Param("userId") String userId,
            @Param("zone") String zone,
            @Param("from") Instant from);

    @Query("""
            select t.spotifyId as trackId,
                   t.name as title,
                   a.name as artistName,
                   al.coverUrl as albumArtUrl,
                   count(p) as playCount,
                   coalesce(sum(t.durationMs), 0) as listeningTimeMs
            from Play p
            join p.track t
            left join t.primaryArtist a
            left join t.album al
            where p.userId = :userId
            group by t.spotifyId, t.name, a.name, al.coverUrl
            order by count(p) desc, sum(t.durationMs) desc
            """)
    List<TopTrackView> rankTopTracks(
            @Param("userId") String userId,
            Pageable pageable);
}
