package com.texora.secops.cgi.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cgi.security.* settings.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@ConfigurationProperties(prefix = "cgi.security")
public class SecurityProperties {

    private boolean exposeApiDocs = false;

    public SecurityProperties() {
        // default constructor (JSON / property binding)
    }

    public boolean isExposeApiDocs() {
        return exposeApiDocs;
    }

    public void setExposeApiDocs(boolean exposeApiDocs) {
        this.exposeApiDocs = exposeApiDocs;
    }

}
