-- V2: Replace generic placeholder status mappings with real Linear ↔ ClickUp statuses
-- Linear team: Dev (d968a8cb-719b-402f-8d6b-43eef5ffeb0b)
--   States: Todo, In Progress, In Review, Done, Canceled, Backlog, Duplicate
-- ClickUp list: Epics (901113686429)
--   Statuses: scoping, in design, in review, ready for development,
--             in development, testing, shipped, cancelled

-- ── Remove generic seed mappings from V1 ─────────────────────────────────────
DELETE FROM status_mappings;

-- Reset the sequence so new IDs are clean
ALTER SEQUENCE status_mappings_id_seq RESTART WITH 1;

-- ── Insert real bidirectional mappings ────────────────────────────────────────
-- Table columns: linear_status, clickup_status, direction, created_at, updated_at
-- Valid direction values: BOTH | LINEAR_TO_CLICKUP | CLICKUP_TO_LINEAR

INSERT INTO status_mappings (linear_status, clickup_status, direction, created_at, updated_at)
VALUES

-- Backlog <-> scoping
('Backlog',              'scoping',               'BOTH',               NOW(), NOW()),

-- Todo <-> ready for development
('Todo',                 'ready for development',  'BOTH',               NOW(), NOW()),

-- In Progress <-> in development
('In Progress',          'in development',          'BOTH',               NOW(), NOW()),

-- In Review <-> in review (direct 1:1)
('In Review',            'in review',               'BOTH',               NOW(), NOW()),

-- Done <-> shipped
('Done',                 'shipped',                 'BOTH',               NOW(), NOW()),

-- Canceled <-> cancelled
('Canceled',             'cancelled',               'BOTH',               NOW(), NOW()),

-- Duplicate -> cancelled (Linear-only concept; one-way)
('Duplicate',            'cancelled',               'LINEAR_TO_CLICKUP',  NOW(), NOW()),

-- ClickUp-only statuses -> best Linear equivalent (one-way into Linear)
('Backlog',              'in design',               'CLICKUP_TO_LINEAR',  NOW(), NOW()),
('In Review',            'testing',                 'CLICKUP_TO_LINEAR',  NOW(), NOW());
