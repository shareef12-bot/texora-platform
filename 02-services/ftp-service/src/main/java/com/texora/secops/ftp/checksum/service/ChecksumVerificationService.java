package com.texora.secops.ftp.checksum.service;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.domain.ChecksumRecord;
import com.texora.secops.ftp.dto.ChecksumRecordResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.Paging;
import com.texora.secops.ftp.exception.ConflictException;
import com.texora.secops.ftp.repository.ChecksumRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Requirement 4 - idempotent checksums. There is exactly ONE record per (job, algorithm), enforced by a database
 * UNIQUE constraint and an INSERT ... ON CONFLICT DO NOTHING. A resumed/retried transfer that reports the same
 * values is a no-op; reporting different values for an already-verified record is a CHECKSUM_CONFLICT (integrity
 * failure), never a second or overwritten record.
 */
@Service
public class ChecksumVerificationService {

    public static final String SHA256 = "SHA-256";

    private final ChecksumRecordRepository repository;
    private final FtpAuditTrail audit;

    public ChecksumVerificationService(ChecksumRecordRepository repository, FtpAuditTrail audit) {
        this.repository = repository;
        this.audit = audit;
    }

    /** @return true when the computed value matches the expected one. */
    @Transactional
    public boolean recordAndVerify(UUID tenantId, String actorId, UUID jobId, String algorithm, String expected, String computed) {
        String exp = normalise(expected);
        String comp = normalise(computed);
        boolean verified = constantTimeEquals(exp, comp);
        Instant now = Instant.now();

        int inserted = repository.insertIfAbsent(UUID.randomUUID(), tenantId, jobId, algorithm, exp, comp, verified, now);
        if (inserted == 1) {
            audit.checksumResult(tenantId, actorId, jobId, verified, Map.<String, Object>of("algorithm", algorithm,
                    "verified", verified));
            return verified;
        }

        ChecksumRecord existing = repository.findByTransferJobIdAndAlgorithmAndTenantId(jobId, algorithm, tenantId)
                .orElseThrow(() -> new ConflictException("CHECKSUM_RECORD_MISSING", "Checksum record could not be resolved"));
        if (!existing.getExpectedValue().equals(exp)) {
            throw new ConflictException("CHECKSUM_CONFLICT", "Expected checksum differs from the recorded value");
        }
        if (existing.isVerified()) {
            if (!existing.getComputedValue().equals(comp)) {
                throw new ConflictException("CHECKSUM_CONFLICT", "Computed checksum differs from an already-verified record");
            }
            return true;   // idempotent retry: nothing to do
        }
        if (!existing.getComputedValue().equals(comp) || existing.isVerified() != verified) {
            existing.setComputedValue(comp);
            existing.setVerified(verified);
            existing.setRecordedAt(now);
            repository.save(existing);
            audit.checksumResult(tenantId, actorId, jobId, verified, Map.<String, Object>of("algorithm", algorithm,
                    "verified", verified, "recomputed", true));
        }
        return verified;
    }

    /** True only if a VERIFIED record exists and the supplied value equals it (used before releasing from quarantine). */
    @Transactional(readOnly = true)
    public boolean matchesRecorded(UUID tenantId, UUID jobId, String algorithm, String computed) {
        return repository.findByTransferJobIdAndAlgorithmAndTenantId(jobId, algorithm, tenantId)
                .map(r -> r.isVerified() && constantTimeEquals(r.getComputedValue(), normalise(computed)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ChecksumRecordResponse> list(UUID tenantId, UUID jobId, int page, int size) {
        Pageable pageable = Paging.of(page, size, Sort.by(Sort.Direction.DESC, "recordedAt"));
        Page<ChecksumRecord> result = jobId == null
                ? repository.findByTenantId(tenantId, pageable)
                : repository.findByTenantIdAndTransferJobId(tenantId, jobId, pageable);
        return new PageResponse<>(result.getContent().stream().map(ChecksumVerificationService::toResponse)
                .collect(Collectors.toList()), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    private static String normalise(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static ChecksumRecordResponse toResponse(ChecksumRecord r) {
        return new ChecksumRecordResponse(r.getId(), r.getTransferJobId(), r.getAlgorithm(), r.getExpectedValue(),
                r.getComputedValue(), r.isVerified(), r.getRecordedAt());
    }
}
