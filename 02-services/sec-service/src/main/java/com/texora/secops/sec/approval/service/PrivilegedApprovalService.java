package com.texora.secops.sec.approval.service;

import com.texora.secops.sec.approval.domain.SecApprovalDecision;
import com.texora.secops.sec.approval.domain.SecPrivilegedRequest;
import com.texora.secops.sec.approval.repository.SecApprovalDecisionRepository;
import com.texora.secops.sec.approval.repository.SecPrivilegedRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Reusable dual-control approval primitive (SEC-F-009).
 *
 * <p>Generalises the two-person approval implemented separately in DC, LDAP,
 * CGI, SIEM, and VPN into one service-layer primitive.</p>
 *
 * <p>CRITICAL INVARIANTS (enforced in code, not documentation):</p>
 * <ul>
 *   <li>Requester CANNOT be an approver — self-approval is rejected with 403.</li>
 *   <li>Requests that are neither approved nor rejected within the configured
 *       window transition to EXPIRED rather than remaining actionable indefinitely.</li>
 *   <li>Every transition is written to the audit chain (via AuditingAspect).</li>
 * </ul>
 */
@Service
public class PrivilegedApprovalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PrivilegedApprovalService.class);

    // Default N-of-M threshold: 1 approver required (configurable per request_type if needed)
    private static final long DEFAULT_APPROVAL_THRESHOLD = 1L;

    @Value("${sec.approval.expiry-hours:48}")
    private int approvalExpiryHours;

    private final SecPrivilegedRequestRepository requestRepository;
    private final SecApprovalDecisionRepository decisionRepository;

    public PrivilegedApprovalService(SecPrivilegedRequestRepository requestRepository,
                                      SecApprovalDecisionRepository decisionRepository) {
        this.requestRepository = requestRepository;
        this.decisionRepository = decisionRepository;
    }

    @Transactional(readOnly = true)
    public Page<SecPrivilegedRequest> listRequests(UUID tenantId, Pageable pageable) {
        return requestRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public SecPrivilegedRequest getRequest(UUID tenantId, UUID requestId) {
        return requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Privileged request not found: " + requestId));
    }

    /**
     * Submit a new privileged request. Justification is mandatory.
     */
    @Transactional
    public SecPrivilegedRequest submitRequest(UUID tenantId, String requestType,
                                               String targetRef, UUID requestedBy,
                                               String justification) {
        if (justification == null || justification.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Justification is mandatory for privileged requests");
        }

        Instant expiresAt = Instant.now().plus(approvalExpiryHours, ChronoUnit.HOURS);
        SecPrivilegedRequest request = new SecPrivilegedRequest(
                UUID.randomUUID(), tenantId, requestType, targetRef,
                requestedBy, justification, expiresAt);

        SecPrivilegedRequest saved = requestRepository.save(request);
        LOGGER.info("Privileged request submitted id={} type={} requestedBy={}",
                saved.getId(), requestType, requestedBy);
        return saved;
    }

    /**
     * Record an approval or rejection decision.
     *
     * <p>SEPARATION OF DUTIES: approver_id == requested_by is rejected with 403.
     * This check is in CODE, not documentation.</p>
     */
    @Transactional
    public SecApprovalDecision recordDecision(UUID tenantId, UUID requestId,
                                               UUID approverId, String decision,
                                               String approverRole, String comments) {
        SecPrivilegedRequest request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Privileged request not found: " + requestId));

        // Verify request is still actionable
        if (!SecPrivilegedRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Request is not in PENDING state, current status: " + request.getStatus());
        }

        // Verify not expired
        if (Instant.now().isAfter(request.getExpiresAt())) {
            request.setStatus(SecPrivilegedRequest.STATUS_EXPIRED);
            request.setResolvedAt(Instant.now());
            requestRepository.save(request);
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Privileged request has expired");
        }

        // ===================================================================
        // CRITICAL: Separation of duties — requester cannot be the approver.
        // This is enforced in service code, never in documentation only.
        // ===================================================================
        if (approverId.equals(request.getRequestedBy())) {
            LOGGER.warn("SELF-APPROVAL REJECTED: requestId={} approverId={} requestedBy={}",
                    requestId, approverId, request.getRequestedBy());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Separation of duties violation: approver cannot be the same as the requester");
        }

        // Prevent duplicate decision from the same approver
        decisionRepository.findByPrivilegedRequestIdAndApproverId(requestId, approverId)
                .ifPresent(existing -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Approver has already submitted a decision for this request");
                });

        SecApprovalDecision approvalDecision = new SecApprovalDecision(
                UUID.randomUUID(), tenantId, requestId, approverId,
                decision, approverRole, comments);
        SecApprovalDecision saved = decisionRepository.save(approvalDecision);

        LOGGER.info("Approval decision recorded: requestId={} approverId={} decision={}",
                requestId, approverId, decision);

        // Check if threshold has been reached
        if ("APPROVED".equals(decision)) {
            long approvalCount = decisionRepository.countByPrivilegedRequestIdAndDecision(
                    requestId, "APPROVED");
            if (approvalCount >= DEFAULT_APPROVAL_THRESHOLD) {
                request.setStatus(SecPrivilegedRequest.STATUS_APPROVED);
                request.setResolvedAt(Instant.now());
                requestRepository.save(request);
                LOGGER.info("Privileged request APPROVED: requestId={} approvalCount={}",
                        requestId, approvalCount);
            }
        } else if ("REJECTED".equals(decision)) {
            request.setStatus(SecPrivilegedRequest.STATUS_REJECTED);
            request.setResolvedAt(Instant.now());
            requestRepository.save(request);
            LOGGER.info("Privileged request REJECTED: requestId={}", requestId);
        }

        return saved;
    }

    /**
     * Check if a privileged operation has been approved (used by other services
     * before allowing a critical operation to proceed).
     */
    @Transactional(readOnly = true)
    public boolean isApproved(UUID tenantId, String requestType, String targetRef) {
        return requestRepository.findLatestApproved(tenantId, requestType, targetRef).isPresent();
    }

    /**
     * Scheduled job to expire PENDING requests past their expiry window.
     * Runs every 5 minutes. Requests must NEVER remain indefinitely actionable.
     */
    @Scheduled(fixedDelay = 300_000L)
    @Transactional
    public void expireStaleRequests() {
        List<SecPrivilegedRequest> expired = requestRepository
                .findByStatusAndExpiresAtBefore(SecPrivilegedRequest.STATUS_PENDING, Instant.now());

        for (SecPrivilegedRequest request : expired) {
            request.setStatus(SecPrivilegedRequest.STATUS_EXPIRED);
            request.setResolvedAt(Instant.now());
            requestRepository.save(request);
            LOGGER.info("Privileged request EXPIRED: id={} type={}", request.getId(), request.getRequestType());
        }

        if (!expired.isEmpty()) {
            LOGGER.info("Expired {} stale privileged requests", expired.size());
        }
    }
}
