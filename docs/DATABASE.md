# Database schema

Core entities:

- `tenants`: customer/company boundary.
- `projects`: tenant-owned operating and financial workspace; a project can group sites.
- `sites`: physical locations belonging to a tenant and optionally associated with a project.
- `integration_accounts`: one tenant's connection to an OEM/cloud provider.
- `devices`: inverter/gateway-level inventory normalized across brands, with a tenant-scoped manufacturer/serial identity and separate operational and lifecycle states.
- `telemetry`: timestamped normalized readings plus raw payload.
- `alarms`: normalized fault/warning events with acknowledgement and clear timestamps.
- `project_costs`: dated, categorized costs with decimal quantity, unit cost and server-calculated total.
- `audit_events`: human-readable project, device, alarm, cost and report actions; no credentials or tokens.

## Forward migration V3

V3 adds projects, optional project assignment on existing sites, device lifecycle and retirement metadata, tenant identity on devices, categorized project costs, alarm acknowledgement and audit history. Existing devices receive their tenant ID from their current site. Existing telemetry and alarms are not deleted. The old global serial uniqueness is replaced with a tenant/manufacturer/serial index. The migration seeds a demo portfolio and sample costs only for the existing demo tenant.

Monetary columns use PostgreSQL `numeric`; cost total is `round(quantity × unit cost, 2)` using Java `BigDecimal` with `HALF_UP`. Project currency defaults to `ZAR` and is stored on costs for future currency extension. No floating-point value is persisted for money.

V5 adds optional retirement notes while preserving all existing device and lifecycle records. Restoring an inverter retains its most recent retirement date, reason and notes for reference; audit events record each retire and restore action.

V6 merges duplicate open alarms that share an inverter, code, and message. It retains the earliest first-seen time and latest last-seen time on the canonical alarm, marks duplicate rows cleared at their last-seen time, and adds a database uniqueness index so concurrent polls cannot recreate duplicates under a different fingerprint.

## Savings basis

The current estimate uses:

`fresh active-device daily PV energy × project import tariff`

Readings older than 15 minutes do not contribute to current fleet output or estimated savings; inverter detail still displays them as last reported values with their source timestamp. The estimate is labelled and exported as estimated, annualized as 365 × the latest fresh daily estimate, and assumes all PV offsets grid imports. It does **not** include export revenue, operating costs, tariff escalation, a historical cumulative series or measured payback. Annual return is estimated annual savings divided by recorded actual project spend; it is unavailable when either input is unavailable/zero. These are indicative MVP figures, not accounting-grade or guaranteed savings.

Financial summaries are kept per project currency; totals across different currency codes are intentionally not combined without an exchange-rate basis.

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
