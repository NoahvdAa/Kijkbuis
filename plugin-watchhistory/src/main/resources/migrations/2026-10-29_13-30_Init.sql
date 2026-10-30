CREATE TABLE playback_sessions (
	id CHAR(36) PRIMARY KEY NOT NULL,
	user_id CHAR(36) NOT NULL,
	channel_id CHAR(36) NOT NULL,
	channel_name VARCHAR(255) NOT NULL,
	started_at INTEGER NOT NULL,
	ended_at INTEGER NOT NULL,
	flags INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_playback_sessions_user_id_started_at ON playback_sessions(user_id, started_at);

CREATE TABLE playback_session_programmes (
	playback_session_id CHAR(36) NOT NULL REFERENCES playback_sessions(id) ON DELETE CASCADE,
	started_at INTEGER NOT NULL,
	ended_at INTEGER NOT NULL,
	name VARCHAR(255) NOT NULL,
	description TEXT NULL,
	PRIMARY KEY (playback_session_id, started_at)
);
