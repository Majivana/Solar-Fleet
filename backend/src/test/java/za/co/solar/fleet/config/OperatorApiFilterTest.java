package za.co.solar.fleet.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class OperatorApiFilterTest {
    @Test
    void protectsApiRoutesOutsideDemoModeAndSetsSecurityHeaders() throws Exception {
        OperatorApiFilter filter = new OperatorApiFilter(false, "operator-key", "tenant-id");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean();

        filter.doFilter(request, response, chain(continued));
        assertEquals(401, response.getStatus());
        assertFalse(continued.get());
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"));
    }

    @Test
    void acceptsCorrectDeploymentKeyAndDoesNotRequireItForStaticPage() throws Exception {
        OperatorApiFilter filter = new OperatorApiFilter(false, "operator-key", "tenant-id");
        MockHttpServletRequest apiRequest = new MockHttpServletRequest("GET", "/api/v1/projects");
        apiRequest.addHeader("X-Operator-Key", "operator-key");
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        AtomicBoolean apiContinued = new AtomicBoolean();
        filter.doFilter(apiRequest, apiResponse, chain(apiContinued));
        assertTrue(apiContinued.get());
        assertEquals(200, apiResponse.getStatus());

        MockHttpServletRequest pageRequest = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse pageResponse = new MockHttpServletResponse();
        AtomicBoolean pageContinued = new AtomicBoolean();
        filter.doFilter(pageRequest, pageResponse, chain(pageContinued));
        assertTrue(pageContinued.get());
        assertNotNull(pageResponse.getHeader("Content-Security-Policy"));
    }

    @Test
    void refusesNonDemoConfigurationWithoutTenantAndKey() {
        assertThrows(IllegalStateException.class, () -> new OperatorApiFilter(false, "", ""));
    }

    private FilterChain chain(AtomicBoolean continued) {
        return (request, response) -> continued.set(true);
    }
}
