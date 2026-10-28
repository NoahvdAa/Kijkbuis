CREATE TABLE users (
    id CHAR(36) PRIMARY KEY NOT NULL,
    oidc_issuer TEXT NOT NULL,
    oidc_subject TEXT NOT NULL,
    username VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at INTEGER NOT NULL,
    last_login_at INTEGER NOT NULL,
    UNIQUE (oidc_issuer, oidc_subject)
);

CREATE TABLE user_groups (
    user_id CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (user_id, group_name)
);

CREATE TABLE sessions (
    token_hash CHAR(64) PRIMARY KEY NOT NULL,
    user_id CHAR(36) NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    expires_at INTEGER NOT NULL
);

CREATE INDEX sessions_user_id ON sessions (user_id);

CREATE TABLE playlists (
    id CHAR(36) PRIMARY KEY NOT NULL,
    name VARCHAR(255) NOT NULL,
    url TEXT NOT NULL,
    epg_url TEXT NULL,
    detected_epg_url TEXT NULL,
    refresh_interval_minutes INTEGER NOT NULL,
    last_refresh_attempt_at INTEGER NULL,
    last_refreshed_at INTEGER NULL,
    last_refresh_error TEXT NULL,
    epg_generation INTEGER NULL,
    -- Used to only download the EPG again when it changed. The cache key covers the URL and the EPG channels in use.
    epg_etag TEXT NULL,
    epg_last_modified TEXT NULL,
    epg_cache_key TEXT NULL,
    epg_last_refresh_attempt_at INTEGER NULL,
    epg_last_refreshed_at INTEGER NULL,
    epg_last_refresh_error TEXT NULL,
    created_at INTEGER NOT NULL
);

CREATE TABLE channels (
    id CHAR(36) PRIMARY KEY NOT NULL,
    playlist_id CHAR(36) NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    identity TEXT NOT NULL,
    position INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    group_title VARCHAR(255) NULL,
    logo_url TEXT NULL,
    stream_url TEXT NOT NULL,
    tvg_id VARCHAR(255) NULL,
    epg_channel_override TEXT NULL,
    channel_number INTEGER NULL,
    first_seen_at INTEGER NOT NULL,
    UNIQUE (playlist_id, identity)
);

-- EPG data is imported into a new generation, which replaces the playlist's current epg_generation once complete.
-- That way imports don't need one long transaction, and a failed import leaves the old data in place.
CREATE TABLE epg_channels (
    playlist_id CHAR(36) NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    id TEXT NOT NULL,
    display_name TEXT NULL,
    icon_url TEXT NULL,
    PRIMARY KEY (playlist_id, generation, id)
);

CREATE TABLE programmes (
    playlist_id CHAR(36) NOT NULL REFERENCES playlists(id) ON DELETE CASCADE,
    generation INTEGER NOT NULL,
    epg_channel_id TEXT NOT NULL,
    starts_at INTEGER NOT NULL,
    ends_at INTEGER NULL,
    title TEXT NOT NULL,
    subtitle TEXT NULL,
    description TEXT NULL,
    category TEXT NULL,
    icon_url TEXT NULL,
    poster_url TEXT NULL,
    backdrop_url TEXT NULL,
    UNIQUE (playlist_id, generation, epg_channel_id, starts_at)
);

-- Only a few hours of past programmes are kept, so searching by start time finds a time window without scanning the
-- days of future programmes
CREATE INDEX programmes_starts_at ON programmes (playlist_id, generation, starts_at);
