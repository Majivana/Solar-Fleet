package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.solar.fleet.domain.TelemetryPoint;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
public interface TelemetryRepository extends JpaRepository<TelemetryPoint, UUID> {
  boolean existsByDeviceIdAndTimestamp(UUID deviceId, Instant timestamp);
  List<TelemetryPoint> findTop100ByDeviceIdOrderByTimestampDesc(UUID deviceId);
  List<TelemetryPoint> findByDeviceIdAndTimestampBetweenOrderByTimestampAsc(UUID deviceId, Instant from, Instant to);
}
