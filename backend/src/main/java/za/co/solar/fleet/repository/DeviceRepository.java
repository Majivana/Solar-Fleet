package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.Device;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface DeviceRepository extends JpaRepository<Device, UUID> {
  @EntityGraph(attributePaths = {"site", "site.tenant", "site.project", "integrationAccount"})
  List<Device> findAll();
  @EntityGraph(attributePaths = {"site", "site.tenant", "site.project", "integrationAccount"})
  List<Device> findBySiteId(UUID siteId);
  @EntityGraph(attributePaths = {"site", "site.tenant", "site.project", "integrationAccount"})
  List<Device> findByIntegrationAccountId(UUID integrationAccountId);
  @Query("select d from Device d join fetch d.tenant join fetch d.site s join fetch s.tenant " +
          "left join fetch s.project left join fetch d.integrationAccount where d.tenant.id = :tenantId")
  List<Device> findFleetByTenantId(@Param("tenantId") UUID tenantId);
  @EntityGraph(attributePaths = {"site", "site.project", "integrationAccount"})
  java.util.Optional<Device> findByIdAndTenantId(UUID id, UUID tenantId);
  long countByStatus(za.co.solar.fleet.domain.DeviceStatus status);
  boolean existsByTenantIdAndBrandAndSerialNumber(UUID tenantId, za.co.solar.fleet.domain.Brand brand, String serialNumber);
}
