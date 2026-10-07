package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_costs")
public class ProjectCost {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    public Project project;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "site_id")
    public Site site;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "device_id")
    public Device device;
    @Column(name = "category", nullable = false, length = 30) public String category;
    @Column(name = "description", nullable = false, length = 500) public String description;
    @Column(name = "quantity", nullable = false, precision = 12, scale = 3) public BigDecimal quantity;
    @Column(name = "unit_cost", nullable = false, precision = 14, scale = 2) public BigDecimal unitCost;
    @Column(name = "total", nullable = false, precision = 16, scale = 2) public BigDecimal total;
    @Column(name = "currency", nullable = false, length = 3) public String currency = "ZAR";
    @Column(name = "cost_date", nullable = false) public LocalDate costDate;
    @Column(name = "supplier", length = 200) public String supplier;
    @Column(name = "reference", length = 200) public String reference;
    @Column(name = "notes", length = 2000) public String notes;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
}
