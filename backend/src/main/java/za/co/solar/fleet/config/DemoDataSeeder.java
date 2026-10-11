package za.co.solar.fleet.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.integration.ConnectorResult;
import za.co.solar.fleet.integration.DemoBrandConnector;
import za.co.solar.fleet.integration.NormalizedTelemetry;
import za.co.solar.fleet.integration.RemoteAlarm;
import za.co.solar.fleet.integration.RemoteDevice;
import za.co.solar.fleet.repository.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ensures demo mode always has one active inverter for every supported brand,
 * plus fixture states (offline, fault, sync-error, stale, retired) and seed telemetry.
 */
@Configuration
public class DemoDataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEMO_TENANT = "Demo Solar Operator";
    private static final String DEMO_PROJECT = "Demo Solar Portfolio";

    @Bean
    CommandLineRunner seed(TenantRepository tenants, SiteRepository sites,
                           IntegrationAccountRepository accounts, DeviceRepository devices,
                           ProjectRepository projects, ProjectCostRepository costs,
                           AuditEventRepository audit, TelemetryRepository telemetry,
                           AlarmRepository alarms,
                           @Value("${platform.demo-data:true}") boolean enabled) {
        return args -> {
            if (!enabled) {
                return;
            }
            Tenant tenant = tenants.findAll().stream()
                    .filter(t -> DEMO_TENANT.equals(t.name))
                    .findFirst()
                    .orElseGet(() -> {
                        Tenant created = new Tenant();
                        created.name = DEMO_TENANT;
                        return tenants.save(created);
                    });

            Project project = projects.findByTenantIdOrderByName(tenant.id).stream()
                    .filter(p -> DEMO_PROJECT.equals(p.name))
                    .findFirst()
                    .orElseGet(() -> createProject(projects, tenant));

            int brandIndex = 0;
            int createdDevices = 0;
            for (Brand brand : Brand.values()) {
                brandIndex++;
                Site site = ensureSite(sites, tenant, project, brand, brandIndex);
                IntegrationAccount account = ensureAccount(accounts, tenant, brand);
                String serial = brand.name() + "-DEMO-SN-" + brandIndex;
                Device device = devices.findFleetByTenantId(tenant.id).stream()
                        .filter(d -> d.brand == brand && serial.equals(d.serialNumber))
                        .findFirst()
                        .orElse(null);
                if (device == null) {
                    device = createDevice(devices, tenant, site, account, brand, brandIndex, serial, false);
                    createdDevices++;
                } else {
                    device.site = site;
                    device.integrationAccount = account;
                    device.tenant = tenant;
                    if (device.lifecycleStatus == LifecycleStatus.RETIRED && brand != Brand.SIGENERGY) {
                        device.lifecycleStatus = LifecycleStatus.ACTIVE;
                        device.retiredAt = null;
                        device.retirementReason = null;
                        device.retirementNotes = null;
                    }
                    devices.save(device);
                }
                seedTelemetryAndAlarms(telemetry, alarms, devices, accounts, account, device, brandIndex);
            }

            ensureRetiredSigenergyFixture(devices, sites, accounts, telemetry, alarms, tenant, project);
            ensureDemoCosts(costs, project);
            ensureDemoAudit(audit, tenant, project);

            long active = devices.findFleetByTenantId(tenant.id).stream()
                    .filter(d -> d.lifecycleStatus != LifecycleStatus.RETIRED)
                    .count();
            long total = devices.findFleetByTenantId(tenant.id).size();
            log.info("Demo fleet ready: {} brands, {} active inverters, {} total devices ({} newly created)",
                    Brand.values().length, active, total, createdDevices);
        };
    }

    private Project createProject(ProjectRepository projects, Tenant tenant) {
        Project project = new Project();
        project.tenant = tenant;
        project.name = DEMO_PROJECT;
        project.customer = "Demo Customer";
        project.location = "Western Cape, South Africa";
        project.status = ProjectStatus.OPERATIONAL;
        project.description = "Sample operating project with one demo inverter per supported brand.";
        project.budget = new BigDecimal("2400000.00");
        project.capacityKw = new BigDecimal("125.000");
        project.importTariff = new BigDecimal("3.2500");
        project.currency = "ZAR";
        return projects.save(project);
    }

    private Site ensureSite(SiteRepository sites, Tenant tenant, Project project, Brand brand, int index) {
        String name = brand.name() + " Demo Site";
        return sites.findByTenantIdOrderByName(tenant.id).stream()
                .filter(s -> name.equals(s.name))
                .findFirst()
                .orElseGet(() -> {
                    Site site = new Site();
                    site.tenant = tenant;
                    site.project = project;
                    site.name = name;
                    site.address = "South Africa";
                    site.province = "Western Cape";
                    site.municipality = "Demo Municipality";
                    site.latitude = BigDecimal.valueOf(-33.9249 + index * 0.01);
                    site.longitude = BigDecimal.valueOf(18.4241 + index * 0.01);
                    site.ratedPvKw = BigDecimal.valueOf(8 + index);
                    site.ratedInverterKw = BigDecimal.valueOf(5 + index);
                    return sites.save(site);
                });
    }

    private IntegrationAccount ensureAccount(IntegrationAccountRepository accounts, Tenant tenant, Brand brand) {
        return accounts.findFirstByTenantIdAndBrandAndEnabledTrue(tenant.id, brand)
                .orElseGet(() -> {
                    IntegrationAccount account = new IntegrationAccount();
                    account.tenant = tenant;
                    account.brand = brand;
                    account.mode = IntegrationMode.DEMO;
                    account.name = brand.name() + " Demo Integration";
                    account.enabled = true;
                    return accounts.save(account);
                });
    }

    private Device createDevice(DeviceRepository devices, Tenant tenant, Site site,
                                IntegrationAccount account, Brand brand, int index,
                                String serial, boolean retired) {
        Device device = new Device();
        device.site = site;
        device.tenant = tenant;
        device.integrationAccount = account;
        device.brand = brand;
        device.model = brand.name() + " Hybrid Inverter";
        device.serialNumber = serial;
        device.externalDeviceId = brand.name() + "-DEMO-" + index;
        device.firmwareVersion = "demo-fw-1.0";
        device.ratedPowerKw = BigDecimal.valueOf(5 + index);
        device.status = DeviceStatus.ONLINE;
        device.lifecycleStatus = LifecycleStatus.ACTIVE;
        device.installedAt = LocalDate.now().minusMonths(index);
        device.purchaseCost = new BigDecimal("85000.00").add(BigDecimal.valueOf(index * 2500L));
        if (retired) {
            device.lifecycleStatus = LifecycleStatus.RETIRED;
            device.retiredAt = Instant.now().minusSeconds(86400);
            device.retirementReason = "Demo retired equipment";
            device.retirementNotes = "Kept for retirement/history workflow demos.";
        }
        return devices.save(device);
    }

    private void ensureRetiredSigenergyFixture(DeviceRepository devices, SiteRepository sites,
                                               IntegrationAccountRepository accounts,
                                               TelemetryRepository telemetry, AlarmRepository alarms,
                                               Tenant tenant, Project project) {
        Brand brand = Brand.SIGENERGY;
        Site site = ensureSite(sites, tenant, project, brand, 10);
        IntegrationAccount account = ensureAccount(accounts, tenant, brand);
        String retiredSerial = "SIGENERGY-DEMO-SN-10";
        String activeSerial = "SIGENERGY-DEMO-SN-10A";

        Device retired = devices.findFleetByTenantId(tenant.id).stream()
                .filter(d -> retiredSerial.equals(d.serialNumber))
                .findFirst()
                .orElseGet(() -> createDevice(devices, tenant, site, account, brand, 10, retiredSerial, true));
        if (retired.lifecycleStatus != LifecycleStatus.RETIRED) {
            retired.lifecycleStatus = LifecycleStatus.RETIRED;
            retired.retiredAt = Instant.now().minusSeconds(86400);
            retired.retirementReason = "Demo retired equipment";
            retired.retirementNotes = "Kept for retirement/history workflow demos.";
            devices.save(retired);
        }

        Device active = devices.findFleetByTenantId(tenant.id).stream()
                .filter(d -> activeSerial.equals(d.serialNumber))
                .findFirst()
                .orElseGet(() -> createDevice(devices, tenant, site, account, brand, 10, activeSerial, false));
        seedTelemetryAndAlarms(telemetry, alarms, devices, accounts, account, active, 10);
    }

    private void seedTelemetryAndAlarms(TelemetryRepository telemetry, AlarmRepository alarms,
                                        DeviceRepository devices, IntegrationAccountRepository accounts,
                                        IntegrationAccount account, Device device, int brandIndex) {
        if (device.lifecycleStatus == LifecycleStatus.RETIRED) {
            return;
        }
        DemoBrandConnector connector = new DemoBrandConnector(device.brand, brandIndex);
        RemoteDevice remote = new RemoteDevice(
                device.externalDeviceId, device.serialNumber, device.model,
                device.firmwareVersion,
                device.ratedPowerKw == null ? 0 : device.ratedPowerKw.doubleValue());

        ConnectorResult<NormalizedTelemetry> result = connector.readTelemetry(account, remote);
        if (result.success()) {
            NormalizedTelemetry t = result.data();
            if (!telemetry.existsByDeviceIdAndTimestamp(device.id, t.timestamp())) {
                TelemetryPoint row = new TelemetryPoint();
                row.device = device;
                row.timestamp = t.timestamp();
                row.sourceTimestamp = t.timestamp();
                row.pvPowerW = t.pvPowerW();
                row.pvEnergyTodayKwh = t.pvEnergyTodayKwh();
                row.pvEnergyTotalKwh = t.pvEnergyTotalKwh();
                row.batterySoc = t.batterySoc();
                row.batteryVoltage = t.batteryVoltage();
                row.batteryCurrent = t.batteryCurrent();
                row.batteryPowerW = t.batteryPowerW();
                row.batteryTemperatureC = t.batteryTemperatureC();
                row.gridVoltage = t.gridVoltage();
                row.gridFrequencyHz = t.gridFrequencyHz();
                row.gridPowerW = t.gridPowerW();
                row.gridImportW = t.gridImportW();
                row.gridExportW = t.gridExportW();
                row.loadPowerW = t.loadPowerW();
                row.loadEnergyTodayKwh = t.loadEnergyTodayKwh();
                row.inverterTemperatureC = t.inverterTemperatureC();
                row.inverterEfficiencyPct = t.inverterEfficiencyPct();
                row.status = t.status();
                row.faultCode = t.faultCode();
                row.warningCode = t.warningCode();
                row.rawPayload = t.rawPayload();
                telemetry.save(row);
            }
            device.status = result.data().status();
            device.lastSeenAt = result.data().timestamp();
            devices.save(device);
            if (account.lastError != null) {
                account.lastError = null;
                accounts.save(account);
            }
        } else {
            // SOLIS demo sync-error fixture: leave without fresh telemetry.
            account.lastError = result.error();
            accounts.save(account);
        }

        ConnectorResult<List<RemoteAlarm>> alarmResult = connector.readAlarms(
                account, remote, Instant.now().minusSeconds(3600), Instant.now());
        if (alarmResult.success()) {
            for (RemoteAlarm ra : alarmResult.data()) {
                String fingerprint = fingerprint(device.id, ra.code(), ra.message());
                Alarm existing = alarms.findByFingerprintAndClearedAtIsNull(fingerprint).orElse(null);
                if (existing == null) {
                    Alarm alarm = new Alarm();
                    alarm.device = device;
                    alarm.severity = ra.severity();
                    alarm.code = ra.code();
                    alarm.message = ra.message();
                    alarm.occurredAt = ra.occurredAt();
                    alarm.lastSeenAt = Instant.now();
                    alarm.fingerprint = fingerprint;
                    alarms.save(alarm);
                }
            }
        }
    }

    private void ensureDemoCosts(ProjectCostRepository costs, Project project) {
        if (costs.existsByProjectId(project.id)) {
            return;
        }
        ProjectCost equipment = new ProjectCost();
        equipment.project = project;
        equipment.category = "INVERTER";
        equipment.description = "Demo inverter procurement";
        equipment.quantity = BigDecimal.ONE;
        equipment.unitCost = new BigDecimal("85000.00");
        equipment.total = new BigDecimal("85000.00");
        equipment.currency = "ZAR";
        equipment.costDate = LocalDate.now().minusDays(45);
        costs.save(equipment);

        ProjectCost installation = new ProjectCost();
        installation.project = project;
        installation.category = "INSTALLATION";
        installation.description = "Demo electrical installation";
        installation.quantity = BigDecimal.ONE;
        installation.unitCost = new BigDecimal("42000.00");
        installation.total = new BigDecimal("42000.00");
        installation.currency = "ZAR";
        installation.costDate = LocalDate.now().minusDays(30);
        costs.save(installation);
    }

    private void ensureDemoAudit(AuditEventRepository audit, Tenant tenant, Project project) {
        if (!audit.findTop50ByTenantIdOrderByOccurredAtDesc(tenant.id).isEmpty()) {
            return;
        }
        AuditEvent event = new AuditEvent();
        event.tenant = tenant;
        event.project = project;
        event.action = "PROJECT_CREATED";
        event.summary = "Demo operating project created with all brand inverters";
        audit.save(event);
    }

    private static String fingerprint(java.util.UUID deviceId, String code, String message) {
        try {
            var digest = java.security.MessageDigest.getInstance("MD5");
            var input = (deviceId + ":" + code + ":" + message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(digest.digest(input));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }
}
