package com.texora.secops.siem.normalization.service;

import com.texora.secops.siem.normalization.model.NormalizedEvent;

/**
 * "Write one mapping profile per upstream source." One Spring bean per source key
 * (dc, ldap, sso, ftp, cgi, vpn, sec, ilmora, texora-jobs, taskorbit, hrms, crm), each isolating
 * the shape of that source's raw audit payload from every other profile and from the rest of
 * the pipeline. Adding a thirteenth source means adding one profile, not touching this
 * interface or EventNormalizationService.
 */
public interface NormalizationProfile {

    /** The ingestion source key this profile handles, e.g. "ldap" or "crm". Matched against the
     *  Kafka topic prefix / registered source name (see MultiProtocolIngestionAdapter). */
    String sourceKey();

    /** The product_id normalized events from this source carry, per the LLD's product_id enum:
     *  ilmora | texora-jobs | taskorbit | hrms | crm | &lt;infra&gt;. */
    String productId();

    NormalizedEvent normalize(RawEventContext context) throws NormalizationException;
}
