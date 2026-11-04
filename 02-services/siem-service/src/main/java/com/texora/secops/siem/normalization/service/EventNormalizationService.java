package com.texora.secops.siem.normalization.service;

import com.texora.secops.siem.normalization.model.NormalizedEvent;
import com.texora.secops.siem.search.client.EventCorpusClient;
import com.texora.secops.siem.search.client.EventCorpusUnavailableException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pipeline step 3: map a raw event to the common schema via its source's
 * {@link NormalizationProfile}, then index it into the event corpus. One profile per source
 * (LLD "Write one mapping profile per upstream source") - adding a new upstream source means
 * registering one new {@link NormalizationProfile} bean, never editing this class.
 */
@Service
public class EventNormalizationService {

    private final Map<String, NormalizationProfile> profilesBySourceKey;
    private final EventCorpusClient eventCorpusClient;

    public EventNormalizationService(List<NormalizationProfile> profiles, EventCorpusClient eventCorpusClient) {
        this.profilesBySourceKey = profiles.stream()
                .collect(Collectors.toMap(NormalizationProfile::sourceKey, Function.identity()));
        this.eventCorpusClient = eventCorpusClient;
    }

    public NormalizedEvent normalizeAndIndex(String sourceKey, String rawPayload, Instant ingestedAt)
            throws NormalizationException, EventCorpusUnavailableException {
        NormalizationProfile profile = profilesBySourceKey.get(sourceKey);
        if (profile == null) {
            throw new NormalizationException("No normalization mapping profile registered for source '" + sourceKey + "'");
        }
        NormalizedEvent event = profile.normalize(new RawEventContext(sourceKey, rawPayload, ingestedAt));
        eventCorpusClient.index(event);
        return event;
    }
}
