package com.texora.secops.ldap.sync.consumer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * DC's sync message (topic dc.sync.requested). Unknown fields are ignored so DC can add
 * fields safely. Every field used for a decision is validated by DcSyncEventConsumer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DcSyncEnvelope {

    private Integer version;
    private String messageId;
    private UUID tenantId;
    private String entityType;
    private UUID entityId;
    private String operation;
    private String payload;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public UUID getEntityId() { return entityId; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
}
