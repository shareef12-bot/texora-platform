package com.texora.secops.cgi.config.props;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * cgi.redaction.* settings. Extra keys can only ADD to the built-in redaction rules.
 *
 * <p>Generated source, committed as plain Java: explicit accessors, plain Java only (Shared Standards B.2).
 */
@ConfigurationProperties(prefix = "cgi.redaction")
public class RedactionProperties {

    private List<String> extraSensitiveKeys = new ArrayList<>();

    public RedactionProperties() {
        // default constructor (JSON / property binding)
    }

    public List<String> getExtraSensitiveKeys() {
        return extraSensitiveKeys;
    }

    public void setExtraSensitiveKeys(List<String> extraSensitiveKeys) {
        this.extraSensitiveKeys = extraSensitiveKeys;
    }

}
