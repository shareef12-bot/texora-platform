package com.texora.secops.siem.search.client;

import com.texora.secops.siem.normalization.model.NormalizedEvent;

import java.util.List;
import java.util.UUID;

/**
 * The only contract the rest of the service knows about the event-corpus store. The concrete
 * implementation ({@code EventCorpusSearchAdapter}) is the single class that imports an
 * OpenSearch/Elasticsearch-compatible HTTP client (B.4) - swapping the backing product never
 * touches {@code EventNormalizationService} or {@code TenantFilteredSearchService}.
 */
public interface EventCorpusClient {

    void index(NormalizedEvent event) throws EventCorpusUnavailableException;

    /** @throws EventCorpusUnavailableException when the backend cannot be reached at all -
     *  callers must degrade gracefully rather than surface this as a hard failure
     *  (Requirement 3 non-functional). */
    EventCorpusSearchResult search(UUID tenantScope, String eventType, String sourceService,
                                    String productId, int page, int pageSize) throws EventCorpusUnavailableException;

    final class EventCorpusSearchResult {
        private final List<NormalizedEvent> events;
        private final long totalHits;

        public EventCorpusSearchResult(List<NormalizedEvent> events, long totalHits) {
            this.events = events;
            this.totalHits = totalHits;
        }

        public List<NormalizedEvent> getEvents() {
            return events;
        }

        public long getTotalHits() {
            return totalHits;
        }
    }
}
