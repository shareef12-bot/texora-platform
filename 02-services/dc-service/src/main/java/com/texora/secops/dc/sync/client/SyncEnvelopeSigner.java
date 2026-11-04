package com.texora.secops.dc.sync.client;

/**
 * Signs the canonical form of a directory-sync message so the LDAP consumer can reject forged/replayed/injected
 * changes (LLD 9.4, DC_TDD "Replication"). The signing KEY must come from SEC / the vault (B.6.6) - DC never
 * stores or configures key material. Only {@link LocalStubSyncEnvelopeSigner} exists today.
 */
public interface SyncEnvelopeSigner {

    String keyId();

    /** Returns a base64 signature over the canonical string. */
    String sign(String canonical);
}
