package za.co.solar.fleet.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.integration.*;
import za.co.solar.fleet.repository.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Polls integration accounts for telemetry and alarms.
 * Connector I/O runs outside database transactions; persistence uses short transactions.
 */
@Service
public class PollingService {
    private static final Logger log = LoggerFactory.getLogger(PollingService.class);
    private static final String GENERIC_CONNECTION_ERROR =
            "Connection failed. Check provider configuration and credentials.";

    private final IntegrationAccountRepository accounts;
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final AlarmRepository alarms;
    private final ConnectorRegistry registry;
    private final TransactionTemplate tx;

    public PollingService(IntegrationAccountRepository accounts, DeviceRepository devices,
                          TelemetryRepository telemetry, AlarmRepository alarms,
                          ConnectorRegistry registry, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.devices = devices;
        this.telemetry = telemetry;
        this.alarms = alarms;
        this.registry = registry;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(initialDelayString = "${platform.polling.initial-delay-ms:1500}",
            fixedDelayString = "${platform.polling.fixed-delay-ms:60000}")
    public void poll() {
        List<IntegrationAccount> enabled = accounts.findByEnabledTrueOrderByBrand();
        for (IntegrationAccount account : enabled) {
            pollAccount(account);
        }
    }

    private void pollAccount(IntegrationAccount account) {
        try {
            InverterConnector connector = registry.forAccount(account);
            List<Device> existing = devices.findByIntegrationAccountId(account.id);
            if (existing.isEmpty()) {
                discoverIfNeeded(account, connector);
                markSynced(account.id);
                return;
            }

            boolean anyFailure = false;
            String failureMessage = null;
            for (Device device : existing) {
                if (device.lifecycleStatus == LifecycleStatus.RETIRED) {
                    continue;
                }
                DevicePollOutcome outcome = pollDevice(account, connector, device);
                if (!outcome.success()) {
                    anyFailure = true;
                    failureMessage = outcome.message();
                }
            }

            if (anyFailure) {
                markFailed(account.id, failureMessage == null ? GENERIC_CONNECTION_ERROR : failureMessage);
            } else {
                markSynced(account.id);
            }
        } catch (Exception e) {
            markFailed(account.id, GENERIC_CONNECTION_ERROR);
            log.warn("Polling failed for {} / {} ({})", account.brand, account.name, e.getClass().getSimpleName());
        }
    }

    private void discoverIfNeeded(IntegrationAccount account, InverterConnector connector) {
        ConnectorResult<List<RemoteDevice>> discovered = connector.discoverDevices(account);
        if (!discovered.success()) {
            throw new IllegalStateException(discovered.error());
        }
        for (RemoteDevice ignored : discovered.data()) {
            // Live discovery requires explicit operator site assignment before import.
            if (account.mode == IntegrationMode.LIVE) {
                continue;
            }
        }
    }

    private DevicePollOutcome pollDevice(IntegrationAccount account, InverterConnector connector, Device device) {
        RemoteDevice remote = new RemoteDevice(
                device.externalDeviceId,
                device.serialNumber,
                device.model,
                device.firmwareVersion,
                device.ratedPowerKw == null ? 0 : device.ratedPowerKw.doubleValue());

        ConnectorResult<NormalizedTelemetry> result = connector.readTelemetry(account, remote);
        if (!result.success()) {
            log.warn("Telemetry sync failed for {} / {}: {}", account.brand, device.serialNumber, result.error());
            return DevicePollOutcome.failed(sanitizeProviderMessage(result.error()));
        }

        UUID deviceId = device.id;
        tx.executeWithoutResult(status -> saveTelemetry(deviceId, result.data()));

        ConnectorResult<List<RemoteAlarm>> alarmResult = connector.readAlarms(
                account, remote, Instant.now().minusSeconds(3600), Instant.now());
        if (!alarmResult.success()) {
            log.warn("Alarm sync failed for {} / {}", account.brand, device.serialNumber);
            return DevicePollOutcome.failed("Alarm synchronization failed.");
        }
        tx.executeWithoutResult(status -> persistAlarms(deviceId, alarmResult.data()));
        return DevicePollOutcome.ok();
    }

    private void saveTelemetry(UUID deviceId, NormalizedTelemetry t) {
        Device device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            return;
        }
        if (telemetry.existsByDeviceIdAndTimestamp(device.id, t.timestamp())) {
            return;
        }
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
        device.status = t.status();
        device.lastSeenAt = t.timestamp();
        devices.save(device);
    }

    private void persistAlarms(UUID deviceId, List<RemoteAlarm> remoteAlarms) {
        Device device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            return;
        }
        for (RemoteAlarm ra : remoteAlarms) {
            String fingerprint = alarmFingerprint(device.id, ra.code(), ra.message());
            Alarm a = alarms.findByFingerprintAndClearedAtIsNull(fingerprint).orElseGet(Alarm::new);
            if (a.id == null) {
                a.device = device;
                a.severity = ra.severity();
                a.code = ra.code();
                a.message = ra.message();
                a.occurredAt = ra.occurredAt();
                a.fingerprint = fingerprint;
            }
            a.lastSeenAt = Instant.now();
            alarms.save(a);
        }
    }

    private void markSynced(UUID accountId) {
        tx.executeWithoutResult(status -> {
            IntegrationAccount account = accounts.findById(accountId).orElse(null);
            if (account == null) {
                return;
            }
            account.lastSyncAt = Instant.now();
            account.lastError = null;
            accounts.save(account);
        });
    }

    private void markFailed(UUID accountId, String message) {
        tx.executeWithoutResult(status -> {
            IntegrationAccount account = accounts.findById(accountId).orElse(null);
            if (account == null) {
                return;
            }
            account.lastError = message;
            accounts.save(account);
        });
    }

    private String sanitizeProviderMessage(String message) {
        if (message == null || message.isBlank()) {
            return GENERIC_CONNECTION_ERROR;
        }
        String trimmed = message.trim();
        return trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed;
    }

    private String alarmFingerprint(UUID deviceId, String code, String message) {
        try {
            var digest = java.security.MessageDigest.getInstance("MD5");
            var input = (deviceId + ":" + code + ":" + message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(digest.digest(input));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    private record DevicePollOutcome(boolean success, String message) {
        static DevicePollOutcome ok() {
            return new DevicePollOutcome(true, null);
        }

        static DevicePollOutcome failed(String message) {
            return new DevicePollOutcome(false, message);
        }
    }
}
