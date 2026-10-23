package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps vpn.audit.events (GatewayIntegrationAdapter's VPN dataplane service). */
@Component
public class VpnAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "vpn";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
