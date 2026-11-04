package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps dc.audit.events (the platform's directory/domain-controller service). */
@Component
public class DcAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "dc";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
