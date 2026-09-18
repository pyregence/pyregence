-- PYR1-1763: one active device per account; tabs in that device share it.
CREATE TABLE active_user_sessions (
    user_rid           INTEGER PRIMARY KEY REFERENCES users(user_uid) ON DELETE CASCADE,
    session_generation UUID NOT NULL UNIQUE,
    device_id          UUID NOT NULL,
    session_epoch      BIGINT NOT NULL DEFAULT 1,
    created_at         BIGINT NOT NULL,
    last_active_at     BIGINT NOT NULL,
    revoked_at         BIGINT
);

CREATE INDEX idx_active_user_sessions_live
    ON active_user_sessions (session_generation)
    WHERE revoked_at IS NULL;
