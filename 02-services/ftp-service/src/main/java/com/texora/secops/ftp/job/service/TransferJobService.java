package com.texora.secops.ftp.job.service;

import com.texora.secops.ftp.admin.service.ServiceConfigService;
import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.domain.EndpointDirection;
import com.texora.secops.ftp.domain.FtpEndpoint;
import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.JobStatuses;
import com.texora.secops.ftp.domain.TransferAttempt;
import com.texora.secops.ftp.domain.TransferJob;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.Paging;
import com.texora.secops.ftp.dto.SubmitTransferJobRequest;
import com.texora.secops.ftp.dto.SubmitTransferJobResponse;
import com.texora.secops.ftp.dto.TransferAttemptResponse;
import com.texora.secops.ftp.dto.TransferJobResponse;
import com.texora.secops.ftp.endpoint.service.EndpointAllowlistService;
import com.texora.secops.ftp.exception.ConflictException;
import com.texora.secops.ftp.exception.RbacDeniedException;
import com.texora.secops.ftp.exception.TenantAccess;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.exception.UpstreamUnavailableException;
import com.texora.secops.ftp.job.event.TransferJobSubmittedEvent;
import com.texora.secops.ftp.repository.TransferAttemptRepository;
import com.texora.secops.ftp.repository.TransferJobRepository;
import com.texora.secops.ftp.security.CallerAuthentication;
import com.texora.secops.ftp.security.PolicyDecision;
import com.texora.secops.ftp.security.PolicyDecisionClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransferJobService {

    private final TransferJobRepository jobs;
    private final TransferAttemptRepository attempts;
    private final EndpointAllowlistService endpoints;
    private final PolicyDecisionClient policyClient;
    private final ServiceConfigService config;
    private final FtpAuditTrail audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public TransferJobService(TransferJobRepository jobs, TransferAttemptRepository attempts,
                              EndpointAllowlistService endpoints, PolicyDecisionClient policyClient,
                              ServiceConfigService config, FtpAuditTrail audit, ApplicationEventPublisher events,
                              PlatformTransactionManager tm) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.endpoints = endpoints;
        this.policyClient = policyClient;
        this.config = config;
        this.audit = audit;
        this.events = events;
        this.tx = new TransactionTemplate(tm);
    }

    /**
     * Deliberately NOT @Transactional as a whole: the SEC policy call is a network call and must not hold a DB
     * connection. Order matters - allowlist (Requirement 1) first, then policy, then the single insert transaction.
     */
    public SubmitTransferJobResponse submit(CallerAuthentication caller, SubmitTransferJobRequest request) {
        final UUID tenantId = caller.getTenantId();

        // Requirement 1: BEFORE any transfer begins. Non-allowlisted -> 422. Other tenant's endpoint -> 403.
        FtpEndpoint source = endpoints.requireAllowlisted(tenantId, caller.getActorId(), request.getSourceEndpointId(),
                EndpointDirection.SOURCE);
        FtpEndpoint dest = endpoints.requireAllowlisted(tenantId, caller.getActorId(), request.getDestEndpointId(),
                EndpointDirection.DEST);

        boolean mandatory = config.inspectionMandatory(tenantId);
        boolean requireInspection = request.getRequireInspection() == null
                ? config.inspectionDefaultRequired(tenantId) : request.getRequireInspection();
        if (mandatory && !requireInspection) {
            throw new UnprocessableException("INSPECTION_MANDATORY", "Content inspection is mandatory for this tenant");
        }

        // Fail-closed policy decision: anything but an explicit ALLOW stops here.
        final UUID jobId = UUID.randomUUID();
        PolicyDecision decision = policyClient.decide(tenantId, caller.getSubject(), "ftp.transfer.submit",
                "endpoints:" + source.getId() + "->" + dest.getId());
        audit.policyDecision(tenantId, caller.getActorId(), jobId, decision.isAllowed(),
                Map.<String, Object>of("reason", String.valueOf(decision.getReason())));
        if (!decision.isAllowed()) {
            if (decision.isUnavailable()) {
                throw new UpstreamUnavailableException("POLICY_UNAVAILABLE",
                        "Policy service unavailable; transfer denied (fail closed)");
            }
            throw new RbacDeniedException("POLICY_DENIED", "Transfer denied by policy");
        }

        final Instant now = Instant.now();
        final String expected = request.getExpectedSha256() == null ? null : request.getExpectedSha256().toLowerCase(Locale.ROOT);
        final TransferJob job = new TransferJob(jobId, tenantId, caller.getSubject(), source.getId(), dest.getId(),
                request.getFileName(), requireInspection, expected, null, JobStatus.SUBMITTED, null, now, now);
        tx.executeWithoutResult(status -> {
            jobs.save(job);
            audit.jobSubmitted(tenantId, caller.getActorId(), jobId, Map.<String, Object>of(
                    "sourceEndpointId", source.getId().toString(), "destEndpointId", dest.getId().toString(),
                    "requireInspection", job.isRequireInspection()));
            // Delivered to the async listener only AFTER this transaction commits.
            events.publishEvent(new TransferJobSubmittedEvent(tenantId, jobId));
        });
        return new SubmitTransferJobResponse(jobId, JobStatus.SUBMITTED.name(), now);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferJobResponse> list(UUID tenantId, String status, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TransferJob> result;
        if (status == null || status.isBlank()) {
            result = jobs.findByTenantId(tenantId, pageable);
        } else {
            JobStatus parsed;
            try {
                parsed = JobStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new UnprocessableException("JOB_STATUS_INVALID", "Unknown job status");
            }
            result = jobs.findByTenantIdAndStatus(tenantId, parsed, pageable);
        }
        return new PageResponse<>(result.getContent().stream().map(j -> toResponse(j, null)).collect(Collectors.toList()),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TransferJobResponse get(UUID tenantId, UUID jobId) {
        TransferJob job = TenantAccess.require(jobs.findByIdAndTenantId(jobId, tenantId),
                () -> jobs.existsById(jobId), "Transfer job");
        List<TransferAttempt> history = attempts.findByTransferJobIdAndTenantIdOrderByAttemptNumberAsc(jobId, tenantId);
        return toResponse(job, history);
    }

    @Transactional
    public TransferJobResponse cancel(CallerAuthentication caller, UUID jobId) {
        UUID tenantId = caller.getTenantId();
        TransferJob job = TenantAccess.require(jobs.findByIdAndTenantId(jobId, tenantId),
                () -> jobs.existsById(jobId), "Transfer job");
        int moved = jobs.transition(jobId, tenantId, JobStatuses.RUNNING, JobStatus.FAILED, "CANCELLED", Instant.now());
        if (moved == 0) {
            throw new ConflictException("JOB_NOT_CANCELLABLE", "Job is not running (status=" + job.getStatus() + ")");
        }
        audit.jobCancelled(tenantId, caller.getActorId(), jobId);
        return get(tenantId, jobId);
    }

    private static TransferJobResponse toResponse(TransferJob j, List<TransferAttempt> history) {
        List<TransferAttemptResponse> attemptDtos = history == null ? null : history.stream()
                .map(a -> new TransferAttemptResponse(a.getAttemptNumber(), a.getPhase().name(), a.getOutcome().name(),
                        a.getStartedAt(), a.getEndedAt(), a.getErrorSummary()))
                .collect(Collectors.toList());
        return new TransferJobResponse(j.getId(), j.getStatus().name(), j.getSourceEndpointId(), j.getDestEndpointId(),
                j.getFileName(), j.isRequireInspection(), j.getInitiatorSsoSubject(), j.getFailureReason(),
                j.getCreatedAt(), j.getUpdatedAt(), attemptDtos);
    }
}
