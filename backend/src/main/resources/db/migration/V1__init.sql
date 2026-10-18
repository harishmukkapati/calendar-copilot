-- A workspace is the ownership boundary for all data. Guests get an anonymous
-- workspace; owner_id is filled in once sign-in (Cognito) is added.
CREATE TABLE workspace (
    id           UUID PRIMARY KEY,
    owner_id     VARCHAR(128),
    time_zone    VARCHAR(64)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    last_seen_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_workspace_last_seen ON workspace (last_seen_at);

-- Tasks form a tree through parent_id (adjacency list). Deleting a task
-- deletes its whole subtree.
CREATE TABLE task (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    parent_id    BIGINT       REFERENCES task (id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    status       VARCHAR(16)  NOT NULL,
    origin       VARCHAR(16)  NOT NULL,
    start_at     TIMESTAMPTZ,
    due_at       TIMESTAMPTZ,
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_task_workspace ON task (workspace_id);
CREATE INDEX idx_task_parent ON task (parent_id);

CREATE TABLE calendar_event (
    id           BIGSERIAL PRIMARY KEY,
    workspace_id UUID         NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    start_at     TIMESTAMPTZ  NOT NULL,
    end_at       TIMESTAMPTZ  NOT NULL,
    color        VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT event_end_after_start CHECK (end_at >= start_at)
);
CREATE INDEX idx_event_workspace ON calendar_event (workspace_id);
