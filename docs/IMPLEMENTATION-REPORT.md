# Implementation report

Date: 2026-10-06

## Completed in this pass

- Audited the source tree, build configuration, migration, repositories, controllers, entities, polling and connector boundaries; recorded confirmed findings in [CURRENT-AUDIT.md](CURRENT-AUDIT.md).
- Added explicit SQL column names for the canonical telemetry fields and key device/entity fields so those mappings do not depend on an implicit naming convention.
- Added forward-only Flyway migration V2 for ingestion/source timestamps, provider sequence/schema metadata, alarm fingerprints, alarm last-seen tracking, and query indexes. Existing open duplicate alarms are collapsed before a partial unique index is applied.
- Added a deterministic open-alarm fingerprint in polling and refreshes `lastSeenAt` when a still-open alarm reappears.
- Added a telemetry duplicate check for the same device and source timestamp.
- Eagerly loads the device relation needed by alarm DTO mapping, avoiding lazy access after repository transaction scope.
- Exposed `demoMode` in the dashboard summary and show `DEMO DATA` in the page when demo seeding is enabled.
- Added unit contract tests asserting that live provider boundaries do not claim discovery success without an approved implementation and that ECT remains unverified.

## Verification

- Initial `mvn clean test` before adding tests: passed compilation; there were no tests at that point.
- Current `mvn clean test`: could not complete Surefire startup because Maven tried to write provider metadata in the read-only `/home/kwanda/.m2` cache. An isolated temporary Maven cache could not download dependencies because network DNS access to Maven Central is unavailable.
- `mvn -o test-compile`: passed, including compilation of the added test source.
- `mvn -o -DskipTests package`: passed.
- `docker compose up --build -d` and `docker compose build`: blocked because the Docker daemon socket is inaccessible in this environment; Docker buildx is also absent.
- `mvn spring-boot:run`, PostgreSQL migration execution, live health endpoint, and browser dashboard were not verified because no accessible PostgreSQL service or Docker daemon was available.

## Integration status

- **Demo/local:** `DemoBrandConnector` produces local demo readings when demo integrations are polled.
- **Provider boundaries, not live:** LuxPower, SOLARMAN, Sungrow/iSolarCloud, SolisCloud, SolarEdge, Victron/VRM, Fronius, SolaX and Sigenergy live classes explicitly return not-configured results. There are no official live API calls in this repository.
- **ECT:** explicitly unverified; no protocol or API is invented.
- **Credentials:** not yet migrated away from `auth_config_json`; production secret references and a Secrets Manager/Vault resolver remain required.

## Files created

- `docs/CURRENT-AUDIT.md`
- `docs/IMPLEMENTATION-REPORT.md`
- `backend/src/main/resources/db/migration/V2__telemetry_freshness_and_alarm_dedup.sql`
- `backend/src/test/java/za/co/solar/fleet/integration/LiveConnectorBoundaryTest.java`

## Files modified

- `backend/src/main/java/za/co/solar/fleet/domain/Alarm.java`
- `backend/src/main/java/za/co/solar/fleet/domain/Device.java`
- `backend/src/main/java/za/co/solar/fleet/domain/IntegrationAccount.java`
- `backend/src/main/java/za/co/solar/fleet/domain/Site.java`
- `backend/src/main/java/za/co/solar/fleet/domain/TelemetryPoint.java`
- `backend/src/main/java/za/co/solar/fleet/job/PollingService.java`
- `backend/src/main/java/za/co/solar/fleet/repository/AlarmRepository.java`
- `backend/src/main/java/za/co/solar/fleet/repository/TelemetryRepository.java`
- `backend/src/main/java/za/co/solar/fleet/service/DashboardService.java`
- `backend/src/main/resources/static/index.html`

## Known limitations and next steps

This change is a stabilization slice, not a production-ready multi-tenant SaaS implementation. Authentication/JWT, tenant authorization, user/membership/role tables, manufacturer/provider separation, credential vaulting, the full discovery lifecycle, provider-specific HTTP clients, provider retry/rate/circuit policies, scalable partitioned telemetry/aggregations, analytics, structured alarm lifecycle, command authorization/audit, API pagination, REST error standardization and Testcontainers tenant-isolation tests remain unimplemented. The telemetry dedupe check is an application-level check and can race under concurrent ingestion; a database-enforced source identity is still needed. Dashboard summary still performs N+1 telemetry reads. See the audit for severity and affected areas.

Production next steps are to implement tenant security before exposing any API, remove plaintext credential configuration, finish the provider/manufacturer migration, add atomic idempotent ingestion and aggregate queries, then verify Flyway and Hibernate against PostgreSQL and run tenant-isolation integration tests in CI. Do not deploy this build as a customer-facing multi-tenant service.
