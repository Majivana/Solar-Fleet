package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alarms")
public class Alarm {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    public Device device;
    @Column(name = "severity", nullable = false, length = 30) public String severity;
    @Column(name = "code", nullable = false, length = 100) public String code;
    @Column(name = "message", nullable = false, length = 1000) public String message;
    @Column(name = "occurred_at", nullable = false) public Instant occurredAt;
    @Column(name = "cleared_at") public Instant clearedAt;
    @Column(name = "fingerprint", length = 64) public String fingerprint;
    @Column(name = "last_seen_at") public Instant lastSeenAt;
    @Column(name = "acknowledged_at") public Instant acknowledgedAt;
}
