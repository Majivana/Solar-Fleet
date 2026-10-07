package za.co.solar.fleet.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.AuditEvent;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    @EntityGraph(attributePaths = {"project", "device"})
    List<AuditEvent> findTop50ByTenantIdOrderByOccurredAtDesc(UUID tenantId);
    @EntityGraph(attributePaths = {"project", "device"})
    List<AuditEvent> findTop50ByTenantIdAndProjectIdOrderByOccurredAtDesc(UUID tenantId, UUID projectId);
}
