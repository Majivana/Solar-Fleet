# Database schema

Core entities:

- `tenants`: customer/company boundary.
- `sites`: physical locations belonging to a tenant.
- `integration_accounts`: one tenant's connection to an OEM/cloud provider.
- `devices`: inverter/gateway-level inventory normalized across brands.
- `telemetry`: timestamped normalized readings plus raw payload.
- `alarms`: normalized fault/warning events.

Production additions should include:

- users / roles / permissions
- batteries
- meters
- PV strings/MPPTs
- tariff plans
- weather observations
- energy aggregates
- commands + command results
- audit log
- integration authorization sessions
- data retention policies

For high-volume telemetry, migrate to TimescaleDB hypertables or PostgreSQL time partitioning and pre-aggregate 5m/15m/hour/day buckets.
