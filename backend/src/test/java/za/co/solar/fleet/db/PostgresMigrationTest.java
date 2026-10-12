package za.co.solar.fleet.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Applies every Flyway migration to a real PostgreSQL in a throw-away schema, including the upgrade path where
 * duplicate telemetry already exists before V7. Runs only when IT_DB_URL (plus IT_DB_USER / IT_DB_PASSWORD) is set;
 * CI provides a postgres service, locally use: IT_DB_URL=jdbc:postgresql://localhost:5432/solarfleet ./mvnw test
 */
@EnabledIfEnvironmentVariable(named = "IT_DB_URL", matches = ".+")
class PostgresMigrationTest {
    @Test
    void migratesFreshAndUpgradesDatabaseWithDuplicateTelemetry() throws Exception {
        String url = System.getenv("IT_DB_URL");
        String user = System.getenv().getOrDefault("IT_DB_USER", "solarfleet");
        String password = System.getenv().getOrDefault("IT_DB_PASSWORD", "solarfleet");
        String schema = "it_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection c = DriverManager.getConnection(url, user, password); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA " + schema);
            try {
                Flyway upToV6 = Flyway.configure().dataSource(url, user, password).schemas(schema)
                        .defaultSchema(schema).target("6").load();
                upToV6.migrate();

                s.execute("SET search_path TO " + schema);
                s.execute("INSERT INTO tenants (id, name, created_at) VALUES ('00000000-0000-0000-0000-000000000001','t',now())");
                s.execute("INSERT INTO sites (id, tenant_id, name, timezone, created_at) VALUES "
                        + "('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','s','UTC',now())");
                s.execute("INSERT INTO integration_accounts (id, tenant_id, brand, mode, name, created_at) VALUES "
                        + "('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000001','SOLIS','DEMO','a',now())");
                s.execute("INSERT INTO devices (id, site_id, tenant_id, integration_account_id, brand, model, serial_number, status, created_at) VALUES "
                        + "('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001',"
                        + "'00000000-0000-0000-0000-000000000003','SOLIS','m','sn','ONLINE',now())");
                for (int i = 0; i < 3; i++) {
                    s.execute("INSERT INTO telemetry (id, device_id, timestamp) VALUES (gen_random_uuid(),"
                            + "'00000000-0000-0000-0000-000000000004','2026-01-01T00:00:00Z')");
                }

                Flyway.configure().dataSource(url, user, password).schemas(schema).defaultSchema(schema).load().migrate();

                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM telemetry")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1), "duplicates must be collapsed by V7");
                }
                try (ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_indexes WHERE schemaname = '" + schema
                        + "' AND indexname = 'uq_telemetry_device_timestamp'")) {
                    rs.next();
                    assertEquals(1, rs.getInt(1));
                }
            } finally {
                s.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }
}
