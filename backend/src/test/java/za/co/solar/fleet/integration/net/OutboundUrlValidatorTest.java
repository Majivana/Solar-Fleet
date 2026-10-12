package za.co.solar.fleet.integration.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutboundUrlValidatorTest {
    @Test
    void acceptsPublicHttpsLiteralAddress() {
        assertEquals("https://8.8.8.8/api", OutboundUrlValidator.validate("https://8.8.8.8/api", false, false).toString());
    }

    @Test
    void rejectsPlainHttpUnlessAllowed() {
        assertThrows(IllegalArgumentException.class, () -> OutboundUrlValidator.validate("http://8.8.8.8", false, false));
        assertDoesNotThrow(() -> OutboundUrlValidator.validate("http://8.8.8.8", true, false));
    }

    @Test
    void rejectsLoopbackPrivateLinkLocalAndMetadataTargets() {
        for (String url : new String[]{"https://127.0.0.1", "https://10.1.2.3", "https://192.168.0.5",
                "https://172.16.9.9", "https://169.254.169.254/latest/meta-data", "https://100.64.0.1",
                "https://[::1]/", "https://[fd00::1]/", "https://0.0.0.0"}) {
            assertThrows(IllegalArgumentException.class, () -> OutboundUrlValidator.validate(url, false, false), url);
        }
    }

    @Test
    void privateTargetsCanBeAllowedExplicitly() {
        assertDoesNotThrow(() -> OutboundUrlValidator.validate("https://10.1.2.3", false, true));
    }

    @Test
    void rejectsMalformedOrCredentialBearingUrls() {
        for (String url : new String[]{null, "", "   ", "ftp://8.8.8.8", "https://", "not a url",
                "https://user:pw@8.8.8.8", "https://8.8.8.8:0"}) {
            assertThrows(IllegalArgumentException.class, () -> OutboundUrlValidator.validate(url, false, false), String.valueOf(url));
        }
    }
}
