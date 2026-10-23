package com.texora.secops.dc.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Pattern;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = false)
public class SyncTriggerRequest {

    @Pattern(regexp = "LDAP_PUSH|HRMS_PULL")
    private String source;

    public SyncTriggerRequest() {
        // required by Jackson
    }

    public SyncTriggerRequest(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        SyncTriggerRequest that = (SyncTriggerRequest) other;
        return Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source);
    }

    @Override
    public String toString() {
        return "SyncTriggerRequest{source=" + source + "}";
    }
}
