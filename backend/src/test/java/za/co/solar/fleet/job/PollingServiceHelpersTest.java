package za.co.solar.fleet.job;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PollingServiceHelpersTest {
    @Test
    void truncateKeepsShortValuesAndCapsLongOnes() {
        assertNull(PollingService.truncate(null, 5));
        assertEquals("abc", PollingService.truncate("abc", 5));
        assertEquals("abcde", PollingService.truncate("abcdefgh", 5));
    }

    @Test
    void fingerprintIsStableAndMatchesDatabaseMd5Format() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String a = PollingService.alarmFingerprint(id, "E1", "Over temperature");
        assertEquals(a, PollingService.alarmFingerprint(id, "E1", "Over temperature"));
        assertNotEquals(a, PollingService.alarmFingerprint(id, "E2", "Over temperature"));
        assertEquals(32, a.length());
        // md5('00000000-0000-0000-0000-000000000001:E1:Over temperature') as computed by PostgreSQL md5()
        assertTrue(a.matches("[0-9a-f]{32}"));
    }
}
