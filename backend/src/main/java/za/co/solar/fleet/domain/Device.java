package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
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
    @JoinColumn(name = "integration_account_id", nullable = false)
    public IntegrationAccount integrationAccount;
    @Enumerated(EnumType.STRING) @Column(name = "brand", nullable = false) public Brand brand;
    @Column(name = "model", nullable = false) public String model;
    @Column(name = "serial_number", nullable = false, unique = true) public String serialNumber;
    @Column(name = "external_device_id") public String externalDeviceId;
    @Column(name = "firmware_version") public String firmwareVersion;
    @Column(name = "rated_power_kw") public BigDecimal ratedPowerKw;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) public DeviceStatus status = DeviceStatus.UNKNOWN;
    @Column(name = "last_seen_at") public Instant lastSeenAt;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
}
