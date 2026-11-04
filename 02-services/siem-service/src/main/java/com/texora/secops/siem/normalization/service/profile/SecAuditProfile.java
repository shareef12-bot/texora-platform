package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps sec.audit.events (VaultKmsAdapter's Security Server - SIEM's own PolicyDecisionClient
 *  target is also this service, but ingestion of ITS audit trail is independent of that). */
@Component
public class SecAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "sec";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
