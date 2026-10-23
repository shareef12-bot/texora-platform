package com.texora.secops.ftp.endpoint.service;

import com.texora.secops.ftp.audit.FtpAuditTrail;
import com.texora.secops.ftp.domain.EndpointDirection;
import com.texora.secops.ftp.domain.FtpEndpoint;
import com.texora.secops.ftp.domain.TransferProtocol;
import com.texora.secops.ftp.dto.EndpointRequest;
import com.texora.secops.ftp.dto.EndpointResponse;
import com.texora.secops.ftp.dto.PageResponse;
import com.texora.secops.ftp.dto.Paging;
import com.texora.secops.ftp.exception.TenantAccess;
import com.texora.secops.ftp.exception.UnprocessableException;
import com.texora.secops.ftp.repository.EndpointRepository;
import com.texora.secops.ftp.security.CallerAuthentication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Requirement 1 - endpoint allowlisting. {@link #requireAllowlisted} is called BEFORE any transfer is created or
 * started (and again immediately before execution), so a non-allowlisted endpoint can never be contacted.
 */
@Service
public class EndpointAllowlistService {

    private static final Pattern SFTP_FINGERPRINT = Pattern.compile("^SHA256:[A-Za-z0-9+/]{43}=?$");

    private final EndpointRepository endpoints;
    private final FtpAuditTrail audit;

    public EndpointAllowlistService(EndpointRepository endpoints, FtpAuditTrail audit) {
        this.endpoints = endpoints;
        this.audit = audit;
    }

    @Transactional
    public EndpointResponse register(UUID tenantId, UUID actorSubject, String actorId, EndpointRequest request) {
        TransferProtocol protocol = TransferProtocol.valueOf(request.getProtocol());
        EndpointDirection direction = EndpointDirection.valueOf(request.getDirection());
        String host = HostValidator.validate(request.getHost());
        String basePath = normaliseBasePath(request.getBasePath());
        String fingerprint = request.getHostKeyFingerprint() == null ? null : request.getHostKeyFingerprint().trim();
        if (protocol == TransferProtocol.SFTP) {
            if (fingerprint == null || !SFTP_FINGERPRINT.matcher(fingerprint).matches()) {
                throw new UnprocessableException("ENDPOINT_HOST_KEY_REQUIRED",
                        "SFTP endpoints require a pinned host key fingerprint (format SHA256:<base64>)");
            }
        } else {
            fingerprint = null;   // FTPS trust comes from the TLS certificate chain, not a pin
        }
        boolean allowlisted = request.getAllowlisted() == null || request.getAllowlisted();
        FtpEndpoint endpoint = new FtpEndpoint(UUID.randomUUID(), tenantId, host, request.getPort(), direction, protocol,
                basePath, request.getCredentialRef().trim(), fingerprint, allowlisted, actorSubject, Instant.now());
        endpoints.saveAndFlush(endpoint);
        audit.endpointRegistered(tenantId, actorId, endpoint.getId(), Map.<String, Object>of(
                "host", host, "port", endpoint.getPort(), "direction", direction.name(),
                "protocol", protocol.name(), "allowlisted", allowlisted));
        return toResponse(endpoint);
    }

    @Transactional(readOnly = true)
    public PageResponse<EndpointResponse> list(UUID tenantId, int page, int size) {
        Page<FtpEndpoint> result = endpoints.findByTenantId(tenantId, Paging.of(page, size, Sort.by("host")));
        return new PageResponse<>(result.getContent().stream().map(EndpointAllowlistService::toResponse)
                .collect(Collectors.toList()), result.getNumber(), result.getSize(), result.getTotalElements());
    }

    /**
     * Loads the endpoint, verifies tenant ownership (403 if another tenant's), direction, and allowlisting.
     * Non-allowlisted -> 422 ENDPOINT_NOT_ALLOWLISTED (and the rejection is audited).
     */
    @Transactional(readOnly = true)
    public FtpEndpoint requireAllowlisted(UUID tenantId, String actorId, UUID endpointId, EndpointDirection expected) {
        FtpEndpoint endpoint = TenantAccess.require(endpoints.findByIdAndTenantId(endpointId, tenantId),
                () -> endpoints.existsById(endpointId), "Endpoint");
        if (endpoint.getDirection() != expected) {
            throw new UnprocessableException("ENDPOINT_DIRECTION_MISMATCH",
                    "Endpoint is registered as " + endpoint.getDirection() + " but is used as " + expected);
        }
        if (!endpoint.isAllowlisted()) {
            audit.endpointRejected(tenantId, actorId == null ? CallerAuthentication.SYSTEM_ACTOR : actorId, endpointId,
                    "NOT_ALLOWLISTED");
            throw new UnprocessableException("ENDPOINT_NOT_ALLOWLISTED", "Endpoint is not on the allowlist");
        }
        return endpoint;
    }

    private static String normaliseBasePath(String raw) {
        String p = raw == null || raw.isBlank() ? "/" : raw.trim();
        if (!p.startsWith("/") || p.contains("..") || p.indexOf('\\') >= 0 || p.indexOf('\0') >= 0 || p.contains("//")) {
            throw new UnprocessableException("ENDPOINT_BASE_PATH_INVALID", "basePath must be an absolute path without '..'");
        }
        return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
    }

    private static EndpointResponse toResponse(FtpEndpoint e) {
        return new EndpointResponse(e.getId(), e.getHost(), e.getPort(), e.getDirection().name(), e.getProtocol().name(),
                e.getBasePath(), e.getCredentialRef(), e.getHostKeyFingerprint(), e.isAllowlisted(), e.getAddedBy(),
                e.getAddedAt());
    }
}
