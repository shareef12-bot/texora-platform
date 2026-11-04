package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps ilmora.audit.events - one of the five business products correlated for
 *  "all users of all products" monitoring (MULTI-PRODUCT REQUIREMENT). */
@Component
public class IlmoraAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "ilmora";
    }

    @Override
    public String productId() {
        return "ilmora";
    }
}
