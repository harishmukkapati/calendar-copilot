-- Version columns for optimistic locking: concurrent updates to the same row fail
-- with a conflict instead of silently overwriting each other.
ALTER TABLE task ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE calendar_event ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
