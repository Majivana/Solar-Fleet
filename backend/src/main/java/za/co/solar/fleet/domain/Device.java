package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "devices")
public class Device {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    public Site site;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    public Tenant tenant;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_account_id", nullable = false)
    public IntegrationAccount integrationAccount;
    @Enumerated(EnumType.STRING) @Column(name = "brand", nullable = false) public Brand brand;
    @Column(name = "model", nullable = false, length = 200) public String model;
    @Column(name = "serial_number", nullable = false, length = 200) public String serialNumber;
    @Column(name = "external_device_id", length = 200) public String externalDeviceId;
    @Column(name = "firmware_version", length = 200) public String firmwareVersion;
    @Column(name = "rated_power_kw") public BigDecimal ratedPowerKw;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) public DeviceStatus status = DeviceStatus.UNKNOWN;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false) public LifecycleStatus lifecycleStatus = LifecycleStatus.ACTIVE;
    @Column(name = "last_seen_at") public Instant lastSeenAt;
    @Column(name = "installed_at") public LocalDate installedAt;
    @Column(name = "warranty_until") public LocalDate warrantyUntil;
    @Column(name = "purchase_cost", precision = 14, scale = 2) public BigDecimal purchaseCost;
    @Column(name = "notes", length = 2000) public String notes;
    @Column(name = "retired_at") public Instant retiredAt;
    @Column(name = "retirement_reason", length = 500) public String retirementReason;
    @Column(name = "retirement_notes", length = 2000) public String retirementNotes;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
}
