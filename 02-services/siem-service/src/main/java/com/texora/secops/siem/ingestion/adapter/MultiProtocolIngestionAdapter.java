package com.texora.secops.siem.ingestion.adapter;

import com.texora.secops.siem.audit.AuditEventPublisher;
import com.texora.secops.siem.audit.SystemActor;
import com.texora.secops.siem.domain.SiemIngestionSource;
import com.texora.secops.siem.domain.enums.AuditOutcome;
import com.texora.secops.siem.domain.enums.Protocol;
import com.texora.secops.siem.domain.enums.SourceStatus;
import com.texora.secops.siem.ingestion.model.RawIngestionEvent;
import com.texora.secops.siem.ingestion.service.IngestionOrchestrationService;
import com.texora.secops.siem.repository.SiemIngestionSourceRepository;
import com.texora.secops.siem.security.CallerContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * B.4 adapter: the ONLY class in this service that imports Syslog/HTTP-transport/Kafka-client
 * plumbing. Everything downstream of {@link IngestionOrchestrationService#handle} deals purely
 * with {@link RawIngestionEvent} - protocol details never leak past this class.
 *
 * <p>Authentication model (LLD §9.1, B.6.1 carve-out): ingestion is source-to-SIEM, not
 * user-to-SIEM, so it is mTLS-authenticated instead of SSO-token-authenticated.
 * <ul>
 *   <li><b>Kafka:</b> trust is established at the broker/transport layer (Spring Kafka's SSL
 *       consumer properties, configured in application.yml) - the 12 platform audit topics are
 *       internal, mTLS-secured infrastructure, so their {@code siem_ingestion_source} rows are
 *       lazily self-provisioned here rather than requiring a manual admin registration step per
 *       topic.</li>
 *   <li><b>HTTP:</b> the mTLS terminator (ingress/service mesh) forwards the verified client
 *       certificate's CN in the {@code X-Client-Cert-CN} header; this adapter checks it against
 *       an explicitly {@code ACTIVE} registered source before accepting anything.</li>
 *   <li><b>Syslog:</b> an optional TLS server socket requiring client auth, off by default
 *       ({@code siem.ingestion.syslog.enabled=false}) - the platform's primary ingestion paths
 *       are Kafka and HTTP; this is provided for completeness per the LLD's protocol list.</li>
 * </ul>
 */
@Component
public class MultiProtocolIngestionAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiProtocolIngestionAdapter.class);
    private static final String AUDIT_SUFFIX = ".audit.events";

    private final IngestionOrchestrationService orchestrationService;
    private final SiemIngestionSourceRepository sourceRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final boolean syslogEnabled;
    private final int syslogPort;
    private final boolean syslogRequireMtls;

    private ExecutorService syslogExecutor;
    private volatile SSLServerSocket syslogServerSocket;

    public MultiProtocolIngestionAdapter(IngestionOrchestrationService orchestrationService,
                                          SiemIngestionSourceRepository sourceRepository,
                                          AuditEventPublisher auditEventPublisher,
                                          @Value("${siem.ingestion.syslog.enabled:false}") boolean syslogEnabled,
                                          @Value("${siem.ingestion.syslog.port:6514}") int syslogPort,
                                          @Value("${siem.ingestion.syslog.require-mtls:true}") boolean syslogRequireMtls) {
        this.orchestrationService = orchestrationService;
        this.sourceRepository = sourceRepository;
        this.auditEventPublisher = auditEventPublisher;
        this.syslogEnabled = syslogEnabled;
        this.syslogPort = syslogPort;
        this.syslogRequireMtls = syslogRequireMtls;
    }

    // ---------------------------------------------------------------- Kafka

    @KafkaListener(
            topics = "#{'${siem.ingestion.kafka-topics}'.split(',')}",
            containerFactory = "ingestionKafkaListenerContainerFactory")
    public void onKafkaEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String sourceKey = deriveSourceKeyFromTopic(record.topic());
        try {
            SiemIngestionSource source = resolveOrProvisionKafkaSource(sourceKey);
            RawIngestionEvent event = new RawIngestionEvent(source.getId(), sourceKey, Protocol.KAFKA,
                    Instant.now(), record.value());
            orchestrationService.handle(event);
            // Only ack once the event is durably recorded (BUFFERED/RETRIED/DLQ/PROCESSED) -
            // handle() only throws if that durable write itself failed, in which case we must
            // NOT ack, so the broker redelivers (Requirement 1 backpressure behaviour).
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            LOGGER.error("Failed to durably record event from topic {} - not acknowledging, "
                    + "broker will redeliver", record.topic(), ex);
            // Deliberately no acknowledgment.acknowledge() here.
        }
    }

    private String deriveSourceKeyFromTopic(String topic) {
        return topic.endsWith(AUDIT_SUFFIX) ? topic.substring(0, topic.length() - AUDIT_SUFFIX.length()) : topic;
    }

    // Note: Spring's @Transactional has no effect on a private method via self-invocation, and
    // Spring Data JPA's save() is already transactional on its own, so no annotation is needed
    // here - it would be dead code.
    private SiemIngestionSource resolveOrProvisionKafkaSource(String sourceKey) {
        return sourceRepository.findByNameAndTenantId(sourceKey, CallerContext.PLATFORM_TENANT_ID)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    SiemIngestionSource source = new SiemIngestionSource(UUID.randomUUID(),
                            CallerContext.PLATFORM_TENANT_ID, sourceKey, Protocol.KAFKA,
                            "kafka-broker-mtls", SourceStatus.ACTIVE, sourceKey, SystemActor.ID, now, now);
                    LOGGER.info("Auto-provisioned Kafka ingestion source '{}'", sourceKey);
                    return sourceRepository.save(source);
                });
    }

    // ---------------------------------------------------------------- HTTP

    public enum HttpIngestOutcome {
        ACCEPTED,
        REJECTED_UNAUTHENTICATED,
        REJECTED_INACTIVE_SOURCE
    }

    /** Called by the thin IngestionHttpController. Never throws for an auth failure - returns an
     *  outcome the controller maps to 401/403, and every rejection is itself audited (an
     *  unauthenticated-ingestion attempt is a security-relevant event in its own right). */
    public HttpIngestOutcome acceptHttpEvent(String sourceKey, String presentedCertCn, String rawBody) {
        Optional<SiemIngestionSource> sourceOpt = sourceRepository.findByNameAndTenantId(sourceKey, CallerContext.PLATFORM_TENANT_ID);
        if (sourceOpt.isEmpty()) {
            auditRejection(sourceKey, "unknown source");
            return HttpIngestOutcome.REJECTED_UNAUTHENTICATED;
        }
        SiemIngestionSource source = sourceOpt.get();
        if (source.getStatus() != SourceStatus.ACTIVE) {
            auditRejection(sourceKey, "source not ACTIVE");
            return HttpIngestOutcome.REJECTED_INACTIVE_SOURCE;
        }
        if (presentedCertCn == null || presentedCertCn.isBlank()
                || !certMatches(source.getMtlsCertRef(), presentedCertCn)) {
            auditRejection(sourceKey, "mTLS client certificate did not match the registered source");
            return HttpIngestOutcome.REJECTED_UNAUTHENTICATED;
        }

        RawIngestionEvent event = new RawIngestionEvent(source.getId(), sourceKey, Protocol.HTTP, Instant.now(), rawBody);
        orchestrationService.handle(event);
        return HttpIngestOutcome.ACCEPTED;
    }

    private boolean certMatches(String registeredCertRef, String presentedCertCn) {
        // mtls_cert_ref points at a SEC/KMS reference (see V1 migration comment), not the raw
        // subject itself; a full implementation resolves the reference via VaultKmsAdapter (SEC)
        // and compares the resolved CN. As a pragmatic baseline this compares against the
        // registered source name's presence in the reference, which callers should tighten once
        // SEC's certificate-reference resolution endpoint is available.
        return registeredCertRef != null && presentedCertCn != null
                && registeredCertRef.toLowerCase().contains(presentedCertCn.toLowerCase());
    }

    private void auditRejection(String sourceKey, String reason) {
        LOGGER.warn("Rejected unauthenticated ingestion attempt for source '{}': {}", sourceKey, reason);
        auditEventPublisher.publish(CallerContext.PLATFORM_TENANT_ID, SystemActor.ID,
                "ingestion.rejected.unauthenticated", "SiemIngestionSource", sourceKey, AuditOutcome.DENIED, reason);
    }

    // ---------------------------------------------------------------- Syslog

    @PostConstruct
    public void startSyslogListenerIfEnabled() {
        if (!syslogEnabled) {
            LOGGER.info("Syslog ingestion is disabled (siem.ingestion.syslog.enabled=false)");
            return;
        }
        syslogExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "syslog-ingestion-listener");
            t.setDaemon(true);
            return t;
        });
        syslogExecutor.submit(this::runSyslogListener);
    }

    @PreDestroy
    public void stopSyslogListener() {
        try {
            if (syslogServerSocket != null) {
                syslogServerSocket.close();
            }
        } catch (IOException ignored) {
            // shutting down anyway
        }
        if (syslogExecutor != null) {
            syslogExecutor.shutdownNow();
        }
    }

    private void runSyslogListener() {
        try {
            SSLServerSocketFactory factory = (SSLServerSocketFactory) (syslogRequireMtls
                    ? SSLContext.getDefault().getServerSocketFactory()
                    : SSLServerSocketFactory.getDefault());
            SSLServerSocket serverSocket = (SSLServerSocket) factory.createServerSocket(syslogPort);
            serverSocket.setNeedClientAuth(syslogRequireMtls);
            this.syslogServerSocket = serverSocket;
            LOGGER.info("Syslog (TLS) ingestion listener started on port {} (requireMtls={})", syslogPort, syslogRequireMtls);

            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                syslogExecutor.submit(() -> handleSyslogConnection((SSLSocket) client));
            }
        } catch (Exception ex) {
            if (syslogExecutor != null && !syslogExecutor.isShutdown()) {
                LOGGER.error("Syslog ingestion listener failed", ex);
            }
        }
    }

    private void handleSyslogConnection(SSLSocket client) {
        try (client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {

            String presentedCn = extractPeerCn(client);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String sourceKey = deriveSyslogSourceKey(line);
                HttpIngestOutcome outcome = acceptHttpEvent(sourceKey, presentedCn, line);
                if (outcome != HttpIngestOutcome.ACCEPTED) {
                    LOGGER.warn("Rejected syslog line from {}: {}", client.getInetAddress(), outcome);
                }
            }
        } catch (Exception ex) {
            LOGGER.warn("Syslog connection from {} terminated: {}", client.getInetAddress(), ex.getMessage());
        }
    }

    private String extractPeerCn(SSLSocket socket) {
        try {
            Certificate[] certs = socket.getSession().getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                return x509.getSubjectX500Principal().getName();
            }
        } catch (SSLPeerUnverifiedException ex) {
            LOGGER.warn("Syslog peer did not present a verifiable client certificate");
        }
        return null;
    }

    private String deriveSyslogSourceKey(String syslogLine) {
        // Minimal RFC5424-ish APP-NAME extraction as a baseline; a hardened implementation would
        // use a proper syslog parser. Falls back to a generic bucket if the shape is unexpected.
        String[] parts = syslogLine.split("\\s+");
        return parts.length > 4 ? parts[4].replaceAll("[^a-zA-Z0-9-]", "") : "syslog-unclassified";
    }
}
