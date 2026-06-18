-- PYR1-1513: epoch ms of last logout; sessions created at/before are rejected (server-side invalidation).
ALTER TABLE users ADD session_invalidated_at bigint NOT NULL DEFAULT 0;
