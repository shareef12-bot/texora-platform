package com.texora.qap.defect.service;

import com.texora.qap.defect.adapter.IssueTrackerAdapter;
import com.texora.qap.domain.QapDefect;
import com.texora.qap.domain.QapTestResult;
import com.texora.qap.flaky.FlakyTestDetectionService;
import com.texora.qap.repository.QapDefectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implements the failure → deduplicated defect ticket flow.
 *
 * <p>Key invariant: the same root cause (same endpoint + error type + service)
 * must NEVER produce more than one open ticket per tenant.  This is enforced by:
 * <ol>
 *   <li>Computing a SHA-256 {@code failure_signature_hash} from (endpoint + errorType + service)</li>
 *   <li>Looking up an existing {@link QapDefect} with that hash + tenant before filing</li>
 *   <li>On a repeat: incrementing {@code occurrence_count} and adding a comment; no new ticket</li>
 *   <li>The database UNIQUE constraint on (failure_signature_hash, tenant_id) as a safety net</li>
 * </ol>
 *
 * <p>A flaky test will NOT trigger defect filing — it records the occurrence but skips
 * the issue tracker entirely (QAP Requirement 2 step 6).
 */
@Service
public class DefectFilingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefectFilingService.class);

    private final QapDefectRepository defectRepository;
    private final IssueTrackerAdapter issueTrackerAdapter;
    private final FlakyTestDetectionService flakyTestDetectionService;

    public DefectFilingService(QapDefectRepository defectRepository,
                                IssueTrackerAdapter issueTrackerAdapter,
                                FlakyTestDetectionService flakyTestDetectionService) {
        this.defectRepository = defectRepository;
        this.issueTrackerAdapter = issueTrackerAdapter;
        this.flakyTestDetectionService = flakyTestDetectionService;
    }

    /**
     * File or update a defect based on a failed test result.
     *
     * <p>Algorithm:
     * <ol>
     *   <li>Check flakiness — quarantined test → record occurrence, skip tracker</li>
     *   <li>Compute failure_signature_hash</li>
     *   <li>Look up existing defect with same hash + tenant</li>
     *   <li>Existing → increment occurrence_count, update last_seen_at, add comment</li>
     *   <li>New → file ticket, create QapDefect row</li>
     * </ol>
     *
     * @param result    the failed test result (must have outcome FAIL or FLAKY)
     * @param tenantId  the tenant context
     * @return          the (created or updated) defect record, or empty if quarantined
     */
    @Transactional
    public Optional<QapDefect> processFailure(QapTestResult result, UUID tenantId) {
        UUID testCaseId = result.getTestCaseId();

        // Step 1 — Flaky check: quarantined tests do NOT generate tickets
        if (flakyTestDetectionService.isQuarantined(testCaseId, tenantId)) {
            LOGGER.info("Test case {} is quarantined — recording occurrence but NOT filing a defect", testCaseId);
            flakyTestDetectionService.recordOutcome(testCaseId, tenantId, false);
            return Optional.empty();
        }

        // Update flake-rate tracking with this failure
        flakyTestDetectionService.recordOutcome(testCaseId, tenantId, false);

        // Step 2 — Compute hash
        String hash = computeFailureSignatureHash(
                result.getCorrelatedService(),
                result.getCorrelatedEndpoint(),
                result.getCorrelatedExceptionType()
        );

        // Step 3 — Deduplicate lookup
        Optional<QapDefect> existingDefect =
                defectRepository.findByFailureSignatureHashAndTenantId(hash, tenantId);

        if (existingDefect.isPresent()) {
            // Step 4 — Repeat occurrence: update existing, add comment
            QapDefect defect = existingDefect.get();
            defect.setOccurrenceCount(defect.getOccurrenceCount() + 1);
            defect.setLastSeenAt(Instant.now());
            defect.setTestCaseId(testCaseId);
            defect.setTestResultId(result.getId()); // point to most recent failure

            String comment = buildCommentText(result, defect.getOccurrenceCount());
            issueTrackerAdapter.addComment(defect.getIssueTrackerRef(), comment);

            LOGGER.info("Defect {} (hash={}) occurrence #{} recorded; updated ticket {}",
                    defect.getId(), hash, defect.getOccurrenceCount(), defect.getIssueTrackerRef());

            return Optional.of(defectRepository.save(defect));

        } else {
            // Step 5 — New failure: file ticket and create defect row
            IssueTrackerAdapter.CreateTicketRequest request = new IssueTrackerAdapter.CreateTicketRequest(
                    tenantId,
                    buildTicketTitle(result),
                    buildTicketDescription(result),
                    result.getCorrelatedService(),
                    result.getCorrelatedEndpoint(),
                    result.getCorrelatedHttpStatus() != null ? result.getCorrelatedHttpStatus() : 0,
                    result.getCorrelatedExceptionType(),
                    result.getScreenshotRef(),
                    result.getRedactedRequest(),
                    result.getRedactedResponse(),
                    result.getTraceId()
            );

            String ticketRef = issueTrackerAdapter.createTicket(request);

            QapDefect newDefect = new QapDefect(
                    UUID.randomUUID(), result.getId(), tenantId,
                    hash, "OPEN", Instant.now(), Instant.now(), 1
            );
            newDefect.setIssueTrackerRef(ticketRef);
            newDefect.setTestCaseId(testCaseId);

            LOGGER.info("New defect filed: ticket={} hash={} testResult={}",
                    ticketRef, hash, result.getId());

            return Optional.of(defectRepository.save(newDefect));
        }
    }

    /**
     * Compute the failure signature hash from (endpoint + errorType + service).
     * SHA-256 over the canonical string "{service}|{endpoint}|{exceptionType}".
     *
     * <p>Null fields are normalised to empty string so that a result with partial
     * correlation data still hashes consistently.
     */
    public String computeFailureSignatureHash(String service, String endpoint, String exception) {
        String canonical = normalise(service) + "|" + normalise(endpoint) + "|" + extractType(exception);
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is always present in a JDK — this branch should never execute
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private String normalise(String s) {
        return s == null ? "" : s.trim();
    }

    /** Extract the exception class name from the full exception/stack-trace string. */
    private String extractType(String exception) {
        if (exception == null || exception.isBlank()) return "";
        int colon = exception.indexOf(':');
        int newline = exception.indexOf('\n');
        int end = colon >= 0 ? colon : (newline >= 0 ? newline : exception.length());
        return exception.substring(0, Math.min(end, 200)).trim();
    }

    private String buildTicketTitle(QapTestResult result) {
        String svc = result.getCorrelatedService() != null ? result.getCorrelatedService() : "unknown-service";
        String ep  = result.getCorrelatedEndpoint() != null ? result.getCorrelatedEndpoint() : "unknown-endpoint";
        int    st  = result.getCorrelatedHttpStatus() != null ? result.getCorrelatedHttpStatus() : 0;
        return String.format("[QAP] %s %s returned %d", svc, ep, st);
    }

    private String buildTicketDescription(QapTestResult result) {
        return "Automated test failure detected by QAP.\n\n" +
               "Test result ID: " + result.getId() + "\n" +
               "Trace ID: " + result.getTraceId() + "\n" +
               "Service: " + result.getCorrelatedService() + "\n" +
               "Endpoint: " + result.getCorrelatedEndpoint() + "\n" +
               "HTTP status: " + result.getCorrelatedHttpStatus() + "\n" +
               "Exception: " + result.getCorrelatedExceptionType();
    }

    private String buildCommentText(QapTestResult result, int occurrenceCount) {
        return "Recurrence #" + occurrenceCount + " — test result " + result.getId()
               + " at " + Instant.now()
               + "\nTrace: " + result.getTraceId();
    }

    // ---- Query methods -------------------------------------------------

    public List<QapDefect> listByTenant(UUID tenantId) {
        return defectRepository.findAllByTenantId(tenantId);
    }

    public Optional<QapDefect> findByIdAndTenant(UUID id, UUID tenantId) {
        return defectRepository.findByIdAndTenantId(id, tenantId);
    }
}
