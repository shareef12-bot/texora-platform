package com.texora.secops.ftp.quarantine.client;

import com.texora.secops.ftp.domain.InspectionOutcome;
import java.util.Objects;

public class InspectionVerdict {

    private InspectionOutcome outcome;

    private String service;

    private String detail;

    public InspectionVerdict() {
    }

    public InspectionVerdict(InspectionOutcome outcome, String service, String detail) {
        this.outcome = outcome;
        this.service = service;
        this.detail = detail;
    }

    public InspectionOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(InspectionOutcome outcome) {
        this.outcome = outcome;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InspectionVerdict)) {
            return false;
        }
        InspectionVerdict that = (InspectionVerdict) other;
        return Objects.equals(outcome, that.outcome) && Objects.equals(service, that.service) && Objects.equals(detail, that.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcome, service, detail);
    }

    @Override
    public String toString() {
        return "InspectionVerdict{" + "outcome=" + outcome + ", service=" + service + ", detail=" + detail + "}";
    }
}
