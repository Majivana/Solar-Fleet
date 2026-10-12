-- Telemetry identity: one reading per device per source timestamp. Existing duplicates are removed first
-- (the earliest-ingested row is kept) so the unique index can be built on databases that already ran V1-V6.
DELETE FROM telemetry t
USING (
    SELECT id,
           row_number() OVER (PARTITION BY device_id, timestamp ORDER BY ingested_at, id) AS rn
    FROM telemetry
) d
WHERE t.id = d.id AND d.rn > 1;

CREATE UNIQUE INDEX uq_telemetry_device_timestamp ON telemetry (device_id, timestamp);
-- Superseded: the unique index above serves the same (device_id, timestamp) lookups in both directions.
DROP INDEX IF EXISTS idx_telemetry_device_time;
-- Supports the retention job's cutoff scan.
CREATE INDEX idx_telemetry_timestamp ON telemetry (timestamp);
-- Supports alarm listings per device (all states, not just open).
CREATE INDEX idx_alarms_device_time ON alarms (device_id, occurred_at DESC);
