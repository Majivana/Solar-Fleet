CREATE TABLE tenants (
    id uuid PRIMARY KEY,
    name varchar(200) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL
);
CREATE TABLE sites (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    name varchar(200) NOT NULL,
    address varchar(500), province varchar(100), municipality varchar(200),
    latitude numeric(10,7), longitude numeric(10,7), timezone varchar(100) NOT NULL,
    rated_pv_kw numeric(12,3), rated_inverter_kw numeric(12,3), created_at timestamptz NOT NULL
);
CREATE INDEX idx_sites_tenant ON sites(tenant_id);

CREATE TABLE integration_accounts (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    brand varchar(50) NOT NULL,
    mode varchar(20) NOT NULL,
    name varchar(200) NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    base_url varchar(500),
    auth_config_json text,
    created_at timestamptz NOT NULL,
    last_sync_at timestamptz,
    last_error text
);
CREATE INDEX idx_integration_accounts_tenant ON integration_accounts(tenant_id);

CREATE TABLE devices (
    id uuid PRIMARY KEY,
    site_id uuid NOT NULL REFERENCES sites(id),
    integration_account_id uuid NOT NULL REFERENCES integration_accounts(id),
    brand varchar(50) NOT NULL,
    model varchar(200) NOT NULL,
    serial_number varchar(200) NOT NULL UNIQUE,
    external_device_id varchar(200),
    firmware_version varchar(200),
    rated_power_kw numeric(12,3),
    status varchar(30) NOT NULL,
    last_seen_at timestamptz,
    created_at timestamptz NOT NULL
);
CREATE INDEX idx_devices_site ON devices(site_id);
CREATE INDEX idx_devices_integration ON devices(integration_account_id);
CREATE INDEX idx_devices_status ON devices(status);

CREATE TABLE telemetry (
    id uuid PRIMARY KEY,
    device_id uuid NOT NULL REFERENCES devices(id),
    timestamp timestamptz NOT NULL,
    pv_power_w numeric(15,3), pv_energy_today_kwh numeric(15,3), pv_energy_total_kwh numeric(18,3),
    battery_soc numeric(7,3), battery_voltage numeric(10,3), battery_current numeric(10,3), battery_power_w numeric(15,3), battery_temperature_c numeric(8,3),
    grid_voltage numeric(10,3), grid_frequency_hz numeric(8,3), grid_power_w numeric(15,3), grid_import_w numeric(15,3), grid_export_w numeric(15,3),
    load_power_w numeric(15,3), load_energy_today_kwh numeric(15,3), inverter_temperature_c numeric(8,3), inverter_efficiency_pct numeric(8,3),
    status varchar(30), fault_code varchar(100), warning_code varchar(100), raw_payload text
);
CREATE INDEX idx_telemetry_device_time ON telemetry(device_id, timestamp DESC);

CREATE TABLE alarms (
    id uuid PRIMARY KEY,
    device_id uuid NOT NULL REFERENCES devices(id),
    severity varchar(30) NOT NULL,
    code varchar(100) NOT NULL,
    message varchar(1000) NOT NULL,
    occurred_at timestamptz NOT NULL,
    cleared_at timestamptz
);
CREATE INDEX idx_alarms_open ON alarms(cleared_at, occurred_at DESC);
