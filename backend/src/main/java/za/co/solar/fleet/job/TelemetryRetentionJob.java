package za.co.solar.fleet.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import za.co.solar.fleet.repository.TelemetryRepository;

import java.time.Duration;
import java.time.Instant;

/**
 * Nightly purge of old telemetry so the table cannot grow without bound. Deletes in small batches (each its own
 * transaction) to avoid long locks, and always keeps the newest reading of every device.
 */
@Service
@ConditionalOnProperty(name = "platform.polling.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);
    static final int BATCH_SIZE = 5_000;
    private static final int MAX_BATCHES_PER_RUN = 2_000;

    private final TelemetryRepository telemetry;
    private final int retentionDays;

    public TelemetryRetentionJob(TelemetryRepository telemetry,
                                 @Value("${platform.telemetry.retention-days:90}") int retentionDays) {
        this.telemetry = telemetry;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "${platform.telemetry.retention-cron:0 30 2 * * *}", zone = "Africa/Johannesburg")
    public void purge() {
        if (retentionDays <= 0) return;
        Instant cutoff = Instant.now().minus(Duration.ofDays(retentionDays));
        long total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int deleted = telemetry.deleteSupersededOlderThan(cutoff, BATCH_SIZE);
            total += deleted;
            if (deleted < BATCH_SIZE) break;
        }
        if (total > 0) log.info("Telemetry retention removed {} readings older than {} days", total, retentionDays);
    }
}
