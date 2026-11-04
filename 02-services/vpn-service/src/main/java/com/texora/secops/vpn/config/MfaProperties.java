package com.texora.secops.vpn.config;

public class MfaProperties {

    private boolean stubEnabled = false;

    public MfaProperties() {
    }

    public boolean isStubEnabled() {
        return stubEnabled;
    }

    public void setStubEnabled(boolean stubEnabled) {
        this.stubEnabled = stubEnabled;
    }

}
