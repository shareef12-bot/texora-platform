package com.texora.secops.dc.sync.client;

import java.util.Objects;

public class SyncEnvelope {

    private int version;

    private String messageId;

    private String tenantId;

    private String entityType;

    private String entityId;

    private String operation;

    private String issuedAt;

    private String nonce;

    private String payload;

    private String keyId;

    private String signature;

    public SyncEnvelope() {
        // required by Jackson
    }

    public SyncEnvelope(int version, String messageId, String tenantId, String entityType, String entityId, String operation, String issuedAt, String nonce, String payload, String keyId, String signature) {
        this.version = version;
        this.messageId = messageId;
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.operation = operation;
        this.issuedAt = issuedAt;
        this.nonce = nonce;
        this.payload = payload;
        this.keyId = keyId;
        this.signature = signature;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getIssuedAt() {
        return issuedAt;
    }

    public void setIssuedAt(String issuedAt) {
        this.issuedAt = issuedAt;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SyncEnvelope that = (SyncEnvelope) other;
        return Objects.equals(version, that.version) && Objects.equals(messageId, that.messageId) && Objects.equals(tenantId, that.tenantId) && Objects.equals(entityType, that.entityType) && Objects.equals(entityId, that.entityId) && Objects.equals(operation, that.operation) && Objects.equals(issuedAt, that.issuedAt) && Objects.equals(nonce, that.nonce) && Objects.equals(payload, that.payload) && Objects.equals(keyId, that.keyId) && Objects.equals(signature, that.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, messageId, tenantId, entityType, entityId, operation, issuedAt, nonce, payload, keyId, signature);
    }

    @Override
    public String toString() {
        return "SyncEnvelope{messageId=" + messageId + ", " + "entityType=" + entityType + ", " + "operation=" + operation + "}";
    }
}
