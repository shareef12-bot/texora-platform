package com.texora.secops.ldap.audit.service;

import com.texora.secops.ldap.domain.LdapAuditEvent;
import com.texora.secops.ldap.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditSearchService {

    private final AuditEventRepository auditEventRepository;

    public AuditSearchService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    @Transactional(readOnly = true)
    public Page<LdapAuditEvent> search(UUID tenantId,
                                        String action,
                                        String targetType,
                                        String targetId,
                                        Instant from,
                                        Instant to,
                                        int page,
                                        int size) {
        if (size > 200) {
            size = 200; // cap page size
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return auditEventRepository.search(tenantId, action, targetType, targetId, from, to, pageable);
    }
}
