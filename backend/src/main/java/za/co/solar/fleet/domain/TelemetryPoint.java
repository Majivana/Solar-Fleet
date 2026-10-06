package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry")
public class TelemetryPoint {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    public Device device;
    @Column(name = "timestamp", nullable = false) public Instant timestamp;
    @Column(name = "pv_power_w") public BigDecimal pvPowerW;
    @Column(name = "pv_energy_today_kwh") public BigDecimal pvEnergyTodayKwh;
    @Column(name = "pv_energy_total_kwh") public BigDecimal pvEnergyTotalKwh;
    @Column(name = "battery_soc") public BigDecimal batterySoc;
    @Column(name = "battery_voltage") public BigDecimal batteryVoltage;
    @Column(name = "battery_current") public BigDecimal batteryCurrent;
    @Column(name = "battery_power_w") public BigDecimal batteryPowerW;
    @Column(name = "battery_temperature_c") public BigDecimal batteryTemperatureC;
    @Column(name = "grid_voltage") public BigDecimal gridVoltage;
    @Column(name = "grid_frequency_hz") public BigDecimal gridFrequencyHz;
    @Column(name = "grid_power_w") public BigDecimal gridPowerW;
    @Column(name = "grid_import_w") public BigDecimal gridImportW;
    @Column(name = "grid_export_w") public BigDecimal gridExportW;
    @Column(name = "load_power_w") public BigDecimal loadPowerW;
    @Column(name = "load_energy_today_kwh") public BigDecimal loadEnergyTodayKwh;
    @Column(name = "inverter_temperature_c") public BigDecimal inverterTemperatureC;
    @Column(name = "inverter_efficiency_pct") public BigDecimal inverterEfficiencyPct;
    @Enumerated(EnumType.STRING) public DeviceStatus status;
    @Column(name = "fault_code") public String faultCode;
    @Column(name = "warning_code") public String warningCode;
    @Column(name = "raw_payload", columnDefinition = "text") public String rawPayload;
    @Column(name = "ingested_at", nullable = false) public Instant ingestedAt = Instant.now();
    @Column(name = "source_timestamp") public Instant sourceTimestamp;
    @Column(name = "provider_sequence") public String providerSequence;
    @Column(name = "provider_schema_version") public String providerSchemaVersion;
}
