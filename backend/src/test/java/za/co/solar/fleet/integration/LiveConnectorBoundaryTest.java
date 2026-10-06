package za.co.solar.fleet.integration;

import org.junit.jupiter.api.Test;
import za.co.solar.fleet.domain.Brand;
import za.co.solar.fleet.domain.IntegrationAccount;
import za.co.solar.fleet.domain.IntegrationMode;

import static org.junit.jupiter.api.Assertions.*;

class LiveConnectorBoundaryTest {
    @Test
    void liveProvidersNeverReportSuccessWithoutAnApprovedImplementation() {
        ConnectorRegistry registry = new ConnectorRegistry();
        for (Brand provider : Brand.values()) {
            IntegrationAccount account = new IntegrationAccount();
            account.brand = provider;
            account.mode = IntegrationMode.LIVE;

            ConnectorResult<?> result = registry.forAccount(account).discoverDevices(account);

            assertFalse(result.success(), provider + " must not claim an unconfigured live connection succeeded");
            assertNotNull(result.error());
            assertTrue(result.data() == null || ((java.util.List<?>) result.data()).isEmpty());
        }
    }

    @Test
    void ectRemainsExplicitlyUnverified() {
        IntegrationAccount account = new IntegrationAccount();
        account.brand = Brand.ECT_UNVERIFIED;
        account.mode = IntegrationMode.LIVE;

        ConnectorResult<?> result = new ConnectorRegistry().forAccount(account).discoverDevices(account);

        assertFalse(result.success());
        assertTrue(result.error().contains("ECT_UNVERIFIED"));
    }
}
