-- Persists every /api/ask question+answer per workspace so the frontend can show a durable
-- history that survives logout/login (client-side-only state would vanish on token expiry).
-- Cleared only via the explicit "clear history" endpoint, never automatically.

CREATE TABLE ask_history (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id),
    asked_by     UUID REFERENCES users (id),
    question     TEXT NOT NULL,
    answer       TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_ask_history_workspace ON ask_history (workspace_id);
