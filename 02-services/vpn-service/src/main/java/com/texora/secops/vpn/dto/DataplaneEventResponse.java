package com.texora.secops.vpn.dto;

public class DataplaneEventResponse {

    private boolean accepted;

    public DataplaneEventResponse() {
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    @Override
    public String toString() {
        return "DataplaneEventResponse{" + "accepted=" + accepted + "}";
    }

}
