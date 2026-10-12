package za.co.solar.fleet.integration.net;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * The one way vendor adapters should make outbound HTTP calls: bounded connect/read timeouts, redirects never
 * followed (so a validated host cannot bounce the request elsewhere) and every base URL passed through
 * {@link OutboundUrlValidator}.
 */
@Component
public class OutboundHttpClient {
    private final RestClient client;
    private final boolean allowHttp;
    private final boolean allowPrivateNetworks;

    public OutboundHttpClient(@Value("${platform.outbound.connect-timeout-ms:5000}") long connectTimeoutMs,
                              @Value("${platform.outbound.read-timeout-ms:15000}") long readTimeoutMs,
                              @Value("${platform.outbound.allow-http:false}") boolean allowHttp,
                              @Value("${platform.outbound.allow-private-networks:false}") boolean allowPrivateNetworks) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.client = RestClient.builder().requestFactory(factory).defaultHeader("User-Agent", "solar-fleet-mvp").build();
        this.allowHttp = allowHttp;
        this.allowPrivateNetworks = allowPrivateNetworks;
    }

    /** Validates {@code baseUrl} and returns a client that may be used to call it. */
    public RestClient forBaseUrl(String baseUrl) {
        URI uri = OutboundUrlValidator.validate(baseUrl, allowHttp, allowPrivateNetworks);
        return client.mutate().baseUrl(uri.toString()).build();
    }
}
