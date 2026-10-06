# Production checklist

This MVP is runnable, but the following items are required before exposing it to paying customers:

## OEM integration
- Obtain signed/approved third-party API access for each OEM.
- Implement exact auth/token flows and endpoints from the vendor's current documentation.
- Implement provider-specific rate limits, retries, pagination and backoff.
- Add raw-response schema/version tracking.
- Complete ECT identification from the actual inverter/logger model.

## Security
- Replace demo mode with OIDC/OAuth2 (for example Keycloak or a managed identity provider).
- Enforce tenant_id in every query/service operation; do not trust a client-supplied tenant header.
- Store OEM secrets in AWS Secrets Manager/KMS or a dedicated vault.
- TLS, MFA, audit logging, secret rotation and least privilege.
- Add command approval and safety policies before any remote-control feature.

## Scale
- Redis for caching/token coordination.
- RabbitMQ or Kafka for ingestion queues.
- TimescaleDB hypertables or time partitioning for telemetry.
- 5m/15m/hour/day aggregate tables.
- Per-brand API quotas and circuit breakers.

## Operations
- Metrics for API latency, failures, rate limits, connector health and data freshness.
- Alert if an integration stops syncing.
- Dead-letter queue for malformed vendor payloads.
- Backups, restore tests and data retention policies.
