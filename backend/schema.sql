-- TKD PRO backend schema (PostgreSQL 14+).
-- The API also creates this table on startup; this file documents it and seeds fresh Docker volumes.
-- Records are stored as JSONB copies of the app's Room rows (see app/src/main/java/com/tkdpro/core/database/entity).

CREATE TABLE IF NOT EXISTS sync_records (
    id                BIGSERIAL PRIMARY KEY,
    entity            VARCHAR(64)  NOT NULL,
    entity_id         BIGINT       NOT NULL,
    payload           JSONB        NOT NULL DEFAULT '{}'::jsonb,
    deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    client_updated_at BIGINT       NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_entity UNIQUE (entity, entity_id)
);

CREATE INDEX IF NOT EXISTS ix_sync_entity_updated ON sync_records (entity, client_updated_at);
