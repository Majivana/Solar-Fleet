package za.co.solar.fleet.integration;

import za.co.solar.fleet.domain.Brand;
import za.co.solar.fleet.domain.IntegrationAccount;
import java.time.Instant;
import java.util.List;

/** Production adapter boundary for SIGENERGY.
 * The endpoint/auth implementation must be populated only from approved OEM documentation/credentials.
 */
public class SigenergyConnector extends LiveApiConnectorBoundary {
    @Override public Brand brand() { return Brand.SIGENERGY; }
    @Override protected String vendorDocumentationUrl() { return "https://developer.sigencloud.com/"; }
    @Override public ConnectorResult<List<RemoteDevice>> discoverDevices(IntegrationAccount account) { return notConfigured(); }
    @Override public ConnectorResult<NormalizedTelemetry> readTelemetry(IntegrationAccount account, RemoteDevice device) { return notConfiguredTelemetry(); }
    @Override public ConnectorResult<List<RemoteAlarm>> readAlarms(IntegrationAccount account, RemoteDevice device, Instant from, Instant to) { return notConfiguredAlarms(); }
}
