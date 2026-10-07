CREATE TABLE projects (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    name varchar(200) NOT NULL,
    customer varchar(200),
    location varchar(300),
    status varchar(30) NOT NULL DEFAULT 'OPERATIONAL',
    description varchar(2000),
    start_date date,
    commissioning_date date,
    budget numeric(16,2),
    capacity_kw numeric(12,3),
    import_tariff numeric(12,4),
    export_tariff numeric(12,4),
    currency varchar(3) NOT NULL DEFAULT 'ZAR',
    notes varchar(2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_projects_tenant_name UNIQUE (tenant_id, name),
    CONSTRAINT ck_projects_budget_nonnegative CHECK (budget IS NULL OR budget >= 0),
    CONSTRAINT ck_projects_capacity_nonnegative CHECK (capacity_kw IS NULL OR capacity_kw >= 0),
    CONSTRAINT ck_projects_import_tariff_nonnegative CHECK (import_tariff IS NULL OR import_tariff >= 0),
    CONSTRAINT ck_projects_export_tariff_nonnegative CHECK (export_tariff IS NULL OR export_tariff >= 0)
);
CREATE INDEX idx_projects_tenant_status ON projects (tenant_id, status);

ALTER TABLE sites ADD COLUMN project_id uuid REFERENCES projects(id);
CREATE INDEX idx_sites_project ON sites (project_id);

ALTER TABLE devices ADD COLUMN lifecycle_status varchar(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE devices ADD COLUMN tenant_id uuid REFERENCES tenants(id);
UPDATE devices d SET tenant_id = s.tenant_id FROM sites s WHERE s.id = d.site_id;
ALTER TABLE devices ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE devices ADD COLUMN installed_at date;
ALTER TABLE devices ADD COLUMN warranty_until date;
ALTER TABLE devices ADD COLUMN purchase_cost numeric(14,2);
ALTER TABLE devices ADD COLUMN notes varchar(2000);
ALTER TABLE devices ADD COLUMN retired_at timestamptz;
ALTER TABLE devices ADD COLUMN retirement_reason varchar(500);
CREATE INDEX idx_devices_lifecycle_site ON devices (lifecycle_status, site_id);
CREATE INDEX idx_devices_tenant_lifecycle ON devices (tenant_id, lifecycle_status);
ALTER TABLE devices DROP CONSTRAINT IF EXISTS devices_serial_number_key;
CREATE UNIQUE INDEX uq_devices_tenant_brand_serial ON devices (tenant_id, brand, serial_number);

CREATE TABLE project_costs (
    id uuid PRIMARY KEY,
    project_id uuid NOT NULL REFERENCES projects(id),
    site_id uuid REFERENCES sites(id),
    device_id uuid REFERENCES devices(id),
    category varchar(30) NOT NULL,
    description varchar(500) NOT NULL,
    quantity numeric(12,3) NOT NULL,
    unit_cost numeric(14,2) NOT NULL,
    total numeric(16,2) NOT NULL,
    currency varchar(3) NOT NULL DEFAULT 'ZAR',
    cost_date date NOT NULL,
    supplier varchar(200),
    reference varchar(200),
    notes varchar(2000),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_project_costs_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_project_costs_unit_cost_nonnegative CHECK (unit_cost >= 0),
    CONSTRAINT ck_project_costs_total_nonnegative CHECK (total >= 0)
);
CREATE INDEX idx_project_costs_project_date ON project_costs (project_id, cost_date DESC);
CREATE INDEX idx_project_costs_device ON project_costs (device_id);
CREATE UNIQUE INDEX uq_project_costs_device_purchase ON project_costs (device_id)
    WHERE category = 'INVERTER' AND reference = 'DEVICE_PURCHASE';

ALTER TABLE alarms ADD COLUMN acknowledged_at timestamptz;

CREATE TABLE audit_events (
    id uuid PRIMARY KEY,
    tenant_id uuid NOT NULL REFERENCES tenants(id),
    project_id uuid REFERENCES projects(id),
    device_id uuid REFERENCES devices(id),
    action varchar(50) NOT NULL,
    summary varchar(500) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_tenant_time ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_events_project_time ON audit_events (project_id, occurred_at DESC);

INSERT INTO projects (id, tenant_id, name, customer, location, status, description, budget, capacity_kw, import_tariff, currency)
SELECT gen_random_uuid(), t.id, 'Demo Solar Portfolio', 'Demo Customer', 'Western Cape, South Africa',
       'OPERATIONAL', 'Demonstration project for the existing seeded sites.', 2400000.00, 125.000, 3.2500, 'ZAR'
FROM tenants t
WHERE t.name = 'Demo Solar Operator'
  AND NOT EXISTS (SELECT 1 FROM projects p WHERE p.tenant_id = t.id AND p.name = 'Demo Solar Portfolio');

UPDATE sites s SET project_id = p.id
FROM projects p
WHERE p.tenant_id = (SELECT tenant_id FROM sites WHERE id = s.id)
  AND p.name = 'Demo Solar Portfolio'
  AND s.project_id IS NULL;

INSERT INTO project_costs (id, project_id, category, description, quantity, unit_cost, total, currency, cost_date)
SELECT gen_random_uuid(), p.id, 'INVERTER', 'Demo inverter procurement', 1, 85000.00, 85000.00, p.currency, current_date - 45
FROM projects p
JOIN tenants t ON t.id = p.tenant_id
WHERE t.name = 'Demo Solar Operator' AND p.name = 'Demo Solar Portfolio'
  AND NOT EXISTS (SELECT 1 FROM project_costs c WHERE c.project_id = p.id);

INSERT INTO project_costs (id, project_id, category, description, quantity, unit_cost, total, currency, cost_date)
SELECT gen_random_uuid(), p.id, 'INSTALLATION', 'Demo electrical installation', 1, 42000.00, 42000.00, p.currency, current_date - 30
FROM projects p
JOIN tenants t ON t.id = p.tenant_id
WHERE t.name = 'Demo Solar Operator' AND p.name = 'Demo Solar Portfolio'
  AND EXISTS (SELECT 1 FROM project_costs c WHERE c.project_id = p.id AND c.description = 'Demo inverter procurement')
  AND NOT EXISTS (SELECT 1 FROM project_costs c WHERE c.project_id = p.id AND c.description = 'Demo electrical installation');

INSERT INTO audit_events (id, tenant_id, project_id, action, summary)
SELECT gen_random_uuid(), t.id, p.id, 'PROJECT_CREATED', 'Demo operating project created'
FROM tenants t
JOIN projects p ON p.tenant_id = t.id AND p.name = 'Demo Solar Portfolio'
WHERE t.name = 'Demo Solar Operator'
  AND NOT EXISTS (SELECT 1 FROM audit_events a WHERE a.project_id = p.id);
