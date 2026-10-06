# REST API

## Dashboard

`GET /api/v1/dashboard/summary`

`GET /api/v1/integrations/catalog`

Returns fleet counts and current aggregate power.

## Sites

`GET /api/v1/sites`

## Devices

`GET /api/v1/devices`

`GET /api/v1/devices/{deviceId}/telemetry`

## Alarms

`GET /api/v1/alarms`

## Production endpoints to add next

`POST /api/v1/integrations`

`POST /api/v1/integrations/{id}/authorize`

`POST /api/v1/integrations/{id}/sync`

`POST /api/v1/devices/{id}/commands`

`GET /api/v1/sites/{id}/analytics?from=...&to=...`

`GET /api/v1/sites/{id}/energy/daily`

`GET /api/v1/reports/{id}`

`POST /api/v1/webhooks/{brand}`
