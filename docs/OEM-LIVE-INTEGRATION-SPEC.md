# OEM live integration specification (2026-10-05)

This document separates what the platform is designed to consume from what must be contractually/technically enabled by each OEM.

## LuxPower

Use the official LuxPower commercial support/developer route to obtain approved API documentation, credentials, account/tenant authorization rules, rate limits, telemetry payloads, alarms and control scope. LuxPower's public monitoring material confirms live monitoring, reports, remote control and alerts, but this repository does not embed an undocumented cloud API.

Required adapter methods:
- authenticate/refresh
- list plants/sites
- list devices
- live telemetry
- historical energy
- alarms
- capability discovery
- optional control commands after approval

A later local-gateway connector can be added for models/protocols where authorized and technically safe.

## ECT

STATUS: UNVERIFIED.

The exact OEM cannot be established from the available public evidence. Do not reverse-engineer a random device or guess an endpoint. Bind the adapter only after confirming the exact inverter model, communications dongle/logger, mobile/web portal and official integration/protocol documentation.

## SOLARMAN

The platform exposes an official OpenAPI. Real-time device data is available through the documented device current-data interface, which returns parameter keys, values, units and names. The normalized adapter should retain the raw parameter map because vendor point keys vary by device. Use the provider's documented access-token and tenant/device authorization flow.

Important adapter behavior:
- dynamic point-map ingestion
- point-key mapping table
- device/plant discovery
- real-time data
- history
- alarms
- vendor-specific rate limiting
- optional control only where authorized

## Sungrow / iSolarCloud

Use the Sungrow Developer Portal. Production onboarding is application based and the platform documents monitoring, device/plant statistics, live data via MQTT, configuration and grid control. Third-party applications receive developer credentials such as an AppKey after approval.

Prefer event/live ingestion where the approved package supports it; fall back to rate-limited REST monitoring for lower-frequency views.

## SolisCloud

SolisCloud currently supports user-level HMAC-SHA1 authorization and OAuth2.0 for third-party monitoring platforms. The developer platform documents data access, device control, plant management and real-time forwarding through RocketMQ, MQTT or HTTP callback.

For this SaaS, prefer OAuth2 for customer authorization and real-time forwarding for high-frequency telemetry. Apply the documented provider limits to REST polling.

## SolarEdge

Use SolarEdge API V2. OAuth2 is the application authorization mechanism for fleet data access. Model sites, inventory, live power, energy and equipment telemetry as normalized resources.

## Victron / VRM

Use VRM API v2. The current documentation recommends access tokens for third-party API usage; the older Bearer-token approach is deprecated from 2026-06-01. Store the access token in the secrets manager and never in source control or telemetry.

## Fronius

For local installations, use the documented Solar API JSON interface or Modbus where appropriate. The Fronius Datamanager/inverter can expose current power, voltage, current and connected component data over the local network. For cloud/historical services, Fronius Solar.web Query API is a separate business-oriented API and can be contracted for live use.

## SolaX

SolaXCloud's third-party ecosystem provides real-time, historical and warning data for authorized inverters. Current documentation uses token-based access and documents real-time data endpoints. The adapter should support provider-specific request frequency limits and dynamic point mapping.

## Sigenergy / SigenCloud

Use SigenCloud's Developer Portal. The current developer onboarding requires account/application approval and generates an AppKey. The platform is designed for SaaS/VPP/energy integrations and supports telemetry/dispatch integrations.

Prefer the provider's telemetry push mechanism rather than aggressive polling where available.

## Common connector contract

Every live adapter must implement:

```text
connect(account)
disconnect(account)
health(account)
discoverSites(account)
discoverDevices(account)
readTelemetry(account, device)
readHistory(account, device, from, to)
readAlarms(account, device, from, to)
getCapabilities(account, device)
```

Optional:

```text
subscribeRealtime(account)
executeCommand(account, device, command)
```

## Non-functional requirements

- 30-60 second default fleet refresh target, configurable per provider/device class.
- Provider-specific rate limiter.
- Exponential backoff and circuit breaker.
- Idempotent telemetry writes using `(device_id, timestamp, source_sequence)` where source sequence exists.
- Raw payload retention with a bounded retention policy.
- UTC persistence; site-local timezone only for presentation/reporting.
- Data freshness tracking.
- Per-integration error state.
- Dead-letter handling for malformed payloads.
- Full audit records for every control command.
