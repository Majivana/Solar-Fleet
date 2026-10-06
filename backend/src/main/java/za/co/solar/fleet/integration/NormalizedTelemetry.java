package za.co.solar.fleet.integration;

import za.co.solar.fleet.domain.DeviceStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record NormalizedTelemetry(
        Instant timestamp,
        BigDecimal pvPowerW,
        BigDecimal pvEnergyTodayKwh,
        BigDecimal pvEnergyTotalKwh,
        BigDecimal batterySoc,
        BigDecimal batteryVoltage,
        BigDecimal batteryCurrent,
        BigDecimal batteryPowerW,
        BigDecimal batteryTemperatureC,
        BigDecimal gridVoltage,
        BigDecimal gridFrequencyHz,
        BigDecimal gridPowerW,
        BigDecimal gridImportW,
        BigDecimal gridExportW,
        BigDecimal loadPowerW,
        BigDecimal loadEnergyTodayKwh,
        BigDecimal inverterTemperatureC,
        BigDecimal inverterEfficiencyPct,
        DeviceStatus status,
        String faultCode,
        String warningCode,
        String rawPayload
) {}
