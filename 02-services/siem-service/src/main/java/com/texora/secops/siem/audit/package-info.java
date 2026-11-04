/**
 * LOCAL audit implementation - a STOPGAP, not a reimplementation of texora-audit-sdk.
 *
 * <p>This service declares a dependency on {@code texora-audit-sdk} (see pom.xml), whose
 * {@code AuditingAspect} is the platform-standard mechanism (B.3/B.6.4). Its real annotation
 * name, publisher class, and Kafka wiring were NOT available when this service was built -
 * KNOWN-INTEGRATION-PITFALLS.md only confirms the class exists and needs spring-security-core.
 * Guessing that API would repeat the mistake that document's item 1 exists to prevent, so this
 * package keeps a small working local equivalent (writes siem_audit_event, publishes to
 * siem.audit.events) until the real API is confirmed.
 *
 * <p>Both local beans carry explicit "siem"-prefixed names to avoid a bean-name collision with
 * the SDK's identically-named classes (same failure class as pitfalls item 3).
 *
 * <p><b>TODO(integration):</b> once the SDK's real API is confirmed, replace {@code @Audited},
 * {@code AuditingAspect} and {@code AuditEventPublisher} with the shared ones, and note that the
 * SDK's aspect reads SecurityContextHolder for the actor - this service authenticates via
 * TokenValidator in RbacInterceptor and does NOT populate SecurityContextHolder today, so
 * RbacInterceptor will also need to set an Authentication built from TokenClaims for the shared
 * aspect to see the actor.
 */
package com.texora.secops.siem.audit;
