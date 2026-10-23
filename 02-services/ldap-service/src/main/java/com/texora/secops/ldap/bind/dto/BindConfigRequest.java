package com.texora.secops.ldap.bind.dto;

public class BindConfigRequest {

    private String bindDn;
    private boolean tlsRequired = true;
    private String allowedSource;
    private String status;

    public BindConfigRequest() {
    }

    public String getBindDn() { return bindDn; }
    public void setBindDn(String bindDn) { this.bindDn = bindDn; }
    public boolean isTlsRequired() { return tlsRequired; }
    public void setTlsRequired(boolean tlsRequired) { this.tlsRequired = tlsRequired; }
    public String getAllowedSource() { return allowedSource; }
    public void setAllowedSource(String allowedSource) { this.allowedSource = allowedSource; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
