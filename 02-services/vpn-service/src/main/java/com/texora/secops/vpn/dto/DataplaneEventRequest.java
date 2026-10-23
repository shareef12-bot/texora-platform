package com.texora.secops.vpn.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class DataplaneEventRequest {

    @NotBlank
    @Size(max = 255)
    private String dataplaneSessionRef;

    @NotBlank
    @Pattern(regexp = "TUNNEL_TERMINATED")
    private String event;

    public DataplaneEventRequest() {
    }

    public String getDataplaneSessionRef() {
        return dataplaneSessionRef;
    }

    public void setDataplaneSessionRef(String dataplaneSessionRef) {
        this.dataplaneSessionRef = dataplaneSessionRef;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    @Override
    public String toString() {
        return "DataplaneEventRequest{" + "dataplaneSessionRef=" + dataplaneSessionRef + ", event=" + event + "}";
    }

}
