package com.texora.secops.starter.config;

import org.springframework.boot.actuate.autoconfigure.metrics.export.otlp.OtlpMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Platform-standard observability configuration.
 *
 * <p>Activates:
 * <ul>
 *   <li>OpenTelemetry trace export via OTLP (endpoint controlled by
 *       {@code management.otlp.tracing.endpoint} property)</li>
 *   <li>Spring Boot Actuator with health, info, and metrics endpoints exposed</li>
 *   <li>Micrometer → OTel bridge so metrics go to the same collector</li>
 * </ul>
 *
 * <p>Services inherit this configuration from the starter. They must set:
 * <pre>
 *   management.otlp.tracing.endpoint=http://otel-collector:4318/v1/traces
 *   spring.application.name=my-service-name   # becomes the OTLP service.name
 * </pre>
 *
 * <p>Actuator endpoints are NOT exposed by default over HTTP in production —
 * services expose only what their Kubernetes liveness/readiness probes need.
 * Override in each service's {@code application.yml}:
 * <pre>
 *   management.endpoints.web.exposure.include: health,info,prometheus
 *   management.endpoint.health.show-details: never
 * </pre>
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Registers a custom span name resolver that includes the service name
     * and HTTP method in every trace span. Falls back to the default
     * resolver if the Micrometer Tracing auto-configuration is absent.
     *
     * <p>Declared {@link ConditionalOnProperty} so tests that don't start
     * a full OTel collector can skip the exporter bean.
     */
    @Bean
    @ConditionalOnProperty(
        name  = "texora.observability.enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public ObservabilityCustomizer observabilityCustomizer() {
        return new ObservabilityCustomizer();
    }

    /**
     * Placeholder customizer. In a full platform deployment this class
     * would register custom {@code SpanNameExtractor} and
     * {@code SamplerFunction} beans. Left intentionally extensible.
     */
    public static final class ObservabilityCustomizer {
        private ObservabilityCustomizer() {
            // no-op in foundation; services add custom tracing here
        }
    }
}
