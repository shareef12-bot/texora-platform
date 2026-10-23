package com.texora.secops.vpn.it;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** vpn_audit_event is append-only IN THE DATABASE - even the table owner (this test's connection) cannot change it. */
class AuditImmutabilityIntegrationTest extends VpnIntegrationTestSupport {

    @Test
    void updateDeleteAndTruncateAreRefused() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into vpn.vpn_audit_event (id, tenant_id, actor_id, action, target_type, target_id, outcome, occurred_at, payload) "
                + "values (?, ?, ?, 'X', 'T', '1', 'SUCCESS', now(), '{}'::jsonb)", id, UUID.randomUUID(), UUID.randomUUID());

        assertThatThrownBy(() -> jdbc.update("update vpn.vpn_audit_event set outcome = 'TAMPERED' where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from vpn.vpn_audit_event where id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("truncate vpn.vpn_audit_event"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");

        assertThat(jdbc.queryForObject("select outcome from vpn.vpn_audit_event where id = ?", String.class, id)).isEqualTo("SUCCESS");
    }

    @Test
    void noColumnAnywhereInTheSchemaCouldHoldACredential() {
        var columns = jdbc.queryForList("select table_name || '.' || column_name from information_schema.columns "
                + "where table_schema = 'vpn' and column_name ~* '(password|secret|private|credential|passwd|apikey|api_key)'", String.class);
        assertThat(columns).as("no secret-bearing columns in the vpn schema").isEmpty();
        assertThat(jdbc.queryForList("select table_name from information_schema.columns where table_schema = 'vpn' "
                + "and column_name = 'tenant_id'", String.class)).hasSize(
                jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = 'vpn' "
                        + "and table_name <> 'flyway_schema_history'", Integer.class));
    }
}
