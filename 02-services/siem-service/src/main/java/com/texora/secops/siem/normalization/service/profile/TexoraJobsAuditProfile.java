package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps texora-jobs.audit.events. */
@Component
public class TexoraJobsAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "texora-jobs";
    }

    @Override
    public String productId() {
        return "texora-jobs";
    }
}
