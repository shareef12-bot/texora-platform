package com.texora.qap.defect.adapter;

import java.util.UUID;

/**
 * THE ONLY class permitted to talk to the external issue tracker (Jira / Azure DevOps /
 * GitHub Issues — vendor TBD per LLD §12).  No other class in the service may import
 * issue-tracker client libraries (Shared Standards B.4 / QAP Requirement 1).
 *
 * <p>The interface is designed so the vendor choice does NOT ripple into business logic.
 * Only this adapter's implementation changes once a vendor is selected.
 *
 * <p><strong>Part A note:</strong> The only live implementation for Part A is
 * {@link StubIssueTrackerAdapter} — a loud stub. A real vendor implementation
 * is deferred until vendor selection (LLD §12).
 */
public interface IssueTrackerAdapter {

    /**
     * Create a new defect ticket in the issue tracker.
     *
     * @param request  all details needed to file the ticket
     * @return         the external ticket reference (e.g. "QAP-1042")
     */
    String createTicket(CreateTicketRequest request);

    /**
     * Add a comment to an existing ticket, used when the same failure recurs
     * (occurrence_count increments; no new ticket is created).
     *
     * @param issueTrackerRef  external ticket reference
     * @param comment          comment body
     */
    void addComment(String issueTrackerRef, String comment);

    // -------------------------------------------------------------------------

    record CreateTicketRequest(
            UUID tenantId,
            String title,
            String description,
            String sourceService,
            String endpoint,
            int httpStatus,
            String exception,       // stack trace from correlation
            String screenshotRef,   // object-storage path, may be null for API tests
            String redactedRequest,
            String redactedResponse,
            String traceId
    ) {}
}
