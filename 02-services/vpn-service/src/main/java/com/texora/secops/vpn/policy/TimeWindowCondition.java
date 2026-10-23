package com.texora.secops.vpn.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimeWindowCondition {

    private String zone;

    private String startTime;

    private String endTime;

    private List<String> daysOfWeek;

    public TimeWindowCondition() {
    }

    public String getZone() {
        return zone;
    }

    public void setZone(String zone) {
        this.zone = zone;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public List<String> getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(List<String> daysOfWeek) {
        this.daysOfWeek = daysOfWeek;
    }

    @Override
    public String toString() {
        return "TimeWindowCondition{" + "zone=" + zone + ", startTime=" + startTime + ", endTime=" + endTime + ", daysOfWeek=" + daysOfWeek + "}";
    }

}
