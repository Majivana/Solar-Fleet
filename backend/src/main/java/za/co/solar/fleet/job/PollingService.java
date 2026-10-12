package za.co.solar.fleet.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.integration.*;
import za.co.solar.fleet.repository.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pulls telemetry and alarms from every enabled integration account.
 *
 * <p>Each account is processed in three steps so that no database transaction (and no pooled connection) is held
 * while waiting on a vendor's network: (1) read a snapshot, (2) call the connector with no transaction open,
 * (3) persist the results in one short transaction. A failure in one account never affects another.
 *
 * <p>Run exactly one poller per deployment (set {@code POLLING_ENABLED=false} on additional replicas).
 */
@Service
@ConditionalOnProperty(name = "platform.polling.enabled", havingValue = "true", matchIfMissing = true)
public class PollingService {
    private static final Logger log = LoggerFactory.getLogger(PollingService.class);
    static final String CONNECTION_ERROR = "Connection failed. Check provider configuration and credentials.";

    private final IntegrationAccountRepository accounts;
    private final DeviceRepository devices;
    private final TelemetryRepository telemetry;
    private final AlarmRepository alarms;
    private final ConnectorRegistry registry;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public PollingService(IntegrationAccountRepository accounts, DeviceRepository devices,
                          TelemetryRepository telemetry, AlarmRepository alarms,
                          ConnectorRegistry registry, PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.devices = devices;
        this.telemetry = telemetry;
        this.alarms = alarms;
        this.registry = registry;
        this.writeTx = new TransactionTemplate(transactionManager);
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    @Scheduled(initialDelayString = "${platform.polling.initial-delay-ms:1500}",
            fixedDelayString = "${platform.polling.fixed-delay-ms:60000}")
    public void poll() {
        List<UUID> accountIds = readTx.execute(status ->
                accounts.findByEnabledTrueOrderByBrand().stream().map(a -> a.id).toList());
        if (accountIds == null) return;
        for (UUID accountId : accountIds) {
            try {
                pollAccount(accountId);
            } catch (RuntimeException e) {
                log.error("Polling failed for integration account {} ({})", accountId, e.getClass().getSimpleName());
                recordFailure(accountId);
            }
        }
    }

    private void pollAccount(UUID accountId) {
        Snapshot snapshot = readTx.execute(status -> {
            IntegrationAccount account = accounts.findById(accountId).orElse(null);
            if (account == null || !account.enabled) return null;
            List<DeviceRef> refs = devices
                    .findByIntegrationAccountIdAndLifecycleStatusNot(accountId, LifecycleStatus.RETIRED)
                    .stream().map(DeviceRef::of).toList();
            return new Snapshot(account, refs);
        });
        // Accounts without active equipment have nothing to poll; do not report errors for them.
        if (snapshot == null || snapshot.devices().isEmpty()) return;

        IntegrationAccount account = snapshot.account();
        InverterConnector connector = registry.forAccount(account);
        List<Polled> results = new ArrayList<>();
        boolean failed = false;
        for (DeviceRef ref : snapshot.devices()) {
            ConnectorResult<NormalizedTelemetry> reading = call(() -> connector.readTelemetry(account, ref.remote()));
            ConnectorResult<List<RemoteAlarm>> remoteAlarms = null;
            if (reading.success()) {
                Instant now = Instant.now();
                remoteAlarms = call(() -> connector.readAlarms(account, ref.remote(), now.minusSeconds(3600), now));
            }
            if (!reading.success()) {
                failed = true;
                log.warn("{} telemetry read failed for account '{}': {}", account.brand, account.name, reading.error());
            } else if (!remoteAlarms.success()) {
                failed = true;
                log.warn("{} alarm read failed for account '{}': {}", account.brand, account.name, remoteAlarms.error());
            }
            results.add(new Polled(ref, reading, remoteAlarms));
        }

        boolean anyFailure = failed;
        writeTx.executeWithoutResult(status -> persist(accountId, results, anyFailure));
    }

    private void persist(UUID accountId, List<Polled> results, boolean failed) {
        IntegrationAccount account = accounts.findById(accountId).orElseThrow();
        for (Polled polled : results) {
            Device device = devices.findById(polled.device().id()).orElse(null);
            if (device == null) continue;
            if (polled.telemetry().success()) saveTelemetry(device, polled.telemetry().data());
            if (polled.alarms() != null && polled.alarms().success()) syncAlarms(device, polled.alarms().data());
        }
        if (failed) {
            account.lastError = CONNECTION_ERROR;
        } else {
            account.lastSyncAt = Instant.now();
            account.lastError = null;
        }
    }

    private void recordFailure(UUID accountId) {
        try {
            writeTx.executeWithoutResult(status ->
                    accounts.findById(accountId).ifPresent(a -> a.lastError = CONNECTION_ERROR));
        } catch (RuntimeException e) {
            log.error("Could not record failure state for integration account {} ({})", accountId,
                    e.getClass().getSimpleName());
        }
    }

    private void saveTelemetry(Device device, NormalizedTelemetry t) {
        if (t == null || t.timestamp() == null) return;
        if (telemetry.existsByDeviceIdAndTimestamp(device.id, t.timestamp())) return;
        TelemetryPoint row = new TelemetryPoint();
        row.device = device; row.timestamp = t.timestamp(); row.sourceTimestamp = t.timestamp(); row.pvPowerW = t.pvPowerW();
        row.pvEnergyTodayKwh = t.pvEnergyTodayKwh(); row.pvEnergyTotalKwh = t.pvEnergyTotalKwh();
        row.batterySoc = t.batterySoc(); row.batteryVoltage = t.batteryVoltage(); row.batteryCurrent = t.batteryCurrent();
        row.batteryPowerW = t.batteryPowerW(); row.batteryTemperatureC = t.batteryTemperatureC();
        row.gridVoltage = t.gridVoltage(); row.gridFrequencyHz = t.gridFrequencyHz(); row.gridPowerW = t.gridPowerW();
        row.gridImportW = t.gridImportW(); row.gridExportW = t.gridExportW(); row.loadPowerW = t.loadPowerW();
        row.loadEnergyTodayKwh = t.loadEnergyTodayKwh(); row.inverterTemperatureC = t.inverterTemperatureC();
        row.inverterEfficiencyPct = t.inverterEfficiencyPct(); row.status = t.status();
        row.faultCode = truncate(t.faultCode(), 100);
        row.warningCode = truncate(t.warningCode(), 100);
        row.rawPayload = t.rawPayload();
        telemetry.save(row);
        if (t.status() != null) device.status = t.status();
        device.lastSeenAt = t.timestamp();
    }

    private void syncAlarms(Device device, List<RemoteAlarm> remoteAlarms) {
        if (remoteAlarms == null) return;
        for (RemoteAlarm ra : remoteAlarms) {
            if (ra == null || ra.code() == null || ra.code().isBlank()) continue;
            String code = truncate(ra.code().trim(), 100);
            String message = truncate(ra.message() == null || ra.message().isBlank() ? code : ra.message(), 1000);
            String fingerprint = alarmFingerprint(device.id, code, message);
            Alarm alarm = alarms.findByFingerprintAndClearedAtIsNull(fingerprint).orElseGet(Alarm::new);
            if (alarm.id == null) {
                alarm.device = device;
                alarm.severity = truncate(ra.severity() == null ? "WARNING" : ra.severity().toUpperCase(Locale.ROOT), 30);
                alarm.code = code;
                alarm.message = message;
                alarm.occurredAt = ra.occurredAt() == null ? Instant.now() : ra.occurredAt();
                alarm.fingerprint = fingerprint;
            }
            alarm.lastSeenAt = Instant.now();
            alarms.save(alarm);
        }
    }

    private static <T> ConnectorResult<T> call(Supplier<ConnectorResult<T>> action) {
        try {
            ConnectorResult<T> result = action.get();
            return result == null ? ConnectorResult.fail("Connector returned no result.") : result;
        } catch (RuntimeException e) {
            return ConnectorResult.fail("Connector failed unexpectedly (" + e.getClass().getSimpleName() + ").");
        }
    }

    static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Identity of an open alarm. MD5 is used purely as a compact, stable key (it is not a security control) and must
     * stay in sync with the {@code md5(device_id:code:message)} fingerprints written by migrations V2 and V6.
     */
    static String alarmFingerprint(UUID deviceId, String code, String message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] input = (deviceId + ":" + code + ":" + message).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 is unavailable", e);
        }
    }

    private record Snapshot(IntegrationAccount account, List<DeviceRef> devices) {}

    private record Polled(DeviceRef device, ConnectorResult<NormalizedTelemetry> telemetry,
                          ConnectorResult<List<RemoteAlarm>> alarms) {}

    record DeviceRef(UUID id, String externalId, String serialNumber, String model, String firmwareVersion,
                     double ratedPowerKw) {
        static DeviceRef of(Device d) {
            return new DeviceRef(d.id, d.externalDeviceId, d.serialNumber, d.model, d.firmwareVersion,
                    d.ratedPowerKw == null ? 0 : d.ratedPowerKw.doubleValue());
        }

        RemoteDevice remote() {
            return new RemoteDevice(externalId, serialNumber, model, firmwareVersion, ratedPowerKw);
        }
    }
}
