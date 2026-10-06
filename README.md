# Solar Fleet Management SaaS MVP

A multi-tenant, multi-brand solar PV inverter monitoring platform for South African commercial/residential fleets.

## MVP status

This repository is a **working monitoring MVP** with:

- Multi-tenant data model
- Sites and inverter/device inventory
- Normalized telemetry model
- Alarm model
- Integration account model
- Polling engine
- REST API
- Demo data for LuxPower and all requested brands
- Generic connector contracts for OEM-specific adapters
- Webhook ingestion boundary for push-capable vendors
- First dashboard served by Spring Boot
- PostgreSQL + Flyway schema
- Docker Compose for local development

### Important integration limitation

OEM cloud APIs are not universally public. Several providers require application/partner approval and credentials. Therefore this MVP deliberately **does not pretend to have live credentials or undocumented endpoints**. The adapter layer is ready for official credentials/endpoints; demo fixtures keep the platform fully runnable before OEM onboarding is completed.

ECT is currently represented as `ECT_UNVERIFIED` because the exact manufacturer/model/logger/API could not be reliably identified. The adapter boundary is included so a verified protocol can be added without changing the core platform.

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

## Current OEM verification notes

The integration matrix and OEM live-integration specification were reviewed against current public vendor documentation on 2026-10-05. Public documentation confirms official developer/API programmes for SOLARMAN, Sungrow, SolisCloud, SolarEdge, Victron, Fronius, SolaX and Sigenergy. LuxPower's public material confirms monitoring/control capabilities, but a public commercial third-party cloud API specification was not identified; request official integration documentation directly. ECT remains unverified.
