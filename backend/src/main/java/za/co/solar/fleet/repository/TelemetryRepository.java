package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.TelemetryPoint;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface TelemetryRepository extends JpaRepository<TelemetryPoint, UUID> {
  boolean existsByDeviceIdAndTimestamp(UUID deviceId, Instant timestamp);
  List<TelemetryPoint> findTop100ByDeviceIdOrderByTimestampDesc(UUID deviceId);
  List<TelemetryPoint> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(UUID deviceId, Instant from, Instant to);
  @Query(value = """
      SELECT DISTINCT ON (t.device_id) t.*
      FROM telemetry t
      JOIN devices d ON d.id = t.device_id
      JOIN sites s ON s.id = d.site_id
      WHERE s.tenant_id = :tenantId AND d.lifecycle_status <> 'RETIRED'
      ORDER BY t.device_id, t.timestamp DESC
      """, nativeQuery = true)
  List<TelemetryPoint> findLatestForTenant(@Param("tenantId") UUID tenantId);
}
