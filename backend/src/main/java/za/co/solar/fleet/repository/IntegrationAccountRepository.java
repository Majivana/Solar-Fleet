package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.*;
import java.util.UUID;
import java.util.List;
public interface IntegrationAccountRepository extends JpaRepository<IntegrationAccount, UUID> {
  List<IntegrationAccount> findByEnabledTrueOrderByBrand();
  java.util.Optional<IntegrationAccount> findFirstByTenantIdAndBrandAndEnabledTrue(UUID tenantId, za.co.solar.fleet.domain.Brand brand);
}
