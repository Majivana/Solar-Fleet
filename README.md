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

Demo mode is intentionally unauthenticated and is for local demonstration only (the compose stack binds it to loopback). Do not expose it to the internet. For a non-demo single-tenant deployment, set `OPERATOR_API_KEY` and `TENANT_ID` (demo is off by default); API requests then require `X-Operator-Key`. This is a deployment-level shared key, **not** user identity, role-based access or multi-tenant SaaS authentication. Deploy behind HTTPS and a trusted network boundary. Production multi-user authentication and per-user tenant membership are still required before offering this as a customer-facing SaaS.

## Technology

- Java 21, Spring Boot 4.0.8 (Spring Web, Data JPA, Validation, Actuator), PostgreSQL 16, Flyway
- Maven 3.9 (via the bundled `./mvnw`), JaCoCo coverage, Maven Enforcer
- Vanilla HTML/CSS/JS dashboard served by the application
- Docker Compose stack with an nginx edge proxy and segmented networks

## Quick start

Requirements: **Docker** for the full stack, or **JDK 21** (Maven is downloaded automatically by `./mvnw`).

```bash
# 1. Whole system in containers (proxy + app + PostgreSQL) in demo mode
docker compose up --build            # or: make up
# open http://localhost:8080   (health: the proxy hides /actuator; the app reports it internally)

# 2. Developer loop: PostgreSQL in Docker, app on your machine
make run                             # = docker compose ... up -d db ; SPRING_PROFILES_ACTIVE=demo ./mvnw -pl backend spring-boot:run

# 3. Build and test only
./mvnw verify                        # unit tests + coverage (backend/target/site/jacoco)
IT_DB_URL=jdbc:postgresql://localhost:5432/solarfleet ./mvnw verify   # also runs the PostgreSQL migration test
```

On Windows without `make`/a POSIX shell, run `docker compose up --build`, or use IntelliJ's bundled Maven with the
Spring profile `demo`, or run `mvn wrapper:wrapper` once to generate Apache's official `mvnw.cmd`.

**The application refuses to start unauthenticated unless you opt in.** Use `SPRING_PROFILES_ACTIVE=demo` (or
`APP_DEMO_DATA=true`) for the synthetic demo; otherwise set `OPERATOR_API_KEY` and `TENANT_ID` (see below).

### Production-style run

```bash
cp .env.example .env     # set POSTGRES_PASSWORD, OPERATOR_API_KEY, TENANT_ID (uuidgen), TENANT_NAME
docker compose -f docker-compose.yml -f docker-compose.prod.yml up --build -d     # or: make up-prod
```

On first start the configured tenant and one (credential-less) LIVE integration account per manufacturer are created so
equipment can be onboarded; live polling reports "sync error" until an approved OEM adapter is configured. Put TLS in
front of the proxy (load balancer, Caddy, or certificates mounted into nginx) before exposing it.

## Network design

```text
host --127.0.0.1:8080--> [proxy] --edge_app (internal)--> [app] --data (internal)--> [db]
                                                           [app] --egress--> OEM cloud APIs
```

| Network | Members | Purpose |
|---|---|---|
| `public` | proxy | the only network with a host port (loopback by default; `BIND_ADDRESS`, `HTTP_PORT`) |
| `edge_app` (internal) | proxy, app | proxy-to-app traffic; no outside route |
| `data` (internal) | app, db | database traffic; PostgreSQL has no host port and no internet route |
| `egress` | app | outbound calls to vendor APIs |

- nginx adds rate limiting on `/api/`, a 1 MB body limit, timeouts, hides `/actuator`, and overwrites `X-Forwarded-For`.
- Outbound vendor calls go through `OutboundHttpClient`: bounded connect/read timeouts, redirects never followed, and
  base URLs validated (https only; no loopback/private/link-local/metadata addresses) to prevent SSRF.
- Every request carries an `X-Request-Id` (also in logs and in error bodies).
- `docker-compose.dev.yml` additionally publishes PostgreSQL on `127.0.0.1:5432` for local development only.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `APP_DEMO_DATA` | `false` (compose: `true`) | synthetic data + unauthenticated API; never expose |
| `OPERATOR_API_KEY`, `TENANT_ID`, `TENANT_NAME` | – | required when demo is off |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` | local PostgreSQL / 10 | database |
| `POLLING_ENABLED`, `POLLING_FIXED_DELAY_MS` | `true`, `60000` | run exactly one poller per deployment |
| `TELEMETRY_RETENTION_DAYS` | `90` | nightly purge (newest reading per device kept); `0` disables |
| `OUTBOUND_CONNECT_TIMEOUT_MS`, `OUTBOUND_READ_TIMEOUT_MS`, `OUTBOUND_ALLOW_HTTP`, `OUTBOUND_ALLOW_PRIVATE_NETWORKS` | `5000`, `15000`, `false`, `false` | vendor HTTP policy |
| `FORWARD_HEADERS` | `none` (compose: `framework`) | trust proxy headers only behind a trusted proxy |

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

Audit: see [docs/AUDIT-2026-10-11.md](docs/AUDIT-2026-10-11.md) for findings, fixes and remaining risks.

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
