package za.co.solar.fleet.service;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryFreshnessTest {
    private final Instant now = Instant.parse("2026-10-07T12:00:00Z");

    @Test
    void includesFreshBoundaryAndSmallFutureClockSkew() {
        assertTrue(TelemetryFreshness.isFresh(now.minusSeconds(15 * 60), now));
        assertTrue(TelemetryFreshness.isFresh(now.plusSeconds(60), now));
    }

    @Test
    void rejectsStaleMissingAndImplausiblyFutureReadings() {
        assertFalse(TelemetryFreshness.isFresh(now.minusSeconds(15 * 60 + 1), now));
        assertFalse(TelemetryFreshness.isFresh(null, now));
        assertFalse(TelemetryFreshness.isFresh(now.plusSeconds(61), now));
    }
}
