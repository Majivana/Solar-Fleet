package za.co.solar.fleet.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.integration.*;
import za.co.solar.fleet.repository.*;

import java.time.Instant;
import java.util.List;

@Service
public class PollingService {
    private static final Logger log = LoggerFactory.getLogger(PollingService.class);
    private final IntegrationAccountRepository accounts;
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final AlarmRepository alarms;
    private final ConnectorRegistry registry;

    public PollingService(IntegrationAccountRepository accounts, DeviceRepository devices,
                          TelemetryRepository telemetry, AlarmRepository alarms,
                          ConnectorRegistry registry) {
        this.accounts = accounts; this.devices = devices; this.telemetry = telemetry;
        this.alarms = alarms; this.registry = registry;
    }

    @Scheduled(initialDelayString = "${platform.polling.initial-delay-ms:1500}", fixedDelayString = "${platform.polling.fixed-delay-ms:60000}")
    @Transactional
    public void poll() {
        for (IntegrationAccount account : accounts.findByEnabledTrueOrderByBrand()) {
            try {
                pollAccount(account);
                account.lastSyncAt = Instant.now();
                account.lastError = null;
                accounts.save(account);
            } catch (Exception e) {
                account.lastError = "Connection failed. Check provider configuration and credentials.";
                accounts.save(account);
                log.error("Polling failed for {} / {} ({})", account.brand, account.name, e.getClass().getSimpleName());
            }
        }
    }

    private void pollAccount(IntegrationAccount account) {
        InverterConnector connector = registry.forAccount(account);
        List<Device> existing = devices.findByIntegrationAccountId(account.id);
        if (existing.isEmpty()) {
            ConnectorResult<List<RemoteDevice>> discovered = connector.discoverDevices(account);
            if (!discovered.success()) throw new IllegalStateException(discovered.error());
            for (RemoteDevice remote : discovered.data()) {
                Device d = new Device();
                d.site = null;
                // The demo seeder creates devices before polling. For live discovery,
                // site binding must be completed explicitly by the operator.
                if (account.mode == IntegrationMode.LIVE) continue;
            }
        }
        for (Device device : existing) {
            if (device.lifecycleStatus == LifecycleStatus.RETIRED) continue;
            ConnectorResult<NormalizedTelemetry> result = connector.readTelemetry(account,
                    new RemoteDevice(device.externalDeviceId, device.serialNumber, device.model,
                            device.firmwareVersion, device.ratedPowerKw == null ? 0 : device.ratedPowerKw.doubleValue()));
            if (!result.success()) throw new IllegalStateException(result.error());
            saveTelemetry(device, result.data());
            syncAlarms(account, connector, device);
        }
    }

    private void saveTelemetry(Device device, NormalizedTelemetry t) {
        if (telemetry.existsByDeviceIdAndTimestamp(device.id, t.timestamp())) return;
        TelemetryPoint row = new TelemetryPoint();
        row.device = device; row.timestamp = t.timestamp(); row.sourceTimestamp = t.timestamp(); row.pvPowerW = t.pvPowerW();
        row.pvEnergyTodayKwh = t.pvEnergyTodayKwh(); row.pvEnergyTotalKwh = t.pvEnergyTotalKwh();
        row.batterySoc = t.batterySoc(); row.batteryVoltage = t.batteryVoltage(); row.batteryCurrent = t.batteryCurrent();
        row.batteryPowerW = t.batteryPowerW(); row.batteryTemperatureC = t.batteryTemperatureC();
        row.gridVoltage = t.gridVoltage(); row.gridFrequencyHz = t.gridFrequencyHz(); row.gridPowerW = t.gridPowerW();
        row.gridImportW = t.gridImportW(); row.gridExportW = t.gridExportW(); row.loadPowerW = t.loadPowerW();
        row.loadEnergyTodayKwh = t.loadEnergyTodayKwh(); row.inverterTemperatureC = t.inverterTemperatureC();
        row.inverterEfficiencyPct = t.inverterEfficiencyPct(); row.status = t.status(); row.faultCode = t.faultCode();
        row.warningCode = t.warningCode(); row.rawPayload = t.rawPayload();
        telemetry.save(row);
        device.status = t.status(); device.lastSeenAt = t.timestamp(); devices.save(device);
    }

    private void syncAlarms(IntegrationAccount account, InverterConnector connector, Device device) {
        ConnectorResult<List<RemoteAlarm>> result = connector.readAlarms(account,
                new RemoteDevice(device.externalDeviceId, device.serialNumber, device.model,
                        device.firmwareVersion, device.ratedPowerKw == null ? 0 : device.ratedPowerKw.doubleValue()),
                Instant.now().minusSeconds(3600), Instant.now());
        if (!result.success()) throw new IllegalStateException("Alarm synchronization failed.");
        for (RemoteAlarm ra : result.data()) {
            String fingerprint = alarmFingerprint(device.id, ra.code(), ra.message());
            Alarm a = alarms.findByFingerprintAndClearedAtIsNull(fingerprint).orElseGet(Alarm::new);
            if (a.id == null) {
                a.device = device; a.severity = ra.severity(); a.code = ra.code();
                a.message = ra.message(); a.occurredAt = ra.occurredAt(); a.fingerprint = fingerprint;
            }
            a.lastSeenAt = Instant.now(); alarms.save(a);
        }
    }

    private String alarmFingerprint(java.util.UUID deviceId, String code, String message) {
        try {
            var digest = java.security.MessageDigest.getInstance("MD5");
            var input = (deviceId + ":" + code + ":" + message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(digest.digest(input));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }
}
