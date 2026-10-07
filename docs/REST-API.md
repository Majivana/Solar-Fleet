# REST API

All implemented routes are under `/api/v1`. The dashboard calls this same-origin API; CORS is not enabled.

## Demo and deployment access

With `APP_DEMO_DATA=true` (the local default), requests are unauthenticated and backed by synthetic demo data. Do not expose demo mode publicly.

Set `APP_DEMO_DATA=false`, `OPERATOR_API_KEY`, and `TENANT_ID` for a non-demo deployment. API requests must then include `X-Operator-Key: <configured key>`. The application restricts object lookups to that configured tenant. This is a single shared deployment key, not an authenticated user session or a multi-tenant identity/role system. Use HTTPS and a trusted network boundary.

## Fleet and integrations

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/dashboard/summary` | Fleet health, production, attention items and recent activity |
| `GET` | `/integrations/catalog` | Provider capability catalogue; `liveConfigured` remains false for unconfigured live adapters |
| `GET` | `/sites` | Sites for the configured/demo tenant |
| `GET` | `/devices?includeRetired=false` | Fleet inventory; retired devices are excluded by default |
| `POST` | `/devices` | Add an inverter; requires site, manufacturer, model, serial and rated power |
| `GET` | `/devices/{id}` | Inverter details and latest available telemetry |
| `PUT` | `/devices/{id}` | Update inverter configuration |
| `GET` | `/devices/{id}/telemetry` | Up to 100 recent telemetry points, newest first |
| `POST` | `/devices/{id}/retire` | Retire equipment without deleting its history; accepts optional `reason`, `retiredAt` and `notes` |
| `POST` | `/devices/{id}/restore` | Restore retired equipment |
| `POST` | `/devices/{id}/maintenance` | Set `{ "maintenance": true|false }` |

## Projects and sites

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/projects` | Project workspace summaries with fleet and financial rollups |
| `POST` | `/projects` | Create a project |
| `GET` | `/projects/{id}` | Project summary |
| `PUT` | `/projects/{id}` | Update project details, budget and savings assumptions |
| `POST` | `/projects/{id}/sites` | Add a site to a project |
| `GET` | `/activity?projectId={id}` | Recent tenant or project audit activity |

## Costs, alarms and reports

| Method | Route | Purpose |
|---|---|---|
| `GET` | `/costs` | Costs in the current tenant |
| `GET` | `/projects/{id}/costs` | Project cost ledger |
| `POST` | `/projects/{id}/costs` | Add a categorized cost; server calculates the rounded total |
| `GET` | `/alarms?includeCleared=true` | Recent alarm history (up to 100); `false` returns open alarms |
| `POST` | `/alarms/{id}/acknowledge` | Acknowledge an open alarm |
| `POST` | `/alarms/{id}/clear` | Clear an alarm while retaining its history |
| `GET` | `/projects/{id}/report` | Project summary, cost ledger, alarms and activity |
| `POST` | `/projects/{id}/report/generate` | Generate and audit a project report |
| `POST` | `/reports/generate` | Audit report generation using `{ "type": "FLEET|ALARMS|COSTS|SAVINGS|PROJECT", "projectId": "optional UUID" }` |

Request validation failures use HTTP 400; unknown or cross-tenant object IDs return 404; duplicate equipment/project names return 409. Integration error details are sanitized before persistence. The API currently returns recent bounded lists rather than a full pagination contract.

## Not implemented

OAuth/OIDC login, users and roles, OEM authorization flows, vendor webhook ingestion, historical energy aggregation, remote control and live provider clients are not implemented. Do not infer those capabilities from connector interfaces or the integration catalogue.
