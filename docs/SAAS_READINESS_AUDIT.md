# Solar Fleet — SaaS readiness and engineering audit

**Audit date:** 11 October 2026  
**Scope:** Supplied repository, static browser client, Spring Boot API, database migrations, Compose configuration and available test artefacts.

## Executive assessment

The repository is a promising operator-focused MVP, not yet a customer-ready, multi-tenant SaaS product. It already includes useful fleet workflows: projects and sites, inverter onboarding, lifecycle controls, alarms, project costs, audit activity, CSV/print reports, synthetic telemetry, PostgreSQL and Flyway migrations. The browser interface has been refined for clearer visual hierarchy, operator efficiency, mobile use and accessibility.

Do not market the current branch as production-ready until the critical controls in the checklist below have been implemented and independently verified. In particular, demo mode is unauthenticated and OEM live adapters are not configured; a green status in the demo tenant is not proof of live telemetry.

## Changes made in this pass

- Refined the browser shell into a higher-contrast energy-operations workspace: forest-green navigation, clearer brand treatment, production-focused overview header, stronger metric hierarchy, more consistent cards/tables/forms, responsive spacing and reduced-motion support.
- Added a keyboard-accessible skip link, made refresh an explicit action, replaced the native browser prompt with an accessible operator-key dialog, and kept automatic refresh from running while the browser tab is hidden.
- Improved operational data presentation: stale or missing device telemetry is counted in the overview's freshness note, invalid timestamps render safely, and refresh errors are de-duplicated instead of stacking on repeated failures.
- Fixed the inverter onboarding entry point so **Add Inverter** actions launched from a project can pass the intended project into the form.
- Hardened CSV exports against spreadsheet formula injection while preserving negative numeric values as numbers, and delayed object-URL revocation to give the browser time to start the download.
- Limited local Compose port publishing to loopback by default and made the database password, demo mode, operator key and tenant ID environment-overridable. Added `.env.example` for local configuration.

## Findings and release gates

| Priority | Finding | Current state | Required before customer production |
|---|---|---|---|
| P0 | Demo mode is unauthenticated. | Explicitly labelled as demo; local Compose ports are loopback-only by default. | Never expose demo mode publicly. Deploy with demo mode disabled and a supported identity boundary. |
| P0 | Shared operator key is not user identity, roles or a multi-tenant login system. | Existing `X-Operator-Key` filter protects non-demo API calls; service lookups use a configured tenant. | Implement OIDC/OAuth2, secure sessions or validated tokens, MFA, per-tenant membership, RBAC and authorization tests for every resource/action. |
| P0 | SaaS isolation has not been validated with automated cross-tenant tests. | Tenant-scoped service methods are present; a shared-key single-tenant deployment is the implemented boundary. | Add API integration tests proving one tenant cannot read or mutate another tenant's projects, devices, alarms, costs, sites, telemetry or reports. |
| P1 | Integration-account schema includes a plaintext-capable `auth_config_json` column. | Live connector boundaries are unconfigured; credential-vault integration is not implemented. | Do not place production credentials in this column. Replace it with secret references and resolve secrets through a managed vault/KMS-backed design; rotate any secrets that may already have been stored. |
| P1 | OEM integrations do not currently deliver verified live data. | Provider adapter boundaries and capability catalogue exist; `liveConfigured` is false. | Obtain official partner access, implement and contract-test each provider's current authentication, pagination, rate limits and telemetry/alarms. Clearly show provider connection state and source time. |
| P1 | Current dataset APIs are bounded inconsistently and do not provide a full pagination contract. | The telemetry detail endpoint is capped at 100 points; other inventory, costs and reporting lists may grow with the tenant. | Add keyset pagination, server-side filtering, query limits, indexes and load tests before large-fleet deployment. |
| P1 | Telemetry is operationally modelled but full history analytics are not implemented. | The MVP uses latest readings/freshness gates and clearly labels demo data; historical aggregation, performance ratio and forecasting are not claimed as working. | Add retention/partitioning, time-series aggregates, measured energy reconciliations and validated KPI definitions. |
| P1 | Browser automation and production deployment verification are incomplete in this environment. | Static JavaScript syntax was checked after UI edits. Maven Wrapper could not download Maven in this execution environment and Docker is not installed here. | Run `./mvnw test`, a clean PostgreSQL/Flyway startup, API security tests, browser E2E tests, accessibility checks and deployment smoke tests in CI. |
| P2 | Operational observability and resilience need end-to-end proof. | Health endpoint, structured operational data, polling timeouts and basic retry behaviour exist. | Add metrics/traces, incident alerts, secret rotation, backup/restore drills, SLOs, rate limiting and tested recovery procedures. |
| P2 | Accessibility and UX should be tested with real operators. | Keyboard skip link, focus styling, responsive design and reduced-motion handling are included. | Add automated accessibility checks and moderated workflows for onboarding, alarm triage, retirement, costs and report exports. |

## Manual test plan

1. Start the app using the README instructions and confirm `/actuator/health` reports `UP`.
2. Confirm the demo banner is visible in demo mode and that the page copy does not label synthetic readings as live.
3. Create a project and site, then use that project's **+ Inverter** action; verify the project/site selection is pre-filled correctly.
4. Add, edit, maintain, retire and restore an inverter; verify the audit trail and historical costs/alarms remain available.
5. Test fleet filters and search with missing/stale timestamps, and confirm the overview notes stale or missing telemetry.
6. Acknowledge and clear alarms; export reports containing quotes, Unicode, negative numeric values, and text beginning with `=`, `+`, `-` or `@`.
7. Repeat at narrow mobile width and keyboard-only, with reduced-motion enabled.
8. For non-demo mode, confirm missing/wrong operator keys fail and cross-tenant resource IDs return 404. This is still a shared-key boundary, not the final SaaS auth model.

## Verification record for this pass

- JavaScript inline source parses successfully with Node.js `new Function(...)` syntax validation.
- Maven tests could not be rerun: the Maven Wrapper download of Maven 3.9.12 failed in the execution environment, and no system Maven executable was available.
- Docker/Compose is not installed in the execution environment; Compose deployment and the live PostgreSQL migration chain were not rerun in this pass.
- The repository includes prior test/migration evidence in `docs/CURRENT-AUDIT.md`; treat that as prior evidence, not a substitute for the blocked verification above.
