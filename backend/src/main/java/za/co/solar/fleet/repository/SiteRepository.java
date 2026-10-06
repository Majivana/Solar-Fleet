package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.Site;
import java.util.UUID;
import java.util.List;
public interface SiteRepository extends JpaRepository<Site, UUID> { List<Site> findByTenantIdOrderByName(UUID tenantId); }
