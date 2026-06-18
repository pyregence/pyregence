-- Epoch ms cutoff for server-side session invalidation. Sessions created before it are rejected.
ALTER TABLE users ADD session_invalidated_at bigint NOT NULL DEFAULT 0;
