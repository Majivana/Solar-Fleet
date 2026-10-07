# Baseline repository audit

Baseline audit performed 2026-10-06 before the operator-workflow changes. The project root is a Git checkout with an initial commit; deployment migration state is not available locally. Findings below describe that baseline. See the follow-up status at the end for changes and remaining risks verified on 2026-10-07.

## Architecture

- Java 21 / Spring Boot 4.0.8 application in `backend`, using Spring MVC, Spring Data JPA, PostgreSQL, Flyway and Actuator.
- One Flyway migration (`V1__init.sql`) defines tenants, sites, integration accounts, devices, telemetry and alarms.
- JPA entities are field-access entities with public mutable fields. A `CommandLineRunner` seeds a demo tenant, sites, accounts and devices.
- `PollingService` runs one global fixed-delay job. Connector interfaces and per-provider live boundary classes exist; live implementations report unconfigured rather than calling guessed APIs.
- `ApiController` exposes a small `/api/v1` API and DTO records. `DashboardService` computes totals by loading devices and querying telemetry separately per device.
- The browser UI is a single static HTML/JavaScript page served by Spring Boot. Compose runs PostgreSQL and the app.

## Confirmed issues

| Severity | Finding | Proposed fix | Affected files |
|---|---|---|---|
| Critical | No authentication, authorization, or tenant context. Device, telemetry and alarm endpoints return global data. Sites infer tenant from the first row. | Add OIDC/JWT validation, membership and role model, tenant context and tenant-scoped queries; cover with API security tests. | `pom.xml`, security config, domain, repositories, services, controller, migrations |
| Critical | `integration_accounts.auth_config_json` stores arbitrary credentials in plaintext-capable text and APIs/polling have no secret abstraction. | Replace with a secret reference and status metadata; resolve secrets from environment/development or a production vault. | `IntegrationAccount`, migrations, provider clients, docs |
| High | Device `site_id` is non-null, but polling discovery constructs a site-less device and then silently skips import. | Persist discovered-device staging records with nullable assignment and explicit confirmation flow. | `Device`, migration, ingestion/onboarding services, API |
| High | `devices.serial_number` is globally unique, even though serials may overlap between manufacturers. | Replace with a manufacturer-scoped or provider-identifier constraint in a forward migration. | `Device`, migration |
| High | `Brand` represents OEMs and the SOLARMAN integration provider in one enum. | Separate manufacturer and integration-provider enums and fields while preserving compatibility during migration. | `Brand`, `Device`, `IntegrationAccount`, connectors, migration |
| High | Dashboard N+1: all devices are loaded, then up to 100 telemetry rows are fetched per device. Other list APIs are unbounded and tenant-global. | Aggregate/latest-per-device query, pagination and tenant filters. | `DashboardService`, repositories, `ApiController` |
| High | Polling does network calls inside one transaction, shares a single cadence across providers, and records failures using arbitrary exception messages. | Isolate provider jobs, add policy/timeouts/retry/circuit/rate limits and avoid long DB transactions. | `PollingService`, connector framework, configuration |
| High | Every telemetry read is inserted. There is no uniqueness identity or duplicate handling; every alarm poll inserts repeated alarms. | Add telemetry identity and alarm fingerprint uniqueness with idempotent upsert/insert behavior. | `TelemetryPoint`, `Alarm`, migration, ingestion |
| High | No source timestamp vs ingestion timestamp, provider schema/version, vendor dynamic points, or freshness model. Raw payload is plain text. | Add canonical and vendor payload metadata, JSONB-compatible storage and data-age status. | telemetry domain/migration/API/UI |
| Medium (partially fixed) | Migration/JPA column agreement depended on implicit naming for many fields. Explicit SQL names are now present for telemetry metrics and key device fields; other entity attributes still use implicit naming. | Finish explicit names on all persisted properties and validate against PostgreSQL at startup. | domain entities |
| Medium (partially fixed) | `SiteDto.inverterCount` is hard-coded to zero; API errors and validation are inconsistent. Alarm DTO device relation is now eagerly fetched to avoid lazy access after repository scope. | Query counts/DTO projections, use structured Problem Details and request IDs. | controller, repositories, exception handling |
| Medium | The dashboard UI assumes every device is online (green dot), requests telemetry individually, lacks data freshness, errors and stale states. | Consume optimized summary/list data; render actual health, loading/error/empty/stale states. | static frontend, API |
| Medium | Site business metadata is incomplete (tariff, grid/distributor, SSEG/export limit, currency/rates, install/commission dates). | Add optional metadata through additive migrations and site DTOs. | `Site`, migration, API |
| Medium | Documentation describes several APIs and production capabilities that are not implemented; OpenAPI file is not generated from runtime. | Clearly label implemented/mocked/planned and align docs with actual routes. | `docs/*`, OpenAPI configuration |
| Medium | No automated tests, including no migration, repository, REST, connector contract or tenant isolation tests. | Add unit and PostgreSQL integration tests with Testcontainers, plus provider stubs. | `pom.xml`, `src/test` |
| Medium | Docker Compose contains a hard-coded development database password and no explicit app healthcheck or secrets guidance. | Keep local-only credentials clearly scoped; use environment overrides and healthcheck; production secrets external. | `docker-compose.yml`, docs |

## Build/startup evidence

- `mvn clean test` completed successfully on the initial tree, compiling 39 Java sources; **no tests were found or run**.
- `pom.xml` already includes `flyway-core` and `flyway-database-postgresql`, with Spring Boot 4.0.8 dependency management. Spring Boot's Flyway auto-configuration is enabled in `application.yml`.
- Hibernate uses `ddl-auto: validate`. The migration uses explicit snake_case columns, while several entities rely on Hibernate's physical naming strategy. A live PostgreSQL startup was not verified during this audit.
- Current Flyway behavior is automatic at startup when PostgreSQL is reachable. Since the checkout lacks Git history, whether V1 has shipped cannot be established; schema changes should therefore be additive.
- V2 adds telemetry freshness metadata and an open-alarm deduplication index. It has been source-reviewed but could not be executed against PostgreSQL in this environment.

## Priority remediation

1. Make entity-to-column names explicit and verify schema validation against PostgreSQL.
2. Add forward-only migrations for provider/manufacturer separation, staging/discovery, idempotency and metadata.
3. Enforce tenant identity and authorization before expanding the API.
4. Replace credential JSON storage and isolate provider execution from database transactions.
5. Add measured ingestion, aggregation and dashboard queries before claiming scale targets.
6. Add tests and report exact local, mocked, approval-required and unimplemented functionality.

## Follow-up status (2026-10-07)

- Added V2-V5 forward migrations for telemetry provenance/freshness and alarm fingerprints, projects/costs/lifecycle/audit, a retired demo fixture and retirement notes.
- Added operator API-key gating for non-demo deployments, tenant-scoped service lookups, sanitized API errors and security headers. The key is a shared deployment credential, not per-user authentication; this does not provide multi-tenant SaaS identity or roles.
- Added project, fleet, lifecycle, alarm, financial and report workflows in the dashboard. Demo fixtures exercise offline, fault, stale telemetry, integration failure and retirement states.
- Removed the unused global/N+1 dashboard service; the active summary uses a latest-per-device query. Actuator exposure is limited to health.
- Current output and savings exclude telemetry older than 15 minutes. Money uses decimal persistence/calculation and project currency; mixed-currency totals are not combined.
- Nine unit/contract tests and JavaScript syntax validation pass. A fresh PostgreSQL 16 instance applied V1-V5 and passed Hibernate schema validation; API smoke tests covered project/site/inverter/cost, currency lock, detail, retirement/restoration, reports and audit. Browser UI automation and tenant-isolation integration tests remain unverified.
- Remaining risks: existing plaintext-capable integration credential storage, unconfigured OEM live APIs, non-paginated fleet/cost data, polling calls within a transaction, and lack of PostgreSQL-backed migration/tenant tests. Do not expose demo mode publicly or deploy this as customer-facing SaaS.
