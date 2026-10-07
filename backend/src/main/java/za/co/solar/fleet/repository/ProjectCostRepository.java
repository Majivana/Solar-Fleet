package za.co.solar.fleet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.solar.fleet.domain.ProjectCost;
import java.util.List;
import java.util.UUID;

public interface ProjectCostRepository extends JpaRepository<ProjectCost, UUID> {
    @EntityGraph(attributePaths = {"project", "site", "device"})
    List<ProjectCost> findByProjectIdOrderByCostDateDesc(UUID projectId);
    @EntityGraph(attributePaths = {"project", "site", "device"})
    List<ProjectCost> findByProjectTenantIdOrderByCostDateDesc(UUID tenantId);
    java.util.Optional<ProjectCost> findFirstByDeviceIdAndReference(UUID deviceId, String reference);
    boolean existsByProjectId(UUID projectId);
    @Query("select coalesce(sum(c.total), 0) from ProjectCost c where c.project.id = :projectId")
    java.math.BigDecimal totalForProject(@Param("projectId") UUID projectId);
    @Query("select c.project.id, sum(c.total) from ProjectCost c where c.project.tenant.id = :tenantId group by c.project.id")
    List<Object[]> totalsByProject(@Param("tenantId") UUID tenantId);
}
