package com.texora.secops.starter.domain;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void set_and_get_returns_same_tenant() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        assertEquals(tenantId, TenantContext.get());
    }

    @Test
    void get_without_set_throws_TenantContextMissingException() {
        assertThrows(TenantContextMissingException.class, TenantContext::get);
    }

    @Test
    void set_null_throws_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> TenantContext.set(null));
    }

    @Test
    void clear_removes_tenant() {
        TenantContext.set(UUID.randomUUID());
        TenantContext.clear();
        assertFalse(TenantContext.isSet());
        assertThrows(TenantContextMissingException.class, TenantContext::get);
    }

    @Test
    void getOrNull_returns_null_when_not_set() {
        assertNull(TenantContext.getOrNull());
    }

    @Test
    void isSet_returns_true_after_set() {
        TenantContext.set(UUID.randomUUID());
        assertTrue(TenantContext.isSet());
    }
}
