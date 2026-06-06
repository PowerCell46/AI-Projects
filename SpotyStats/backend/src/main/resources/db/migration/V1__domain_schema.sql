-- Core domain schema for SpotyStats.
-- Catalog tables (artist/album/track) use the Spotify ID as the natural primary key
-- so that syncing can upsert by ID without surrogate-key lookups.

create table app_user (
    spotify_user_id varchar(64) primary key,
    display_name    varchar(255),
    email           varchar(320),
    image_url       varchar(1024),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create table artist (
    spotify_id varchar(64) primary key,
    name       varchar(512) not null
);

create table album (
    spotify_id varchar(64) primary key,
    name       varchar(512) not null,
    cover_url  varchar(1024)
);

create table track (
    spotify_id        varchar(64) primary key,
    name              varchar(512) not null,
    duration_ms       integer      not null,
    popularity        integer,
    album_id          varchar(64) references album(spotify_id),
    primary_artist_id varchar(64) references artist(spotify_id)
);

-- All credited artists for a track, ordered by position. Charts attribute a Play
-- only to the Primary Artist (track.primary_artist_id); this table powers display
-- of the full "artist(s)" list on history cards.
create table track_artist (
    track_id  varchar(64) not null references track(spotify_id) on delete cascade,
    artist_id varchar(64) not null references artist(spotify_id),
    position  integer     not null,
    primary key (track_id, artist_id)
);

-- A single listening event. De-duplicated on (user_id, played_at): a user cannot
-- play two things at the same instant, so this is a robust natural key for sync.
create table play (
    id        bigint generated always as identity primary key,
    user_id   varchar(64) not null references app_user(spotify_user_id) on delete cascade,
    track_id  varchar(64) not null references track(spotify_id),
    played_at timestamptz not null,
    constraint uq_play_user_played_at unique (user_id, played_at)
);

create index ix_play_user_played_at on play (user_id, played_at desc);

-- Server-side OAuth token store. The refresh token (a long-lived secret) is stored
-- ENCRYPTED at rest; the short-lived access token is stored as-is. Managed by
-- EncryptedJdbcOAuth2AuthorizedClientService, not by JPA.
create table spotify_authorized_client (
    client_registration_id  varchar(100) not null,
    principal_name          varchar(200) not null,
    access_token_type       varchar(50)  not null,
    access_token_value      text         not null,
    access_token_issued_at  timestamptz,
    access_token_expires_at timestamptz,
    access_token_scopes     varchar(1000),
    refresh_token_value     text,
    refresh_token_issued_at timestamptz,
    created_at              timestamptz  not null default now(),
    primary key (client_registration_id, principal_name)
);
