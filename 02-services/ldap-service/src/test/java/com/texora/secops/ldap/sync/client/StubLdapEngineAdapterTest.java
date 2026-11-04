package com.texora.secops.ldap.sync.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the stub adapter behaves safely:
 * - ping returns false (engine absent)
 * - search returns empty list (no data leaked)
 * - mutating methods are no-ops (no state changed)
 * - filter/syntax validation returns true (to allow workflow testing)
 */
@DisplayName("StubLdapEngineAdapter")
class StubLdapEngineAdapterTest {

    private final StubLdapEngineAdapter stub = new StubLdapEngineAdapter();

    @Test
    @DisplayName("ping: returns false — engine not present")
    void ping_returnsFalse() {
        assertThat(stub.ping()).isFalse();
    }

    @Test
    @DisplayName("search: returns empty list — no directory data")
    void search_returnsEmptyList() {
        List<Map<String, List<String>>> result = stub.search(
                "dc=example,dc=com", "(objectClass=*)", List.of("uid", "cn"));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("isValidFilter: returns true — workflow unblocked in stub mode")
    void isValidFilter_returnsTrue() {
        assertThat(stub.isValidFilter("(objectClass=*)")).isTrue();
        assertThat(stub.isValidFilter("(uid=jdoe)")).isTrue();
    }

    @Test
    @DisplayName("isValidSyntax: returns true — schema workflow unblocked in stub mode")
    void isValidSyntax_returnsTrue() {
        assertThat(stub.isValidSyntax("1.3.6.1.4.1.1466.115.121.1.26")).isTrue();
    }

    @Test
    @DisplayName("addOrReplace: is a no-op — no exception thrown")
    void addOrReplace_noOp() {
        stub.addOrReplace("cn=test,dc=example,dc=com", Map.of("cn", List.of("test")));
        // No exception = pass
    }

    @Test
    @DisplayName("delete: is a no-op — no exception thrown")
    void delete_noOp() {
        stub.delete("cn=test,dc=example,dc=com");
    }

    @Test
    @DisplayName("applySchemaChange: is a no-op — no exception thrown")
    void applySchemaChange_noOp() {
        stub.applySchemaChange("texoraUser", Map.of("uid", "1.3.6"), List.of("uid"));
    }
}
