package com.texora.secops.ftp.quarantine.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.texora.secops.ftp.config.FtpProperties;
import com.texora.secops.ftp.domain.InspectionOutcome;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Sends a staged file to an EXTERNAL sandbox/inspection service and returns its verdict.
 * Nothing is executed, parsed or loaded in-process: the file is streamed as opaque bytes.
 *
 * Contract (assumed - see README): POST {base}/api/v1/inspections, body = raw bytes, headers
 * X-Texora-Tenant-Id / X-Texora-Job-Id / X-Texora-File-Sha256; 200 JSON {"verdict","service","detail"}.
 * Fail closed: non-200, timeout, unreachable, unparseable or unknown verdict never yields CLEAN - the client either
 * throws {@link InspectionUnavailableException} or maps the value to INCONCLUSIVE. Only an explicit "CLEAN" is CLEAN.
 * Service-to-service mTLS comes from the JVM's SSL configuration.
 */
@Component
public class ContentInspectionClient {

    private final FtpProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient http;

    public ContentInspectionClient(FtpProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(props.getInspection().getConnectTimeoutMs()))
                .build();
    }

    public InspectionVerdict inspect(UUID tenantId, UUID jobId, Path stagedFile, String sha256) {
        String base = props.getInspection().getBaseUrl();
        if (base == null || base.isBlank()) {
            throw new InspectionUnavailableException("No inspection service configured");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + "/api/v1/inspections"))
                    .timeout(Duration.ofMillis(props.getInspection().getReadTimeoutMs()))
                    .header("Content-Type", "application/octet-stream")
                    .header("X-Texora-Tenant-Id", tenantId.toString())
                    .header("X-Texora-Job-Id", jobId.toString())
                    .header("X-Texora-File-Sha256", sha256)
                    .POST(HttpRequest.BodyPublishers.ofFile(stagedFile))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new InspectionUnavailableException("Inspection service returned HTTP " + response.statusCode());
            }
            JsonNode json = objectMapper.readTree(response.body());
            String verdict = json.path("verdict").asText("");
            String service = json.path("service").asText("inspection-service");
            String detail = json.path("detail").asText(null);
            return new InspectionVerdict(parse(verdict), service, detail);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InspectionUnavailableException("Interrupted while waiting for inspection", e);
        } catch (IOException | RuntimeException e) {
            if (e instanceof InspectionUnavailableException) {
                throw (InspectionUnavailableException) e;
            }
            throw new InspectionUnavailableException("Inspection service call failed: " + e.getClass().getSimpleName(), e);
        }
    }

    private static InspectionOutcome parse(String verdict) {
        if ("CLEAN".equals(verdict)) {
            return InspectionOutcome.CLEAN;
        }
        if ("MALICIOUS".equals(verdict)) {
            return InspectionOutcome.MALICIOUS;
        }
        return InspectionOutcome.INCONCLUSIVE;   // anything unrecognised gets no benefit of the doubt
    }
}
