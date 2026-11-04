package com.texora.secops.ftp.transfer.client;

import java.util.Objects;

public class DeliveryResult {

    private long bytes;

    public DeliveryResult() {
    }

    public DeliveryResult(long bytes) {
        this.bytes = bytes;
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DeliveryResult)) {
            return false;
        }
        DeliveryResult that = (DeliveryResult) other;
        return Objects.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes);
    }

    @Override
    public String toString() {
        return "DeliveryResult{" + "bytes=" + bytes + "}";
    }
}
