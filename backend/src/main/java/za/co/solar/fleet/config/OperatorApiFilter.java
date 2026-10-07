package za.co.solar.fleet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class OperatorApiFilter extends OncePerRequestFilter {
    private final boolean demoMode;
    private final String apiKey;
    private final String tenantId;

    public OperatorApiFilter(@Value("${platform.demo-data:true}") boolean demoMode,
                             @Value("${platform.operator-api-key:}") String apiKey,
                             @Value("${platform.tenant-id:}") String tenantId) {
        this.demoMode = demoMode;
        this.apiKey = apiKey;
        this.tenantId = tenantId;
        if (!demoMode && (apiKey.isBlank() || tenantId.isBlank())) {
            throw new IllegalStateException("APP_DEMO_DATA=false requires OPERATOR_API_KEY and TENANT_ID.");
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
        if (demoMode || !request.getRequestURI().startsWith("/api/")) {
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
