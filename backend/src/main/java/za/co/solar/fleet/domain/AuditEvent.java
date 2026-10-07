package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "tenant_id", nullable = false)
    public Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "project_id")
    public Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "device_id")
    public Device device;
    @Column(name = "action", nullable = false, length = 50) public String action;
    @Column(name = "summary", nullable = false, length = 500) public String summary;
    @Column(name = "occurred_at", nullable = false) public Instant occurredAt = Instant.now();
}
