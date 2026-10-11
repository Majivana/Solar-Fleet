package za.co.solar.fleet.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Shared HTTP client for future OEM live adapters.
 * Timeouts keep outbound network calls from blocking polling indefinitely.
 */
@Configuration
public class NetworkClientConfig {

    @Bean
    RestClient.Builder oemRestClientBuilder(
            @Value("${platform.network.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${platform.network.read-timeout-ms:15000}") long readTimeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return RestClient.builder().requestFactory(requestFactory);
    }
}
