WITH ranked AS (
    SELECT id, device_id, code, message, occurred_at, last_seen_at,
           row_number() OVER (
               PARTITION BY device_id, code, message
               ORDER BY occurred_at, id
           ) AS position,
           min(occurred_at) OVER (PARTITION BY device_id, code, message) AS first_seen_at,
           max(last_seen_at) OVER (PARTITION BY device_id, code, message) AS most_recent_seen_at
    FROM alarms
    WHERE cleared_at IS NULL
),
canonical AS (
    SELECT id, first_seen_at, most_recent_seen_at
    FROM ranked
    WHERE position = 1
)
UPDATE alarms a
SET occurred_at = c.first_seen_at,
    last_seen_at = COALESCE(c.most_recent_seen_at, c.first_seen_at)
FROM canonical c
WHERE a.id = c.id;

WITH ranked AS (
    SELECT id, occurred_at, last_seen_at,
           row_number() OVER (
               PARTITION BY device_id, code, message
               ORDER BY occurred_at, id
           ) AS position
    FROM alarms
    WHERE cleared_at IS NULL
)
UPDATE alarms a
SET cleared_at = COALESCE(r.last_seen_at, r.occurred_at)
FROM ranked r
WHERE a.id = r.id AND r.position > 1;

UPDATE alarms
SET fingerprint = md5(device_id::text || ':' || code || ':' || message)
WHERE cleared_at IS NULL;

CREATE UNIQUE INDEX uq_alarms_open_natural_identity
    ON alarms (device_id, code, md5(message))
    WHERE cleared_at IS NULL;
