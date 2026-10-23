package com.texora.secops.siem.normalization.service.profile;

import com.texora.secops.siem.normalization.service.AbstractSharedSdkNormalizationProfile;
import org.springframework.stereotype.Component;

/** Maps ftp.audit.events (SecureTransferEngineAdapter's SFTP/FTPS service). */
@Component
public class FtpAuditProfile extends AbstractSharedSdkNormalizationProfile {

    @Override
    public String sourceKey() {
        return "ftp";
    }

    @Override
    public String productId() {
        return "<infra>";
    }
}
