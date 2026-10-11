package za.co.solar.fleet.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class NetworkClientConfigTest {
    @Test
    void buildsRestClientWithTimeouts() {
        RestClient.Builder builder = new NetworkClientConfig().oemRestClientBuilder(2000, 5000);
        assertNotNull(builder);
        assertNotNull(builder.build());
    }
}
