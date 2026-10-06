package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.Device;
import java.util.UUID;
import java.util.List;
public interface DeviceRepository extends JpaRepository<Device, UUID> {
  @EntityGraph(attributePaths = {"site", "site.tenant"})
  List<Device> findAll();
  @EntityGraph(attributePaths = {"site", "site.tenant"})
  List<Device> findBySiteId(UUID siteId);
  @EntityGraph(attributePaths = {"site", "site.tenant"})
  List<Device> findByIntegrationAccountId(UUID integrationAccountId);
  long countByStatus(za.co.solar.fleet.domain.DeviceStatus status);
}
