package com.texora.secops.ldap.search.dto;

import java.util.List;
import java.util.Map;

public class DirectorySearchResponse {

    private String baseDn;
    private String filter;
    private int count;
    private List<Entry> entries;

    public DirectorySearchResponse() {
    }

    public String getBaseDn() { return baseDn; }
    public void setBaseDn(String baseDn) { this.baseDn = baseDn; }
    public String getFilter() { return filter; }
    public void setFilter(String filter) { this.filter = filter; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public List<Entry> getEntries() { return entries; }
    public void setEntries(List<Entry> entries) { this.entries = entries; }

    public static class Entry {
        private String dn;
        private Map<String, List<String>> attributes;

        public Entry() {
        }

        public String getDn() { return dn; }
        public void setDn(String dn) { this.dn = dn; }
        public Map<String, List<String>> getAttributes() { return attributes; }
        public void setAttributes(Map<String, List<String>> attributes) { this.attributes = attributes; }
    }
}
