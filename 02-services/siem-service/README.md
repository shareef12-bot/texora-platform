# Texora SecOps - SOC/SIEM Log Server

Platform-wide consumer of every audit topic in the Texora SecOps program
(`dc`, `ldap`, `sso`, `ftp`, `cgi`, `vpn`, `sec`) plus the five business products
(Ilmora, Texora Jobs, TaskOrbit, HRMS, CRM). Built from `Texora_SecOps_SIEM_LLD.docx`,
`05-PROMPT-3-siem-service.md`, and the shared engineering standards in
`01-SHARED-STANDARDS.md`.

## Stack

Java 21, Spring Boot 3.5.x (version pinned by the root platform pom), PostgreSQL (schema `siem`) via Spring Data JPA, Flyway,
Kafka (Spring Kafka), an OpenSearch/Elasticsearch-API-compatible event corpus,
texora-iam-client TokenValidator against the shared SSO/IAM issuer, OpenTelemetry + Micrometer,
JUnit 5 + Mockito + Testcontainers. **No Lombok anywhere** - every entity/DTO/value
class has explicit getters/setters, enforced in CI via the Maven Enforcer plugin
(`mvn dependency:tree` failing the build if `lombok` appears).

## Architecture at a glance

```
MultiProtocolIngestionAdapter  (Syslog / HTTP / Kafka - the ONE class per B.4 that
        |                       imports transport-specific libraries)
        v
IngestionOrchestrationService  (buffer -> retry w/ backoff -> DLQ; Requirement 1)
        v
EventNormalizationService      (one NormalizationProfile per upstream source)
        v
EventCorpusSearchAdapter       (indexes into the OpenSearch-compatible store)
        v
RuleExecutionSandbox           (every APPROVED rule, isolated + timeout-bounded;
        |                       Requirement 2)
        v
AlertLifecycleService          (OPEN -> TRIAGED -> ESCALATED -> CLOSED)
```

Two independently-scaling stores, per the LLD:
- **PostgreSQL** (`siem` schema): admin metadata - ingestion sources/log, correlation
  rules + change audit, alerts + transitions, SIEM's own audit trail, config.
- **OpenSearch-compatible store**: the high-volume, queryable, normalized event
  corpus. Talked to exclusively through `EventCorpusSearchAdapter`
  (`search/adapter`), via the plain HTTP index/`_search` API rather than a
  version-pinned client library, so the backing product can be swapped freely.

## Requirements traceability

| LLD requirement | Where |
|---|---|
| 1 - Never silently drop an event | `ingestion/service/IngestionOrchestrationService.java`, `IngestionRetryScheduler.java`; `IngestionOrchestrationServiceTest` |
| 2 - Rule isolation | `correlation/engine/RuleExecutionSandbox.java` (per-rule bounded task, catch-and-continue); `RuleExecutionSandboxTest` (deliberately breaks one rule) |
| 3 - Tenant/RBAC-scoped search | `search/service/TenantFilteredSearchService.java` (403 on cross-tenant scope, graceful degradation on backend impairment); `TenantFilteredSearchServiceTest` |
| 4 - Two-person rule approval | `correlation/service/RuleApprovalService.java` (submitter cannot approve; every decision audited); `RuleApprovalServiceTest` |
| Multi-product ingestion | `normalization/service/profile/*` - one profile per source, including all five business products |
| DLQ/lag as first-class metrics | `ingestion/metrics/IngestionMetrics.java` - `siem_ingestion_dlq_depth`, `siem_ingestion_lag` on `/actuator/prometheus` |

## Assumptions made during the build (please confirm against the real platform)

These were reasonable, defensible choices where the LLD and shared standards left a
detail unspecified. None of them are hidden - each is also called out in a code
comment at the point it matters:

1. **`siem_ingestion_source`/`siem_ingestion_log` tenant scoping.** The blanket
   "every table has `tenant_id` NOT NULL" rule (B.5) doesn't map cleanly onto an
   ingestion *channel*, which multiplexes events for every tenant of a product
   (e.g. `crm.audit.events` carries all CRM tenants' events). These two tables use
   a reserved **platform tenant id** (`00000000-0000-0000-0000-000000000000`,
   `CallerContext.PLATFORM_TENANT_ID`); the real tenant is carried per-event on
   `normalized_event.tenantScope` and copied onto `siem_alert` once a rule matches.
2. **JWT claim names.** `RbacInterceptor` reads `tenant_id` and `roles` claims and
   the `sub` claim as the user id, per the "shared IAM client library" convention
   described but not fully specified in the standards doc. Confirm these match
   what the real SSO service issues.
3. **Shared Audit SDK stand-in.** No shared Audit SDK artifact was provided, so
   `audit/AuditEventPublisher` + `audit/AuditingAspect` implement the same
   contract locally (write to `siem_audit_event`, publish to Kafka). Swap these
   for the real SDK client once it's available; the `@Audited` annotation call
   sites won't need to change.
4. **Every other service's audit-event JSON shape.** `normalization/service/
   AbstractSharedSdkNormalizationProfile` assumes every producer on a
   `*.audit.events` topic uses the same shared-SDK shape (`id, tenantId, actorId,
   action, targetType, targetId, outcome, occurredAt`). If a specific source
   diverges, override `mapEventType`/`mapTarget` in that one profile - the other
   eleven are unaffected.
5. **mTLS termination model.** `MultiProtocolIngestionAdapter`'s HTTP path reads
   the verified client certificate CN from an `X-Client-Cert-CN` header, assuming
   TLS terminates at an ingress/mesh that forwards it (common on Kubernetes). If
   your platform terminates mTLS in-process instead, swap in the
   `jakarta.servlet.request.X509Certificate` request-attribute path already
   stubbed in `IngestionHttpController`. `mtls_cert_ref` is compared as a
   substring match against the presented CN as a placeholder - wire this to SEC's
   real certificate-reference resolution endpoint before production use.
6. **Correlation engine scope.** `RuleExecutionSandbox` supports up to a
   **two-stage** condition chain (a threshold condition, optionally followed by a
   second condition that must occur within a further window) via in-memory,
   per-instance sliding windows (`CorrelationWindowStore`). This is intentionally
   the simplest thing that satisfies the LLD's example rule shape and the
   isolation requirement - it is not a general-purpose CEP engine. Two follow-ups
   before running >1 replica: (a) back the window store with a shared store
   (Redis, etc.) so correlation state is consistent across replicas, and (b)
   extend the parser/evaluator for N-stage chains if detections need them.
7. **Alert transition graph.** `OPEN -> TRIAGED -> ESCALATED -> CLOSED`, with a
   `OPEN|TRIAGED -> CLOSED` shortcut for the common false-positive path; `CLOSED`
   is terminal (`alerting/model/AlertTransitionRules.java`). Confirm this matches
   SOC's actual triage workflow.
8. **`/api/v1/config` and `/api/v1/policies`** aren't in the package-structure
   listing in the prompt; they're implemented under a new `admin/` package,
   mirroring the generic pattern described for other services. `siem_config_item`
   is a table this build adds (a simple tenant-scoped key/value store) since the
   LLD's schema section didn't define one.

## Platform integration status (KNOWN-INTEGRATION-PITFALLS.md audit)

| Pitfall | Status |
|---|---|
| 1. Real `texora-iam-client` API | **Fixed.** `RbacInterceptor` calls `TokenValidator.validate(rawHeader)`, catches `PlatformException.UnauthenticatedException`, reads `TokenClaims`. Local JWT parsing removed; `SecurityConfig` supplies `TokenValidator(JwtDecoder)`. No invented MFA class. |
| 2. `spring-security-core` | **Fixed.** Declared explicitly in `pom.xml`. |
| 3. Unique `GlobalExceptionHandler` bean name | **Fixed.** `@Component("siemGlobalExceptionHandler")`; catch-all logs the exception. Local audit beans also renamed (`siemAuditingAspect`, `siemAuditEventPublisher`) to pre-empt the same collision. |
| 4. Root `<module>` + `relativePath` | **Partly fixed.** `<parent>` now `texora-secops-platform` with `<relativePath>../../pom.xml</relativePath>`. **Manual step:** add `<module>02-services/siem-service</module>` to the root `pom.xml` (not available to this build). |
| 5. Package paths | `@LocalServerPort` correct; `TenantContextMissingException` now imported from `com.texora.secops.starter.exception`. |
| 8. Version-sensitive libs | **Fixed.** `springdoc-openapi` bumped from 2.6.0 to **2.8.15** (springdoc's confirmed Spring Boot 3.5.x baseline; 2.5.x throws `NoSuchMethodError`). |
| 9. Testcontainers needs Docker | Noted - `SiemIntegrationTest` needs Docker Desktop locally. |

### Open integration items (NOT guessed - confirm against the real libraries)

1. **`texora-audit-sdk` API.** Dependency added, but the SDK's real annotation/publisher API was not
   available. `audit/` remains a documented local stopgap (see `audit/package-info.java`). The SDK's
   aspect reads `SecurityContextHolder`; this service does not populate it, so `RbacInterceptor`
   must also set an `Authentication` from `TokenClaims` when the SDK is adopted.
2. **Shared `TenantContext` / `CallerContext`.** The starter almost certainly has a shared tenant
   context; its API is unconfirmed, so a small local `CallerContext` (holding `TokenClaims`) is used
   and deliberately not named `TenantContext`. Replace once confirmed.
3. **`PlatformException` package.** Imported from `com.texora.secops.starter.exception` by inference
   (next to the confirmed `TenantContextMissingException`). If the compiler objects, use the real package.
4. **Maven coordinates.** `texora-secops-starter`, `texora-audit-sdk`, `texora-iam-client` assumed
   under `com.texora.secops`; parent coordinates copied from the pitfalls doc's own example.
5. **Overlapping local classes.** Local `RbacInterceptor`/`PolicyDecisionClient` follow B.3 but could
   collide with same-named starter beans; rename with a `siem` prefix if startup reports a clash.
6. **Testcontainers BOM** is still imported locally in this pom; move it to the root pom if managed there.

## Running locally

```bash
docker compose up --build
```

Brings up Postgres, Kafka (+ Zookeeper), a single-node OpenSearch, and the service
on `:8086`. Swagger UI: `http://localhost:8086/api/v1/docs`.

**Note on this repository's provenance:** it was authored in a sandboxed
environment without access to Maven Central, so `mvn verify` could not be run
here to confirm a clean compile. The code was written carefully against
well-known Spring Boot 3 / Jakarta EE APIs, but please run a full build in your
own CI as the first step before relying on it.

## Testing

`RuleExecutionSandboxTest`, `RuleApprovalServiceTest`, `TenantFilteredSearchServiceTest`,
and `IngestionOrchestrationServiceTest` directly exercise the four adversarial/resilience
scenarios the LLD calls out by name. `SiemIntegrationTest` is a Testcontainers-backed
end-to-end smoke test (needs a Docker daemon) covering unauthenticated/unauthorized
paths; extend it with the full 401/403/409/422 matrix per endpoint before sign-off,
per the Definition of Done in the shared standards (B.8/B.9).

## What's deliberately out of scope for this build

- Wiring to the real shared IAM client library, shared Audit SDK, and SEC's
  certificate-resolution endpoint (stubbed against reasonable interfaces - see
  assumptions above).
- A production-grade Syslog parser (RFC 5424/6587 framing) - the provided
  listener is a functional baseline, off by default.
- Load testing artifacts (LLD asks for "combined peak output of all services
  plus concurrent analyst search" - this needs the other seven services' real
  throughput numbers to design meaningfully).
