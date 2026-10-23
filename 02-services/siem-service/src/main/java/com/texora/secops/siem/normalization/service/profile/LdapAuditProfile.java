package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps ldap.audit.events (Texora SecOps LDAP service, LdapEngineAdapter's own audit trail). */
@Component
public class LdapAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "ldap";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
