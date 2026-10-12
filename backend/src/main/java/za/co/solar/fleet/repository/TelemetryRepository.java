package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.TelemetryPoint;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
public interface TelemetryRepository extends JpaRepository<TelemetryPoint, UUID> {
  boolean existsByDeviceIdAndTimestamp(UUID deviceId, Instant timestamp);

  /**
   * Deletes up to {@code batchSize} readings older than {@code cutoff}. A reading is only eligible when the
   * device has a newer one, so the latest reading per device always survives retention.
   */
  @Transactional
  @Modifying
  @Query(value = """
      DELETE FROM telemetry
      WHERE id IN (
          SELECT x.id FROM telemetry x
          WHERE x.timestamp < :cutoff
            AND EXISTS (SELECT 1 FROM telemetry n WHERE n.device_id = x.device_id AND n.timestamp > x.timestamp)
          LIMIT :batchSize)
      """, nativeQuery = true)
  int deleteSupersededOlderThan(@Param("cutoff") Instant cutoff, @Param("batchSize") int batchSize);
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
