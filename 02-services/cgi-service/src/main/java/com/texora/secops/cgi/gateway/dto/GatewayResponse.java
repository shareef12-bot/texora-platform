package com.texora.secops.cgi.gateway.dto;

/** What the gateway returns after a COMPLETED execution. Never logged. */
public final class GatewayResponse {

    private final int status;
    private final String contentType;
    private final byte[] body;

    public GatewayResponse(int status, String contentType, byte[] body) {
        this.status = status;
        this.contentType = contentType;
        this.body = body == null ? new byte[0] : body;
    }

    public int getStatus() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "GatewayResponse{status=" + status + ", bodyBytes=" + body.length + "}";
    }
}
