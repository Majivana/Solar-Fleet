package za.co.solar.fleet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OperatorApiFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(OperatorApiFilter.class);
    private final boolean demoMode;
    private final String apiKey;
    private final String tenantId;

    public OperatorApiFilter(@Value("${platform.demo-data:false}") boolean demoMode,
                             @Value("${platform.operator-api-key:}") String apiKey,
                             @Value("${platform.tenant-id:}") String tenantId) {
        this.demoMode = demoMode;
        this.apiKey = apiKey;
        this.tenantId = tenantId;
        if (!demoMode && (apiKey.isBlank() || tenantId.isBlank())) {
            throw new IllegalStateException("Refusing to start without authentication. Provide OPERATOR_API_KEY and "
                    + "TENANT_ID, or opt in to the unauthenticated synthetic demo with APP_DEMO_DATA=true "
                    + "(SPRING_PROFILES_ACTIVE=demo).");
        }
        if (demoMode) {
            log.warn("DEMO MODE: the API is UNAUTHENTICATED and serves synthetic data. Do not expose this instance.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; " +
                        "connect-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'self'; frame-ancestors 'none'");
        boolean api = request.getRequestURI().startsWith("/api/");
        if (api) {
            response.setHeader("Cache-Control", "no-store");
        }
        if (demoMode || !api) {
            chain.doFilter(request, response);
            return;
        }
        byte[] expected = apiKey.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = request.getHeader("X-Operator-Key") == null
                ? new byte[0] : request.getHeader("X-Operator-Key").getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"Operator access key required.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
