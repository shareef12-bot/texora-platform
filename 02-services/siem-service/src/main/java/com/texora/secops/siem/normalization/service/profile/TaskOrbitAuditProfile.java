package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps taskorbit.audit.events. */
@Component
public class TaskOrbitAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "taskorbit";
    }

    @Override
    public String productId() {
        return "taskorbit";
    }
}
