package za.co.solar.fleet.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.solar.fleet.domain.*;
import za.co.solar.fleet.repository.IntegrationAccountRepository;
import za.co.solar.fleet.repository.TenantRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Non-demo deployments previously had no way to create the configured tenant or any integration account, so no
 * inverter could ever be onboarded. This idempotently creates the tenant named by TENANT_ID and one LIVE integration
 * account per manufacturer. Accounts have no credentials, so live polling honestly reports "sync error" until an
 * approved OEM adapter is configured.
 */
@Configuration
public class TenantBootstrap {
    private static final Logger log = LoggerFactory.getLogger(TenantBootstrap.class);

    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    CommandLineRunner bootstrapOperatorTenant(TenantRepository tenants, IntegrationAccountRepository accounts,
                                              PlatformTransactionManager transactionManager,
                                              @Value("${platform.demo-data:false}") boolean demo,
                                              @Value("${platform.tenant-id:}") String tenantId,
                                              @Value("${platform.tenant-name:Operator}") String tenantName) {
        return args -> {
            if (demo || tenantId.isBlank()) return;
            UUID id = UUID.fromString(tenantId);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                // Native insert so the operator-chosen id is honoured (the entity generates its own ids).
                int created = entityManager.createNativeQuery(
                                "INSERT INTO tenants (id, name, created_at) VALUES (:id, :name, :ts) ON CONFLICT DO NOTHING")
                        .setParameter("id", id)
                        .setParameter("name", tenantName.isBlank() ? "Operator" : tenantName.trim())
                        .setParameter("ts", Timestamp.from(Instant.now()))
                        .executeUpdate();
                if (created > 0) log.info("Created operator tenant {}", id);
                Tenant tenant = tenants.findById(id).orElseThrow(() -> new IllegalStateException(
                        "TENANT_ID " + id + " does not exist and could not be created (is the tenant name already used?)."));
                for (Brand brand : Brand.values()) {
                    if (accounts.existsByTenantIdAndBrand(id, brand)) continue;
                    IntegrationAccount account = new IntegrationAccount();
                    account.tenant = tenant;
                    account.brand = brand;
                    account.mode = IntegrationMode.LIVE;
                    account.name = brand.name() + " (live - credentials not configured)";
                    account.enabled = true;
                    accounts.save(account);
                }
            });
        };
    }
}
