ALTER TABLE telemetry ADD COLUMN ingested_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE telemetry ADD COLUMN source_timestamp timestamptz;
ALTER TABLE telemetry ADD COLUMN provider_sequence varchar(200);
ALTER TABLE telemetry ADD COLUMN provider_schema_version varchar(100);
CREATE INDEX idx_telemetry_ingested_at ON telemetry (ingested_at DESC);

ALTER TABLE alarms ADD COLUMN fingerprint varchar(64);
ALTER TABLE alarms ADD COLUMN last_seen_at timestamptz;
UPDATE alarms SET last_seen_at = occurred_at WHERE last_seen_at IS NULL;
UPDATE alarms SET fingerprint = md5(device_id::text || ':' || code || ':' || message)
WHERE fingerprint IS NULL;
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY fingerprint ORDER BY occurred_at DESC, id) AS rn
    FROM alarms WHERE cleared_at IS NULL
)
DELETE FROM alarms a USING ranked r WHERE a.id = r.id AND r.rn > 1;
CREATE UNIQUE INDEX uq_alarms_open_fingerprint ON alarms (fingerprint) WHERE cleared_at IS NULL;
CREATE INDEX idx_alarms_device_open_time ON alarms (device_id, occurred_at DESC) WHERE cleared_at IS NULL;
