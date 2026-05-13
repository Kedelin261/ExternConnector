-- =====================================================
-- ExternConnector Linear-ClickUp Sync
-- V1: Initial Schema
-- =====================================================

-- Task Mappings: bidirectional link between Linear and ClickUp tasks
CREATE TABLE task_mappings (
    id                  BIGSERIAL PRIMARY KEY,
    linear_issue_id     VARCHAR(255) NOT NULL,
    linear_team_id      VARCHAR(255) NOT NULL,
    clickup_task_id     VARCHAR(255) NOT NULL,
    clickup_list_id     VARCHAR(255) NOT NULL,
    linear_status       VARCHAR(255),
    clickup_status      VARCHAR(255),
    sync_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_linear_issue_id  UNIQUE (linear_issue_id),
    CONSTRAINT uq_clickup_task_id  UNIQUE (clickup_task_id)
);

CREATE INDEX idx_task_mappings_linear_issue   ON task_mappings (linear_issue_id);
CREATE INDEX idx_task_mappings_clickup_task   ON task_mappings (clickup_task_id);
CREATE INDEX idx_task_mappings_sync_enabled   ON task_mappings (sync_enabled);

-- Status Mappings: user-defined Linear <-> ClickUp status translation
CREATE TABLE status_mappings (
    id              BIGSERIAL PRIMARY KEY,
    linear_status   VARCHAR(255) NOT NULL,
    clickup_status  VARCHAR(255) NOT NULL,
    direction       VARCHAR(20)  NOT NULL DEFAULT 'BOTH',  -- BOTH | LINEAR_TO_CLICKUP | CLICKUP_TO_LINEAR
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_status_direction UNIQUE (linear_status, clickup_status, direction)
);

-- Seed default status mappings
INSERT INTO status_mappings (linear_status, clickup_status, direction) VALUES
    ('Todo',        'to do',        'BOTH'),
    ('In Progress', 'in progress',  'BOTH'),
    ('In Review',   'review',       'BOTH'),
    ('Done',        'complete',     'BOTH'),
    ('Cancelled',   'cancelled',    'BOTH'),
    ('Backlog',     'open',         'BOTH');

-- Webhook Logs: immutable audit trail of all received webhooks
CREATE TABLE webhook_logs (
    id              BIGSERIAL PRIMARY KEY,
    event_id        VARCHAR(255) NOT NULL,          -- from webhook payload (idempotency key)
    source          VARCHAR(20)  NOT NULL,           -- LINEAR | CLICKUP
    event_type      VARCHAR(255) NOT NULL,
    payload         JSONB        NOT NULL,
    signature_valid BOOLEAN      NOT NULL,
    processed       BOOLEAN      NOT NULL DEFAULT FALSE,
    processing_error TEXT,
    received_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,
    CONSTRAINT uq_webhook_event_id UNIQUE (event_id, source)
);

CREATE INDEX idx_webhook_logs_event_id   ON webhook_logs (event_id);
CREATE INDEX idx_webhook_logs_source     ON webhook_logs (source);
CREATE INDEX idx_webhook_logs_received   ON webhook_logs (received_at);
CREATE INDEX idx_webhook_logs_processed  ON webhook_logs (processed);

-- Sync Events: audit log of all sync actions taken
CREATE TABLE sync_events (
    id                  BIGSERIAL PRIMARY KEY,
    task_mapping_id     BIGINT      REFERENCES task_mappings(id) ON DELETE SET NULL,
    source_platform     VARCHAR(20) NOT NULL,        -- LINEAR | CLICKUP
    target_platform     VARCHAR(20) NOT NULL,
    event_type          VARCHAR(255) NOT NULL,
    source_status       VARCHAR(255),
    target_status       VARCHAR(255),
    status              VARCHAR(20) NOT NULL,         -- SUCCESS | FAILED | SKIPPED | LOOP_PREVENTED
    error_message       TEXT,
    idempotency_key     VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_sync_events_task_mapping   ON sync_events (task_mapping_id);
CREATE INDEX idx_sync_events_source         ON sync_events (source_platform);
CREATE INDEX idx_sync_events_status         ON sync_events (status);
CREATE INDEX idx_sync_events_created        ON sync_events (created_at);

-- Idempotency keys: prevent duplicate processing of retried webhooks
CREATE TABLE idempotency_keys (
    key         VARCHAR(512) PRIMARY KEY,
    source      VARCHAR(20)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);

-- Auto-update updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_task_mappings_updated_at
    BEFORE UPDATE ON task_mappings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_status_mappings_updated_at
    BEFORE UPDATE ON status_mappings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
