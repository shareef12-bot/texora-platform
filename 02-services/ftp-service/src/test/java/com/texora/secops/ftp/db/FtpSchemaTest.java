package com.texora.secops.ftp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real Flyway migrations on real PostgreSQL (Testcontainers; needs Docker - see KNOWN-INTEGRATION-PITFALLS #9).
 * Proves: audit immutability (trigger AND grants), checksum idempotency anchor, tenant integrity, tenant_id everywhere.
 */
@Testcontainers
class FtpSchemaTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String APP_USER = "ftp_app";
    private static final String APP_PASSWORD = "app-pw";

    @BeforeAll
    static void migrate() throws Exception {
        try (Connection c = admin(); Statement s = c.createStatement()) {
            s.execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
        }
        Flyway.configure()
                .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
                .schemas("ftp").defaultSchema("ftp").createSchemas(true)
                .placeholders(Map.of("app_role", APP_USER))
                .locations("classpath:db/migration")
                .load().migrate();
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    }

    private static Connection app() throws SQLException {
        return DriverManager.getConnection(pg.getJdbcUrl(), APP_USER, APP_PASSWORD);
    }

    private static UUID insertEndpoint(Connection c, UUID tenant, String direction) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ftp.ftp_endpoint (id, tenant_id, host, port, direction, protocol, "
                + "base_path, credential_ref, host_key_fingerprint, allowlisted, added_by, added_at) "
                + "VALUES (?, ?, ?, 22, ?, 'SFTP', '/', 'ref', 'SHA256:x', true, ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setString(3, "h-" + id + ".example.com");
            ps.setString(4, direction);
            ps.setObject(5, UUID.randomUUID());
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertJob(Connection c, UUID tenant, UUID src, UUID dst) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ftp.ftp_transfer_job (id, tenant_id, initiator_sso_subject, "
                + "source_endpoint_id, dest_endpoint_id, file_name, require_inspection, status, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, 'f.csv', true, 'SUBMITTED', ?, ?)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setObject(3, UUID.randomUUID());
            ps.setObject(4, src);
            ps.setObject(5, dst);
            ps.setTimestamp(6, Timestamp.from(Instant.now()));
            ps.setTimestamp(7, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertAudit(Connection c, UUID tenant) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ftp.ftp_audit_event (id, tenant_id, actor_id, action, "
                + "target_type, target_id, outcome, occurred_at, payload) VALUES (?, ?, 'actor', 'TEST', 'X', ?, 'SUCCESS', ?, '{}'::jsonb)")) {
            ps.setObject(1, id);
            ps.setObject(2, tenant);
            ps.setObject(3, UUID.randomUUID());
            ps.setTimestamp(4, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
        return id;
    }

    private static int count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void auditRowsCannotBeUpdatedDeletedOrTruncatedEvenByTheOwner() throws Exception {
        try (Connection c = admin()) {
            UUID id = insertAudit(c, UUID.randomUUID());
            for (String sql : new String[]{
                    "UPDATE ftp.ftp_audit_event SET outcome = 'TAMPERED' WHERE id = '" + id + "'",
                    "DELETE FROM ftp.ftp_audit_event WHERE id = '" + id + "'",
                    "TRUNCATE ftp.ftp_audit_event"}) {
                assertThatThrownBy(() -> {
                    try (Statement s = c.createStatement()) {
                        s.execute(sql);
                    }
                }).as(sql).isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            }
            assertThat(count(c, "SELECT count(*) FROM ftp.ftp_audit_event WHERE id = '" + id + "' AND outcome = 'SUCCESS'")).isEqualTo(1);
        }
    }

    @Test
    void theApplicationRoleCanInsertAndReadAuditRowsButHasNoUpdateOrDeletePrivilege() throws Exception {
        try (Connection c = app()) {
            UUID tenant = UUID.randomUUID();
            insertAudit(c, tenant);
            assertThat(count(c, "SELECT count(*) FROM ftp.ftp_audit_event WHERE tenant_id = '" + tenant + "'")).isEqualTo(1);
            for (String privilege : new String[]{"UPDATE", "DELETE", "TRUNCATE"}) {
                try (Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery("SELECT has_table_privilege('" + APP_USER + "', 'ftp.ftp_audit_event', '" + privilege + "')")) {
                    rs.next();
                    assertThat(rs.getBoolean(1)).as(privilege).isFalse();
                }
            }
            assertThatThrownBy(() -> {
                try (Statement s = c.createStatement()) {
                    s.execute("UPDATE ftp.ftp_audit_event SET outcome = 'X'");
                }
            }).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void theApplicationRoleCanNeverDeleteFromAnyTable() throws Exception {
        try (Connection c = admin(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM information_schema.role_table_grants WHERE grantee = '" + APP_USER
                     + "' AND table_schema = 'ftp' AND privilege_type IN ('DELETE', 'TRUNCATE')")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void checksumRecordIsUniquePerJobAndAlgorithmSoRetriesCannotDuplicateIt() throws Exception {
        try (Connection c = admin()) {
            UUID tenant = UUID.randomUUID();
            UUID job = insertJob(c, tenant, insertEndpoint(c, tenant, "SOURCE"), insertEndpoint(c, tenant, "DEST"));
            String insert = "INSERT INTO ftp.ftp_checksum_record (id, tenant_id, transfer_job_id, algorithm, expected_value, "
                    + "computed_value, verified, recorded_at) VALUES (gen_random_uuid(), '" + tenant + "', '" + job
                    + "', 'SHA-256', 'a', 'a', true, now()) ON CONFLICT (transfer_job_id, algorithm) DO NOTHING";
            try (Statement s = c.createStatement()) {
                assertThat(s.executeUpdate(insert)).isEqualTo(1);
                assertThat(s.executeUpdate(insert)).isZero();
                assertThat(s.executeUpdate(insert)).isZero();
            }
            assertThat(count(c, "SELECT count(*) FROM ftp.ftp_checksum_record WHERE transfer_job_id = '" + job + "'")).isEqualTo(1);
        }
    }

    @Test
    void aJobCannotReferenceAnotherTenantsEndpoint() throws Exception {
        try (Connection c = admin()) {
            UUID tenantA = UUID.randomUUID();
            UUID tenantB = UUID.randomUUID();
            UUID srcA = insertEndpoint(c, tenantA, "SOURCE");
            UUID dstB = insertEndpoint(c, tenantB, "DEST");
            assertThatThrownBy(() -> insertJob(c, tenantB, srcA, dstB))
                    .isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23503");
        }
    }

    @Test
    void attemptNumbersAreUniquePerJob() throws Exception {
        try (Connection c = admin()) {
            UUID tenant = UUID.randomUUID();
            UUID job = insertJob(c, tenant, insertEndpoint(c, tenant, "SOURCE"), insertEndpoint(c, tenant, "DEST"));
            String insert = "INSERT INTO ftp.ftp_transfer_attempt (id, tenant_id, transfer_job_id, attempt_number, phase, outcome, "
                    + "started_at) VALUES (gen_random_uuid(), '" + tenant + "', '" + job + "', 1, 'FETCH', 'SUCCESS', now())";
            try (Statement s = c.createStatement()) {
                s.executeUpdate(insert);
            }
            assertThatThrownBy(() -> {
                try (Statement s = c.createStatement()) {
                    s.executeUpdate(insert);
                }
            }).isInstanceOf(SQLException.class).extracting("SQLState").isEqualTo("23505");
        }
    }

    @Test
    void everyTableHasATenantIdNotNullColumn() throws Exception {
        try (Connection c = admin()) {
            int tables = count(c, "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'ftp' "
                    + "AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'");
            int withTenant = count(c, "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'ftp' "
                    + "AND column_name = 'tenant_id' AND is_nullable = 'NO' AND data_type = 'uuid' AND table_name <> 'flyway_schema_history'");
            assertThat(tables).isGreaterThanOrEqualTo(8);
            assertThat(withTenant).isEqualTo(tables);
        }
    }
}
