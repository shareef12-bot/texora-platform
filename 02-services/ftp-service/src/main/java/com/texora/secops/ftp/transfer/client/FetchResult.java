package com.texora.secops.ftp.transfer.client;

import java.util.Objects;

public class FetchResult {

    private long bytes;

    private String sha256;

    private long resumedFromOffset;

    public FetchResult() {
    }

    public FetchResult(long bytes, String sha256, long resumedFromOffset) {
        this.bytes = bytes;
        this.sha256 = sha256;
        this.resumedFromOffset = resumedFromOffset;
    }

    public long getBytes() {
        return bytes;
    }

    public void setBytes(long bytes) {
        this.bytes = bytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public long getResumedFromOffset() {
        return resumedFromOffset;
    }

    public void setResumedFromOffset(long resumedFromOffset) {
        this.resumedFromOffset = resumedFromOffset;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FetchResult)) {
            return false;
        }
        FetchResult that = (FetchResult) other;
        return Objects.equals(bytes, that.bytes) && Objects.equals(sha256, that.sha256) && Objects.equals(resumedFromOffset, that.resumedFromOffset);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes, sha256, resumedFromOffset);
    }

    @Override
    public String toString() {
        return "FetchResult{" + "bytes=" + bytes + ", sha256=" + sha256 + ", resumedFromOffset=" + resumedFromOffset + "}";
    }
}
