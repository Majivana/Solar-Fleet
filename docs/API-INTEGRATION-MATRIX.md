# Integration matrix

| Brand | Adapter name | Preferred production path | MVP state |
|---|---|---|---|
| LuxPower | LuxPowerConnector | Official LuxPower commercial/cloud API after OEM onboarding; local protocol can be a gateway fallback | Demo adapter + boundary |
| ECT | EctConnector | Pending exact OEM/model/logger identification | Unverified adapter boundary |
| SOLARMAN | SolarmanConnector | SOLARMAN OpenAPI; OAuth/access-token flow | Demo adapter + boundary |
| Sungrow | SungrowConnector | iSolarCloud Developer Portal; approved AppKey / OAuth flow; live data via supported APIs | Demo adapter + boundary |
| Solis | SolisConnector | SolisCloud Open API; OAuth2 for third-party monitoring; HTTP/MQTT/RocketMQ forwarding where approved | Demo adapter + boundary |
| SolarEdge | SolarEdgeConnector | SolarEdge API V2 + OAuth2 | Demo adapter + boundary |
| Victron | VictronConnector | VRM API v2 using current access-token mechanism | Demo adapter + boundary |
| Fronius | FroniusConnector | Local Solar API JSON / Modbus; Solar.web Query API for cloud/historical services | Demo adapter + boundary |
| SolaX | SolaxConnector | SolaXCloud API | Demo adapter + boundary |
| Sigenergy | SigenergyConnector | SigenCloud Developer API / AppKey; telemetry push | Demo adapter + boundary |

## Why the MVP uses boundaries rather than fake live calls

OEM API access is credentialed, account-scoped and in several cases requires commercial application/approval. This repository intentionally avoids reverse-engineering or embedding undocumented production URLs/credentials.

Each production adapter should contain:

- OAuth/client/token management where applicable
- per-provider rate limiter
- retries with exponential backoff
- pagination
- request correlation IDs
- raw response capture
- schema/version validation
- normalized mapping
- alarm mapping
- capability discovery
- health metrics
