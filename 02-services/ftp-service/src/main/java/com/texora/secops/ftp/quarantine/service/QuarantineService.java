package com.texora.secops.ftp.quarantine.service;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.domain.InspectionOutcome;
import com.texora.secops.ftp.domain.InspectionResult;
import com.texora.secops.ftp.domain.JobStatus;
import com.texora.secops.ftp.domain.JobStatuses;
import com.texora.secops.ftp.domain.QuarantineRecord;
import com.texora.secops.ftp.domain.QuarantineStatus;
import com.texora.secops.ftp.domain.TransferJob;
import com.texora.secops.ftp.dto.InspectionResultResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.Paging;
import com.texora.secops.ftp.dto.PlaceQuarantineRequest;
import com.texora.secops.ftp.dto.QuarantineRecordResponse;
import com.texora.secops.ftp.dto.ReleaseQuarantineRequest;
import com.texora.secops.ftp.exception.ConflictException;
import com.texora.secops.ftp.exception.NotFoundException;
import com.texora.secops.ftp.exception.RbacDeniedException;
import com.texora.secops.ftp.exception.TenantAccess;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.job.event.QuarantineDestroyedEvent;
import com.texora.secops.ftp.job.event.QuarantineReleasedEvent;
import com.texora.secops.ftp.quarantine.client.ContentInspectionClient;
import com.texora.secops.ftp.quarantine.client.InspectionVerdict;
import com.texora.secops.ftp.repository.InspectionResultRepository;
import com.texora.secops.ftp.repository.QuarantineRecordRepository;
import com.texora.secops.ftp.repository.TransferJobRepository;
import com.texora.secops.ftp.security.CallerAuthentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Requirements 2 and 3.
 *
 * FAIL-CLOSED INSPECTION: the only path to "release" is an explicit CLEAN verdict. MALICIOUS, INCONCLUSIVE, an
 * unavailable service, a timeout, a null/garbled verdict, or ANY exception all end in QUARANTINED.
 * Nothing in this class (or package) is scheduled: a quarantined file is never auto-released on a timer or a retry
 * loop. The only exit is {@link #release}, which needs a second person.
 */
@Service
public class QuarantineService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuarantineService.class);
    public static final String UNAVAILABLE_SERVICE = "UNAVAILABLE";

    private final QuarantineRecordRepository quarantineRepo;
    private final InspectionResultRepository inspectionRepo;
    private final TransferJobRepository jobs;
    private final ContentInspectionClient inspectionClient;
    private final FtpAuditTrail audit;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;

    public QuarantineService(QuarantineRecordRepository quarantineRepo, InspectionResultRepository inspectionRepo,
                             TransferJobRepository jobs, ContentInspectionClient inspectionClient, FtpAuditTrail audit,
                             ApplicationEventPublisher events, PlatformTransactionManager tm) {
        this.quarantineRepo = quarantineRepo;
        this.inspectionRepo = inspectionRepo;
        this.jobs = jobs;
        this.inspectionClient = inspectionClient;
        this.audit = audit;
        this.events = events;
        this.tx = new TransactionTemplate(tm);
    }

    /**
     * Inspects the staged file (via the EXTERNAL service) and decides its fate.
     *
     * @return true ONLY when the verdict is an explicit CLEAN and the caller may release the file to the destination;
     *         false means the job has been moved to QUARANTINED (or is no longer running) and must not be delivered.
     */
    public boolean inspectStaged(TransferJob job, Path stagedFile, String sha256) {
        InspectionVerdict verdict;
        try {
            verdict = inspectionClient.inspect(job.getTenantId(), job.getId(), stagedFile, sha256);
        } catch (Exception e) {
            // Fail closed: any failure at all is "unavailable", which quarantines. Never skip-and-release.
            LOGGER.warn("Content inspection unavailable for job {} - quarantining: {}", job.getId(), e.toString());
            verdict = unavailable(e);
        }
        if (verdict == null || verdict.getOutcome() == null) {
            verdict = unavailable(null);
        }
        final InspectionVerdict v = verdict;
        if (v.getOutcome() == InspectionOutcome.CLEAN) {
            tx.executeWithoutResult(status -> {
                inspectionRepo.save(new InspectionResult(UUID.randomUUID(), job.getTenantId(), job.getId(), null,
                        v.getService(), InspectionOutcome.CLEAN, truncate(v.getDetail(), 500), Instant.now()));
                audit.inspectionResult(job.getTenantId(), CallerAuthentication.SYSTEM_ACTOR, job.getId(), "CLEAN",
                        Map.<String, Object>of("service", String.valueOf(v.getService())));
            });
            return true;
        }
        quarantineFromInspection(job, v);
        return false;
    }

    private void quarantineFromInspection(TransferJob job, InspectionVerdict v) {
        final String reason = reasonFor(v);
        tx.executeWithoutResult(status -> {
            Instant now = Instant.now();
            int moved = jobs.transition(job.getId(), job.getTenantId(), JobStatuses.RUNNING, JobStatus.QUARANTINED, reason, now);
            if (moved == 0) {
                LOGGER.warn("Job {} is no longer running; not creating a quarantine record", job.getId());
                return;
            }
            QuarantineRecord record = new QuarantineRecord(UUID.randomUUID(), job.getTenantId(), job.getId(), reason, now,
                    null, null, QuarantineStatus.QUARANTINED, null);
            quarantineRepo.save(record);
            inspectionRepo.save(new InspectionResult(UUID.randomUUID(), job.getTenantId(), job.getId(), record.getId(),
                    v.getService(), v.getOutcome(), truncate(v.getDetail(), 500), now));
            audit.inspectionResult(job.getTenantId(), CallerAuthentication.SYSTEM_ACTOR, job.getId(), v.getOutcome().name(),
                    Map.<String, Object>of("service", String.valueOf(v.getService())));
            audit.quarantinePlaced(job.getTenantId(), CallerAuthentication.SYSTEM_ACTOR, record.getId(),
                    Map.<String, Object>of("jobId", job.getId().toString(), "reason", reason));
        });
    }

    /** POST /quarantine-controls - an authorised operator manually quarantines a running job. */
    @Transactional
    public QuarantineRecordResponse placeManually(CallerAuthentication caller, PlaceQuarantineRequest request) {
        UUID tenantId = caller.getTenantId();
        TransferJob job = TenantAccess.require(jobs.findByIdAndTenantId(request.getTransferJobId(), tenantId),
                () -> jobs.existsById(request.getTransferJobId()), "Transfer job");
        Instant now = Instant.now();
        int moved = jobs.transition(job.getId(), tenantId, JobStatuses.RUNNING, JobStatus.QUARANTINED, "MANUAL_QUARANTINE", now);
        if (moved == 0) {
            throw new ConflictException("JOB_NOT_QUARANTINABLE", "Only a running job can be quarantined (status=" + job.getStatus() + ")");
        }
        QuarantineRecord record = new QuarantineRecord(UUID.randomUUID(), tenantId, job.getId(),
                truncate("MANUAL: " + request.getReason().trim(), 500), now, null, null, QuarantineStatus.QUARANTINED, null);
        quarantineRepo.save(record);
        audit.quarantinePlaced(tenantId, caller.getActorId(), record.getId(),
                Map.<String, Object>of("jobId", job.getId().toString(), "manual", true));
        return toResponse(record);
    }

    /**
     * Requirement 3 - two-person release/destroy.
     * The caller already holds ftp.quarantine.release (RbacInterceptor). Additionally the reviewer must NOT be the
     * job's original initiator: self-release is a 403, is audited as DENIED, and changes nothing.
     */
    @Transactional
    public QuarantineRecordResponse release(CallerAuthentication reviewer, UUID quarantineId, ReleaseQuarantineRequest request) {
        UUID tenantId = reviewer.getTenantId();
        QuarantineRecord record = TenantAccess.require(quarantineRepo.findByIdAndTenantId(quarantineId, tenantId),
                () -> quarantineRepo.existsById(quarantineId), "Quarantine record");
        if (record.getStatus() != QuarantineStatus.QUARANTINED) {
            throw new ConflictException("QUARANTINE_ALREADY_RESOLVED", "Quarantine record is already " + record.getStatus());
        }
        TransferJob job = jobs.findByIdAndTenantId(record.getTransferJobId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Transfer job for quarantine record not found"));

        if (job.getInitiatorSsoSubject().equals(reviewer.getSubject())) {
            audit.quarantineReleaseDenied(tenantId, reviewer.getActorId(), record.getId(), "SELF_RELEASE_ATTEMPT");
            throw new RbacDeniedException("SELF_RELEASE_BLOCKED",
                    "The initiator of a transfer cannot release or destroy its quarantined file");
        }

        boolean release = "RELEASE".equals(request.getAction());
        if (!release && !"DESTROY".equals(request.getAction())) {
            throw new UnprocessableException("QUARANTINE_ACTION_INVALID", "action must be RELEASE or DESTROY");
        }
        Instant now = Instant.now();
        record.setStatus(release ? QuarantineStatus.RELEASED : QuarantineStatus.DESTROYED);
        record.setReleasedBy(reviewer.getSubject());
        record.setReleasedAt(now);
        record.setResolutionNote(truncate(request.getJustification().trim(), 1000));
        quarantineRepo.save(record);

        if (release) {
            audit.quarantineReleased(tenantId, reviewer.getActorId(), record.getId(),
                    Map.<String, Object>of("jobId", job.getId().toString()));
            // Delivery to the destination happens AFTER this transaction commits (see TransferEventListener).
            events.publishEvent(new QuarantineReleasedEvent(tenantId, job.getId(), reviewer.getSubject()));
        } else {
            jobs.transition(job.getId(), tenantId, EnumSet.of(JobStatus.QUARANTINED), JobStatus.FAILED,
                    "QUARANTINE_DESTROYED", now);
            audit.quarantineDestroyed(tenantId, reviewer.getActorId(), record.getId(),
                    Map.<String, Object>of("jobId", job.getId().toString()));
            events.publishEvent(new QuarantineDestroyedEvent(tenantId, job.getId()));
        }
        return toResponse(record);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuarantineRecordResponse> list(UUID tenantId, String status, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "quarantinedAt"));
        Page<QuarantineRecord> result;
        if (status == null || status.isBlank()) {
            result = quarantineRepo.findByTenantId(tenantId, pageable);
        } else {
            QuarantineStatus parsed;
            try {
                parsed = QuarantineStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new UnprocessableException("QUARANTINE_STATUS_INVALID", "Unknown quarantine status");
            }
            result = quarantineRepo.findByTenantIdAndStatus(tenantId, parsed, pageable);
        }
        return new PageResponse<>(result.getContent().stream().map(this::toResponse).collect(Collectors.toList()),
                result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private QuarantineRecordResponse toResponse(QuarantineRecord r) {
        List<InspectionResultResponse> inspections = inspectionRepo
                .findByTransferJobIdAndTenantIdOrderByInspectedAtAsc(r.getTransferJobId(), r.getTenantId()).stream()
                .map(i -> new InspectionResultResponse(i.getId(), i.getInspectionService(), i.getOutcome().name(),
                        i.getDetail(), i.getInspectedAt()))
                .collect(Collectors.toList());
        return new QuarantineRecordResponse(r.getId(), r.getTransferJobId(), r.getReason(), r.getQuarantinedAt(),
                r.getReleasedBy(), r.getReleasedAt(), r.getStatus().name(), inspections);
    }

    private static InspectionVerdict unavailable(Exception e) {
        String detail = "INSPECTION_UNAVAILABLE" + (e == null ? "" : ": " + e.getClass().getSimpleName());
        return new InspectionVerdict(InspectionOutcome.INCONCLUSIVE, UNAVAILABLE_SERVICE, detail);
    }

    private static String reasonFor(InspectionVerdict v) {
        if (UNAVAILABLE_SERVICE.equals(v.getService())) {
            return "INSPECTION_UNAVAILABLE";
        }
        if (v.getOutcome() == InspectionOutcome.MALICIOUS) {
            return "MALICIOUS_CONTENT";
        }
        return "INSPECTION_INCONCLUSIVE";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
