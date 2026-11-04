package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps cgi.audit.events (WorkerIsolationAdapter's process/container spawn+kill service). */
@Component
public class CgiAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "cgi";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
