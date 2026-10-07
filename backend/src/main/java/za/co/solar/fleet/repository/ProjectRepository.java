package za.co.solar.fleet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @EntityGraph(attributePaths = "tenant")
    List<Project> findByTenantIdOrderByName(UUID tenantId);
    @EntityGraph(attributePaths = "tenant")
    Optional<Project> findByIdAndTenantId(UUID id, UUID tenantId);
    boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
