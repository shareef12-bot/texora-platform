package com.texora.qap.defect.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * LOCAL STUB — NOT PRODUCTION CODE.
 *
 * <p>This implementation of {@link IssueTrackerAdapter} is active until a real
 * issue-tracker vendor is selected (LLD §12 open item).  It does NOT connect
 * to Jira, Azure DevOps, GitHub Issues, or any other tracker.
 *
 * <p>Every call logs a loud warning. {@link #createTicket} returns a synthetic
 * reference so that the rest of the defect-filing pipeline (deduplication,
 * occurrence_count tracking) can be verified end-to-end without a real tracker.
 *
 * <p><strong>DO NOT promote this class to production use. It is a test stub only.</strong>
 */
@Component
public class StubIssueTrackerAdapter implements IssueTrackerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(StubIssueTrackerAdapter.class);

    private final AtomicInteger counter = new AtomicInteger(1000);

    /**
     * {@inheritDoc}
     *
     * <p><strong>STUB:</strong> Returns a synthetic "STUB-xxxx" reference.
     * No real ticket is created anywhere.
     */
    @Override
    public String createTicket(CreateTicketRequest request) {
        String ref = "STUB-" + counter.getAndIncrement();
        LOGGER.warn(
            "⚠️  STUB IssueTrackerAdapter: createTicket() called for tenant={}, endpoint={}, " +
            "httpStatus={} — NO real issue tracker is connected. Returning synthetic ref={}. " +
            "Replace this stub once the issue tracker vendor is selected (LLD §12).",
            request.tenantId(), request.endpoint(), request.httpStatus(), ref
        );
        return ref;
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>STUB:</strong> Logs and discards; no real comment is posted.
     */
    @Override
    public void addComment(String issueTrackerRef, String comment) {
        LOGGER.warn(
            "⚠️  STUB IssueTrackerAdapter: addComment() called for ref={} — " +
            "NO real comment has been posted. Replace this stub with a real vendor implementation.",
            issueTrackerRef
        );
    }
}
