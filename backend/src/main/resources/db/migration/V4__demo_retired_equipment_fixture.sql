UPDATE devices d
SET lifecycle_status = 'RETIRED',
    retired_at = COALESCE(d.retired_at, now() - interval '1 day'),
    retirement_reason = COALESCE(d.retirement_reason, 'Demo retired equipment')
FROM integration_accounts ia
JOIN tenants t ON t.id = ia.tenant_id
WHERE d.integration_account_id = ia.id
  AND t.name = 'Demo Solar Operator'
  AND ia.mode = 'DEMO'
  AND d.serial_number = 'SIGENERGY-DEMO-SN-10'
  AND d.lifecycle_status <> 'RETIRED';
