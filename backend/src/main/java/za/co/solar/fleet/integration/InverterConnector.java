package za.co.solar.fleet.integration;

import za.co.solar.fleet.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface InverterConnector {
    Brand brand();
    ConnectorResult<List<RemoteDevice>> discoverDevices(IntegrationAccount account);
    ConnectorResult<NormalizedTelemetry> readTelemetry(IntegrationAccount account, RemoteDevice device);
    ConnectorResult<List<RemoteAlarm>> readAlarms(IntegrationAccount account, RemoteDevice device, Instant from, Instant to);
    default ConnectorCapabilities capabilities() { return ConnectorCapabilities.readOnly(); }
}
