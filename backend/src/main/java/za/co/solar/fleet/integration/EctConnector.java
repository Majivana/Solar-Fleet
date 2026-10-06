package za.co.solar.fleet.integration;

import za.co.solar.fleet.domain.Brand;
import za.co.solar.fleet.domain.IntegrationAccount;
import java.time.Instant;
import java.util.List;

/** ECT is intentionally unverified. Do not implement a guessed protocol. */
public class EctConnector extends LiveApiConnectorBoundary {
    @Override public Brand brand() { return Brand.ECT_UNVERIFIED; }
    @Override protected String vendorDocumentationUrl() { return "TBD - exact OEM/model/logger required"; }
    @Override public ConnectorResult<List<RemoteDevice>> discoverDevices(IntegrationAccount account) { return notConfigured(); }
    @Override public ConnectorResult<NormalizedTelemetry> readTelemetry(IntegrationAccount account, RemoteDevice device) { return notConfiguredTelemetry(); }
    @Override public ConnectorResult<List<RemoteAlarm>> readAlarms(IntegrationAccount account, RemoteDevice device, Instant from, Instant to) { return notConfiguredAlarms(); }
}
