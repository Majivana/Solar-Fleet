# Solar Fleet Management SaaS MVP

A multi-tenant, multi-brand solar PV inverter monitoring platform for South African commercial/residential fleets.

## MVP status

This repository is an **operator-focused fleet-management MVP** with a responsive operations dashboard, project and site workspaces, inverter onboarding and lifecycle actions, open-alarm handling, decimal-backed project costs, savings estimates, activity history and CSV/print reports.

The data model retains the existing tenant, site, inverter, telemetry, alarm and integration boundaries. Projects group sites; costs and audit events are additive. PostgreSQL migrations preserve existing telemetry and alarm history.

### Integration limitation

OEM cloud APIs are not universally public. Several providers require application/partner approval and credentials. Therefore this MVP deliberately **does not pretend to have live credentials or undocumented endpoints**. The adapter layer is ready for official credentials/endpoints; demo fixtures keep the platform fully runnable before OEM onboarding is completed.

ECT is currently represented as `ECT_UNVERIFIED` because the exact manufacturer/model/logger/API could not be reliably identified. The adapter boundary is included so a verified protocol can be added without changing the core platform.

The local demo connectors are synthetic fixtures and are labelled as demo data in the UI. Live integrations remain unconfigured; no production telemetry should be inferred from the demo readings.

## Operator workflows

- Add or edit an inverter from the fleet or project workspace. Duplicate manufacturer/serial pairs are rejected within the selected tenant.
- Retire equipment without deleting it; telemetry, alarms and costs remain available. Retirement reason, date and notes are kept as device history and each retirement/restoration is audited. Retired equipment can be restored, or moved into and out of maintenance.
- Add project sites and categorized costs; each cost total is calculated from quantity × unit cost and rounded to two decimal places. An inverter purchase cost entered during onboarding is also added to the project cost ledger.
- Savings are explicitly estimates: latest daily PV energy × configured import tariff, assuming all PV offsets grid imports. Export revenue, operating costs and historical cumulative savings are excluded.
- Project budgets and costs default to ZAR but accept another three-letter currency code. A project's currency is locked after its first cost so ledger totals cannot mix currencies.
- Acknowledge/clear open alarms, view recent history, and export fleet, alarms, project performance, cost or savings data as CSV or print.

## Security boundary

Demo mode is intentionally unauthenticated and is for local demonstration only. Do not expose it to the internet. For a non-demo single-tenant deployment, set `APP_DEMO_DATA=false`, `OPERATOR_API_KEY` and `TENANT_ID`; API requests then require `X-Operator-Key`. This is a deployment-level shared key, **not** user identity, role-based access or multi-tenant SaaS authentication. Deploy behind HTTPS and a trusted network boundary. Production multi-user authentication and per-user tenant membership are still required before offering this as a customer-facing SaaS.

## Technology

- Java 21
- Spring Boot 4.0.8
- Spring Web
- Spring Data JPA
- PostgreSQL
- Flyway
- Jackson
- Actuator
- Vanilla HTML/CSS/JS dashboard for the first runnable UI

## Run locally

### Option A: Docker Compose

```bash
docker compose up --build
```

Open:

- Dashboard: http://localhost:8080
- Health: http://localhost:8080/actuator/health
- API: http://localhost:8080/api/v1/dashboard/summary

### Option B: Maven

Requirements: Java 21+, Maven 3.9+

```bash
cd backend
mvn spring-boot:run
```

Set PostgreSQL variables as needed:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/solarfleet
export DB_USERNAME=solarfleet
export DB_PASSWORD=solarfleet
```

## Demo tenant

The application seeds one demo tenant, four sites and one demo device per requested brand when `APP_DEMO_DATA=true`.

Brands represented:

- LuxPower
- ECT (unverified adapter boundary)
- SOLARMAN
- Sungrow
- Solis
- SolarEdge
- Victron
- Fronius
- SolaX
- Sigenergy

## Production roadmap

1. Obtain OEM developer/partner credentials and customer authorization flows.
2. Replace each demo connector with the official vendor adapter.
3. Add Keycloak/OIDC + MFA and enforce tenant-scoped authorization in every service/query.
4. Add Redis and RabbitMQ/Kafka for high-volume ingestion.
5. Move telemetry to TimescaleDB/partitioned PostgreSQL.
6. Add weather/irradiance and tariff datasets for PR, anomaly and financial analytics.
7. Add remote-control commands only after vendor capability validation and safety/approval workflows.

Current route behavior and the exact security/reporting boundary are documented in [REST-API.md](docs/REST-API.md). The project financial formula is described in [DATABASE.md](docs/DATABASE.md).

## Current OEM verification notes

The integration matrix and OEM live-integration specification were reviewed against current public vendor documentation on 2026-10-05. Public documentation confirms official developer/API programmes for SOLARMAN, Sungrow, SolisCloud, SolarEdge, Victron, Fronius, SolaX and Sigenergy. LuxPower's public material confirms monitoring/control capabilities, but a public commercial third-party cloud API specification was not identified; request official integration documentation directly. ECT remains unverified.
