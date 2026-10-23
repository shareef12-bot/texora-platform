package com.texora.secops.siem.ingestion.service;

import com.texora.secops.siem.audit.Audited;
import com.texora.secops.siem.domain.SiemIngestionSource;
import com.texora.secops.siem.domain.enums.Protocol;
import com.texora.secops.siem.domain.enums.SourceStatus;
import com.texora.secops.siem.repository.SiemIngestionSourceRepository;
import com.texora.secops.siem.security.CrossTenantAccessException;
import com.texora.secops.siem.web.ConflictException;
import com.texora.secops.siem.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin CRUD for registered ingestion channels (GET/POST /api/v1/log-ingestion). This is
 * distinct from the raw event-receipt path in {@code MultiProtocolIngestionAdapter} - this
 * service is SSO/RBAC-authenticated, used by SOC admins to provision the mTLS-authenticated
 * channels that adapter then accepts traffic from.
 */
@Service
public class IngestionSourceService {

    private final SiemIngestionSourceRepository sourceRepository;

    public IngestionSourceService(SiemIngestionSourceRepository sourceRepository) {
        this.sourceRepository = sourceRepository;
    }

    public List<SiemIngestionSource> list(UUID tenantId) {
        return sourceRepository.findAllByTenantId(tenantId);
    }

    public SiemIngestionSource get(UUID id, UUID tenantId) {
        SiemIngestionSource source = sourceRepository.findByIdRaw(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingestion source " + id + " not found"));
        if (!source.getTenantId().equals(tenantId)) {
            throw new CrossTenantAccessException("Ingestion source " + id + " does not belong to caller's tenant");
        }
        return source;
    }

    @Audited(action = "ingestion-source.register", targetType = "SiemIngestionSource")
    @Transactional
    public SiemIngestionSource register(UUID tenantId, UUID registeredBy, String name, Protocol protocol,
                                         String mtlsCertRef, String productId) {
        Optional<SiemIngestionSource> existing = sourceRepository.findByNameAndTenantId(name, tenantId);
        if (existing.isPresent()) {
            throw new ConflictException("An ingestion source named '" + name + "' is already registered");
        }
        Instant now = Instant.now();
        SiemIngestionSource source = new SiemIngestionSource(UUID.randomUUID(), tenantId, name, protocol,
                mtlsCertRef, SourceStatus.ACTIVE, productId, registeredBy, now, now);
        return sourceRepository.save(source);
    }

    @Audited(action = "ingestion-source.status-change", targetType = "SiemIngestionSource")
    @Transactional
    public SiemIngestionSource setStatus(UUID id, UUID tenantId, SourceStatus status) {
        SiemIngestionSource source = get(id, tenantId);
        source.setStatus(status);
        source.setUpdatedAt(Instant.now());
        return sourceRepository.save(source);
    }
}
