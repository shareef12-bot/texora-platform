package com.texora.secops.vpn.config;

public class SchedulingProperties {

    private boolean enabled = true;

    private int tickMs = 5000;

    private int watchdogMs = 15000;

    public SchedulingProperties() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getTickMs() {
        return tickMs;
    }

    public void setTickMs(int tickMs) {
        this.tickMs = tickMs;
    }

    public int getWatchdogMs() {
        return watchdogMs;
    }

    public void setWatchdogMs(int watchdogMs) {
        this.watchdogMs = watchdogMs;
    }

}
