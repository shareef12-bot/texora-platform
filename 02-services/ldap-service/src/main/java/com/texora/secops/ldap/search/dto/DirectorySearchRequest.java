package com.texora.secops.ldap.search.dto;

import java.util.List;

public class DirectorySearchRequest {

    private String baseDn;
    private String filter;
    private List<String> attributes;

    public DirectorySearchRequest() {
    }

    public String getBaseDn() { return baseDn; }
    public void setBaseDn(String baseDn) { this.baseDn = baseDn; }
    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }
    public List<String> getAttributes() { return attributes; }
    public void setAttributes(List<String> attributes) { this.attributes = attributes; }
}
