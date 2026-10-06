package za.co.solar.fleet.integration;

import za.co.solar.fleet.domain.Brand;
import za.co.solar.fleet.domain.IntegrationAccount;
import java.time.Instant;
import java.util.List;

/**
 * Boundary for vendor-specific production adapters.
 * Implement the exact official authentication/endpoints in a separate class per OEM.
 * Do not put undocumented credentials or guessed URLs into the core application.
 */
public abstract class LiveApiConnectorBoundary implements InverterConnector {
    protected abstract String vendorDocumentationUrl();
    protected String requireBaseUrl(IntegrationAccount account) {
        if (account.baseUrl == null || account.baseUrl.isBlank()) {
            throw new IllegalStateException("Missing configured base URL for " + brand());
        }
        return account.baseUrl;
    }
    protected ConnectorResult<List<RemoteDevice>> notConfigured() {
        return ConnectorResult.fail(brand()+" live adapter is not configured with approved OEM credentials/endpoints.");
    }
    protected ConnectorResult<NormalizedTelemetry> notConfiguredTelemetry() {
        return ConnectorResult.fail(brand()+" live telemetry adapter is not configured.");
    }
    protected ConnectorResult<List<RemoteAlarm>> notConfiguredAlarms() {
        return ConnectorResult.fail(brand()+" live alarm adapter is not configured.");
    }
}
