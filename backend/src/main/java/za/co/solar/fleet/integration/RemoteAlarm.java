package za.co.solar.fleet.integration;

import java.time.Instant;
public record RemoteAlarm(String severity, String code, String message, Instant occurredAt) {}
