package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sites")
public class Site {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    public Tenant tenant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    public Project project;
    @Column(name = "name", nullable = false) public String name;
    @Column(name = "address") public String address;
    @Column(name = "province") public String province;
    @Column(name = "municipality") public String municipality;
    @Column(name = "latitude") public BigDecimal latitude;
    @Column(name = "longitude") public BigDecimal longitude;
    @Column(name = "timezone", nullable = false) public String timezone = "Africa/Johannesburg";
    @Column(name = "rated_pv_kw") public BigDecimal ratedPvKw;
    @Column(name = "rated_inverter_kw") public BigDecimal ratedInverterKw;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
}
