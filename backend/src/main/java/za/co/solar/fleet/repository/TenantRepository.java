package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.Tenant;
import java.util.UUID;
public interface TenantRepository extends JpaRepository<Tenant, UUID> {}
