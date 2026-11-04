package com.texora.secops.vpn.subject;

import com.texora.secops.vpn.VpnConstants;
import com.texora.secops.vpn.admin.VpnConfigService;
import com.texora.secops.vpn.audit.VpnAuditRecorder;
import com.texora.secops.vpn.domain.VpnSubject;
import com.texora.secops.vpn.dto.PageResponse;
import com.texora.secops.vpn.dto.SubjectResponse;
import com.texora.secops.vpn.exception.VpnException;
import com.texora.secops.vpn.repository.VpnSessionRepository;
import com.texora.secops.vpn.repository.VpnSubjectRepository;
import com.texora.secops.vpn.security.Caller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * vpn_subject records, kept in sync with the Domain Controller identity via {@link DcIdentityClient}.
 * The identity-status STALENESS THRESHOLD lives here: status older than the threshold is not trusted - it is refreshed,
 * and if it cannot be refreshed the identity is UNCERTAIN (which denies establishment and revokes active sessions).
 */
@Service
public class VpnSubjectService {

    private static final Logger LOGGER = LoggerFactory.getLogger(VpnSubjectService.class);

    private final VpnSubjectRepository subjects;
    private final VpnSessionRepository sessions;
    private final DcIdentityClient dc;
    private final VpnConfigService config;
    private final VpnAuditRecorder audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public VpnSubjectService(VpnSubjectRepository subjects, VpnSessionRepository sessions, DcIdentityClient dc,
                             VpnConfigService config, VpnAuditRecorder audit, TransactionTemplate tx, Clock clock) {
        this.subjects = subjects;
        this.sessions = sessions;
        this.dc = dc;
        this.config = config;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    public SubjectResponse create(Caller caller, UUID dcUserId) {
        UUID tenantId = caller.getTenantId();
        if (subjects.findByTenantIdAndDcUserId(tenantId, dcUserId).isPresent()) {
            throw VpnException.conflict("SUBJECT_EXISTS", "A VPN subject already exists for this DC user");
        }
        DcIdentityResult identity = dc.lookup(tenantId, dcUserId);
        if (identity == DcIdentityResult.UNAVAILABLE) {
            throw VpnException.gatewayTimeout("DC_UNAVAILABLE", "Identity could not be confirmed with the Domain Controller");
        }
        if (identity != DcIdentityResult.ACTIVE) {
            throw VpnException.unprocessable("DC_USER_NOT_ACTIVE", "DC user does not exist or is not active");
        }
        return tx.execute(status -> {
            Instant now = clock.instant();
            VpnSubject s = new VpnSubject();
            s.setId(UUID.randomUUID());
            s.setTenantId(tenantId);
            s.setDcUserId(dcUserId);
            s.setStatus(VpnConstants.SUBJECT_ACTIVE);
            s.setIdentitySyncedAt(now);
            s.setCreatedAt(now);
            subjects.save(s);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dcUserId", dcUserId.toString());
            audit.record(tenantId, caller.getUserId(), VpnConstants.ACTION_SUBJECT_CREATED, "VPN_SUBJECT",
                    s.getId().toString(), VpnConstants.OUTCOME_SUCCESS, payload);
            return toResponse(s);
        });
    }

    public PageResponse<SubjectResponse> list(UUID tenantId, int page, int size) {
        Page<VpnSubject> result = subjects.findByTenantId(tenantId, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
        List<SubjectResponse> items = new ArrayList<>();
        for (VpnSubject s : result.getContent()) {
            items.add(toResponse(s));
        }
        return new PageResponse<>(items, result.getNumber(), result.getSize(), result.getTotalElements());
    }

    public Optional<UUID> findDcUserId(UUID tenantId, UUID subjectId) {
        return subjects.findByIdAndTenantId(subjectId, tenantId).map(VpnSubject::getDcUserId);
    }

    public Optional<VpnSubject> findByDcUser(UUID tenantId, UUID dcUserId) {
        return subjects.findByTenantIdAndDcUserId(tenantId, dcUserId);
    }

    /**
     * Returns the identity state with the staleness rule applied. Never throws for DC problems: a failed refresh of a
     * stale status is UNCERTAIN. When a refresh flips a subject to SUSPENDED, all of that subject's ACTIVE sessions are
     * marked due for re-validation in the same transaction (the "identity status change" trigger).
     */
    public IdentityState ensureFresh(UUID tenantId, UUID subjectId) {
        VpnSubject subject = subjects.findByIdAndTenantId(subjectId, tenantId).orElse(null);
        if (subject == null) {
            return IdentityState.INACTIVE;
        }
        Instant now = clock.instant();
        Instant threshold = now.minusSeconds(config.identityStalenessSeconds(tenantId));
        boolean stale = subject.getIdentitySyncedAt() == null || subject.getIdentitySyncedAt().isBefore(threshold);
        if (stale) {
            DcIdentityResult result = dc.lookup(tenantId, subject.getDcUserId());
            if (result == DcIdentityResult.UNAVAILABLE) {
                LOGGER.warn("Identity for subject {} is stale and DC is unavailable -> UNCERTAIN", subjectId);
                return IdentityState.UNCERTAIN;
            }
            String newStatus = result == DcIdentityResult.ACTIVE ? VpnConstants.SUBJECT_ACTIVE : VpnConstants.SUBJECT_SUSPENDED;
            tx.executeWithoutResult(st -> {
                VpnSubject fresh = subjects.findByIdAndTenantId(subjectId, tenantId).orElseThrow();
                boolean changed = !newStatus.equals(fresh.getStatus());
                fresh.setStatus(newStatus);
                fresh.setIdentitySyncedAt(now);
                subjects.save(fresh);
                if (changed) {
                    sessions.markDueBySubject(tenantId, subjectId, now);
                }
            });
            return VpnConstants.SUBJECT_ACTIVE.equals(newStatus) ? IdentityState.ACTIVE : IdentityState.INACTIVE;
        }
        return VpnConstants.SUBJECT_ACTIVE.equals(subject.getStatus()) ? IdentityState.ACTIVE : IdentityState.INACTIVE;
    }

    private SubjectResponse toResponse(VpnSubject s) {
        SubjectResponse r = new SubjectResponse();
        r.setId(s.getId());
        r.setDcUserId(s.getDcUserId());
        r.setStatus(s.getStatus());
        r.setIdentitySyncedAt(s.getIdentitySyncedAt());
        r.setCreatedAt(s.getCreatedAt());
        return r;
    }
}
