# Architecture

## Target production architecture

```text
Browser (React/Next.js)
        |
   API Gateway / Spring Security
        |
   Spring Boot Core API
        |
  +-----+-----+----------+-----------+
  |           |          |           |
Sites/Assets Analytics  Alerts   Command Service
  |           |          |           |
  +-----------+----------+-----------+
              |
       Integration Layer
  LuxPower / ECT / SOLARMAN / Sungrow / Solis /
  SolarEdge / Victron / Fronius / SolaX / Sigenergy
              |
       OEM Cloud / LAN / MQTT / Webhook
              |
     PostgreSQL + TimescaleDB
              |
         Redis / RabbitMQ
```

The core application uses a normalized telemetry contract. Each OEM adapter maps its own vocabulary to the canonical model.

## Data ingestion modes

1. Polling: scheduler -> per-brand rate limiter -> OEM REST API.
2. Push: OEM webhook/MQTT/stream -> ingestion endpoint -> validation -> normalized telemetry.
3. Local: gateway -> LAN/Modbus/JSON/proprietary protocol -> ingestion API.

## Security

- OIDC/OAuth2 + MFA for production user authentication.
- Tenant-scoped authorization at API and database query layers.
- Secrets in AWS Secrets Manager / Vault, never source-controlled.
- TLS everywhere.
- Immutable audit trail for remote commands.
- Separate READ and CONTROL permissions.
- Remote control disabled by default.
