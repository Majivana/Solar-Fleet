package za.co.solar.fleet.integration;

public record ConnectorCapabilities(boolean telemetry, boolean history, boolean alarms, boolean remoteControl, boolean push) {
    public static ConnectorCapabilities readOnly() { return new ConnectorCapabilities(true, true, true, false, false); }
}
