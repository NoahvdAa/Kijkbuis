CREATE TABLE stops (
    stream_id CHAR(36) PRIMARY KEY NOT NULL,
    reason VARCHAR(16) NOT NULL,
    -- The titles of the programmes the stream was on for, separated by commas
    programmes TEXT NOT NULL
);
