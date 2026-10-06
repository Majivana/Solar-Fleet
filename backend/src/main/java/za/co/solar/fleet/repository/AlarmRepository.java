package za.co.solar.fleet.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import za.co.solar.fleet.domain.Alarm;
import java.util.UUID;
import java.util.List;
public interface AlarmRepository extends JpaRepository<Alarm, UUID> {
  java.util.Optional<Alarm> findByFingerprintAndClearedAtIsNull(String fingerprint);
  @EntityGraph(attributePaths = "device")
  List<Alarm> findTop100ByClearedAtIsNullOrderByOccurredAtDesc();
  long countByClearedAtIsNull();
}
