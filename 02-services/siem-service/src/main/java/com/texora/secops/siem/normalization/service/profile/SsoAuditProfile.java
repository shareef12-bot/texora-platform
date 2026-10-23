package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps sso.audit.events (the platform's SSO/IAM service - also the issuer SIEM itself trusts
 *  for bearer-token validation, see SecurityConfig). */
@Component
public class SsoAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "sso";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
