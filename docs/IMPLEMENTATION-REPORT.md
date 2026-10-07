# Operator-ready MVP implementation report

Date: 2026-10-07

## Product and operator workflows

The vanilla JavaScript dashboard now provides fleet overview, project workspaces, fleet search/filter/sort, inverter detail, alarms, financial summaries, report previews/CSV export/print and an integration catalogue. Operators can add/edit inverters, save-and-add-another, retire with date/reason/notes, restore, mark maintenance, add project/site/cost records, acknowledge/clear alarms and generate audited reports. Demo fixtures distinguish healthy, offline, faulted, stale and sync-error conditions and include retired equipment.

Project and device actions are recorded in `audit_events`. Retirement is lifecycle-based; telemetry, alarms, costs and the latest retirement details are retained. Costs use `BigDecimal` and PostgreSQL `numeric`; a project currency defaults to ZAR and cannot be changed after costs exist. Cross-currency summaries are not added together.

Savings are estimates based on a fresh daily PV reading and the configured import tariff. Readings older than 15 minutes are excluded from current production and estimated savings. Export revenue, operating costs, tariff escalation and historical cumulative savings are not modelled; simple returns are indicative, not accounting-grade.

## Database and API

Forward-only migrations V2-V5 cover telemetry provenance/freshness and alarm deduplication, projects/costs/lifecycle/audit, a retired demo fixture, and retirement notes. Existing telemetry and alarm history is preserved. New API routes are listed in [REST-API.md](REST-API.md); all object lookup paths use the configured/demo tenant boundary.

## Security and integration status

The API filter requires a deployment-level shared key and configured tenant outside demo mode, uses a constant-time key comparison, and sets security headers. Actuator exposure is limited to health. This is not user identity, role-based access or customer-facing multi-tenant SaaS authentication. Demo mode is unauthenticated and must not be exposed publicly. Existing integration credentials remain in the original `auth_config_json` storage model; use an external trusted network and do not deploy with real secrets until secret management is implemented.

OEM provider classes remain connector boundaries, not live integrations. The demo connector is synthetic. No production provider API or credentials are claimed.

## Performance and remaining risks

The latest telemetry lookup uses PostgreSQL `DISTINCT ON`; cost totals are aggregated by project and relevant indexes are defined. The unused legacy global/N+1 dashboard service was removed. Fleet, alarms and costs still use bounded or unpaginated in-memory lists, the dashboard performs several API requests, and database query plans have not been measured. Provider polling remains a scheduled transaction around connector calls. Add paging, stronger ingestion idempotency, tenant membership/authentication, secret references and provider-specific timeout/retry policies before production SaaS use.

## Verification

- `mvn -o test`: passed, 9 tests, 0 failures/errors/skips.
- `mvn -o package`: passed.
- Dashboard inline JavaScript: `node --check` passed.
- `git diff --check`: passed.
- Fresh PostgreSQL 16 startup applied Flyway V1-V5 and passed Hibernate schema validation.
- API workflow smoke test passed project/site/inverter creation, cost calculation, currency-change rejection, detail lookup, retirement/restoration, report generation and audit history.
- Browser-level UI automation, Docker image build and real OEM integrations were not verified. The pre-existing Compose stack was left untouched; it still uses schema V3 until rebuilt/restarted.
