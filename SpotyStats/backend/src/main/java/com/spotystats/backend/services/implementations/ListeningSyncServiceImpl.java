package com.spotystats.backend.services.implementations;

import com.spotystats.backend.dtos.spotify.SpotifyAlbum;
import com.spotystats.backend.dtos.spotify.SpotifyArtist;
import com.spotystats.backend.dtos.spotify.SpotifyRecentlyPlayed;
import com.spotystats.backend.dtos.spotify.SpotifyRecentlyPlayed.PlayHistoryItem;
import com.spotystats.backend.dtos.spotify.SpotifyTrack;
import com.spotystats.backend.clients.SpotifyApiClient;
import com.spotystats.backend.entities.Album;
import com.spotystats.backend.entities.Artist;
import com.spotystats.backend.entities.Track;
import com.spotystats.backend.entities.TrackArtist;
import com.spotystats.backend.entities.TrackArtistId;
import com.spotystats.backend.repositories.AlbumRepository;
import com.spotystats.backend.repositories.ArtistRepository;
import com.spotystats.backend.repositories.PlayRepository;
import com.spotystats.backend.repositories.TrackArtistRepository;
import com.spotystats.backend.repositories.TrackRepository;
import com.spotystats.backend.services.interfaces.ListeningSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;


/**
 * Pulls the user's recently played tracks from Spotify and persists them.
 * Catalog rows (artist/album/track) are upserted by Spotify id; plays are
 * de-duplicated on (userId, playedAt), so re-syncing is idempotent.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListeningSyncServiceImpl implements ListeningSyncService {

    private static final String RECENTLY_PLAYED_PATH = "/me/player/recently-played?limit=50";

    private final SpotifyApiClient spotifyApiClient;

    private final ArtistRepository artistRepository;

    private final AlbumRepository albumRepository;

    private final TrackRepository trackRepository;

    private final TrackArtistRepository trackArtistRepository;

    private final PlayRepository playRepository;

    @Override
    @Transactional
    public void syncRecentlyPlayed(String userId) {
        final SpotifyRecentlyPlayed recentlyPlayed = spotifyApiClient
                .get(RECENTLY_PLAYED_PATH, SpotifyRecentlyPlayed.class);

        if (recentlyPlayed == null || recentlyPlayed.getItems() == null) {
            log.warn("Spotify returned no recently played payload for user {}", userId);
            return;
        }

        recentlyPlayed
                .getItems()
                .forEach(item -> recordPlay(userId, item));
    }

    private void recordPlay(String userId, PlayHistoryItem item) {
        if (item.getTrack() == null || item.getTrack().getId() == null || item.getPlayedAt() == null) {
            return; // e.g. local files, which have no Spotify catalog id
        }

        final Instant playedAt = Instant.parse(item.getPlayedAt());

        if (playRepository.existsByUserIdAndPlayedAt(userId, playedAt)) {
            return;
        }

        final Track track = upsertTrack(item.getTrack());
        playRepository.insertIfAbsent(userId, track.getSpotifyId(), playedAt);
    }

    private Track upsertTrack(SpotifyTrack trackDto) {
        final Track track = trackRepository
                .findById(trackDto.getId())
                .orElseGet(() -> new Track(trackDto.getId()));

        track.setName(trackDto.getName());
        track.setDurationMs(trackDto.getDurationMs());
        track.setPopularity(trackDto.getPopularity());
        track.setAlbum(upsertAlbum(trackDto.getAlbum()));
        track.setPrimaryArtist(primaryArtistOf(trackDto));

        final Track savedTrack = trackRepository.save(track);
        upsertCredits(savedTrack, trackDto.getArtists());

        return savedTrack;
    }

    private Artist primaryArtistOf(SpotifyTrack trackDto) {
        final List<SpotifyArtist> artists = trackDto.getArtists();

        if (artists == null || artists.isEmpty() || artists.getFirst().getId() == null) {
            return null;
        }

        return upsertArtist(artists.getFirst());
    }

    private Artist upsertArtist(SpotifyArtist artistDto) {
        final Artist artist = artistRepository
                .findById(artistDto.getId())
                .orElseGet(() -> new Artist(artistDto.getId()));

        artist.setName(artistDto.getName());

        return artistRepository.save(artist);
    }

    private Album upsertAlbum(SpotifyAlbum albumDto) {
        if (albumDto == null || albumDto.getId() == null) {
            return null;
        }

        final Album album = albumRepository
                .findById(albumDto.getId())
                .orElseGet(() -> new Album(albumDto.getId()));

        album.setName(albumDto.getName());
        album.setCoverUrl(firstImageUrl(albumDto));

        return albumRepository.save(album);
    }

    private void upsertCredits(Track track, List<SpotifyArtist> artistDtos) {
        if (artistDtos == null) {
            return;
        }

        for (int position = 0; position < artistDtos.size(); position++) {
            final SpotifyArtist artistDto = artistDtos.get(position);

            if (artistDto.getId() == null) {
                continue;
            }

            final Artist artist = upsertArtist(artistDto);
            final TrackArtistId creditId = new TrackArtistId(track.getSpotifyId(), artist.getSpotifyId());

            final TrackArtist credit = trackArtistRepository
                    .findById(creditId)
                    .orElseGet(() -> new TrackArtist(track, artist));

            credit.setPosition(position);
            trackArtistRepository.save(credit);
        }
    }

    private static String firstImageUrl(SpotifyAlbum albumDto) {
        if (albumDto.getImages() == null || albumDto.getImages().isEmpty()) {
            return null;
        }

        return albumDto.getImages().getFirst().getUrl();
    }
}
