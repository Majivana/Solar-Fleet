package za.co.solar.fleet.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integration_accounts")
public class IntegrationAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    public Tenant tenant;
    @Enumerated(EnumType.STRING) @Column(name = "brand", nullable = false) public Brand brand;
    @Enumerated(EnumType.STRING) @Column(name = "mode", nullable = false) public IntegrationMode mode = IntegrationMode.DEMO;
    @Column(name = "name", nullable = false) public String name;
    @Column(name = "enabled", nullable = false) public boolean enabled = true;
    @Column(name = "base_url") public String baseUrl;
    @Column(name = "auth_config_json", columnDefinition = "text") public String authConfigJson;
    @Column(name = "created_at", nullable = false) public Instant createdAt = Instant.now();
    @Column(name = "last_sync_at") public Instant lastSyncAt;
    @Column(name = "last_error") public String lastError;
}
