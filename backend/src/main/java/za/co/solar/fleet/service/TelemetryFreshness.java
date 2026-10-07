package za.co.solar.fleet.service;

import java.time.Instant;

public final class TelemetryFreshness {
    private static final long MAX_AGE_SECONDS = 15 * 60;
    private static final long MAX_FUTURE_SKEW_SECONDS = 60;

    private TelemetryFreshness() {}

    public static boolean isFresh(Instant timestamp, Instant now) {
        return timestamp != null
                && !timestamp.isBefore(now.minusSeconds(MAX_AGE_SECONDS))
                && !timestamp.isAfter(now.plusSeconds(MAX_FUTURE_SKEW_SECONDS));
    }
}
