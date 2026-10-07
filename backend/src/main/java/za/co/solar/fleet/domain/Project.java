package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    public Tenant tenant;
    @Column(name = "name", nullable = false, length = 200) public String name;
    @Column(name = "customer", length = 200) public String customer;
    @Column(name = "location", length = 300) public String location;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false) public ProjectStatus status = ProjectStatus.OPERATIONAL;
    @Column(name = "description", length = 2000) public String description;
    @Column(name = "start_date") public LocalDate startDate;
    @Column(name = "commissioning_date") public LocalDate commissioningDate;
    @Column(name = "budget", precision = 16, scale = 2) public BigDecimal budget;
    @Column(name = "capacity_kw", precision = 12, scale = 3) public BigDecimal capacityKw;
    @Column(name = "import_tariff", precision = 12, scale = 4) public BigDecimal importTariff;
    @Column(name = "export_tariff", precision = 12, scale = 4) public BigDecimal exportTariff;
    @Column(name = "currency", nullable = false, length = 3) public String currency = "ZAR";
    @Column(name = "notes", length = 2000) public String notes;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
}
